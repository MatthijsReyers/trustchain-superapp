package nl.tudelft.trustchain.p2playstore.models

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.sharedWallet.SWResponseSignatureBlockTD
import nl.tudelft.trustchain.currencyii.util.taproot.MuSig
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.BaseData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesData
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
import nl.tudelft.trustchain.p2playstore.utils.taproot.CTransaction
import org.bitcoinj.core.Address
import org.bitcoinj.core.ECKey
import java.math.BigInteger

class UpdateProposalPoll(block: TrustChainBlock) : Poll(block) {

    init {
        assert(block.type == P2pStoreCommunity.PROPOSE_UPDATE_BLOCK)
    }

    private val blockData = ProposeUpdateTransactionData(block.transaction).getData()

    // See `Poll` class
    override val daoId = blockData.DAO_ID
    override val proposalId = blockData.SW_UNIQUE_PROPOSAL_ID
    override val requestingUser = block.publicKey.toHex()
    override val votesRequired: Int = blockData.SW_SIGNATURES_REQUIRED
    override val receivingUser = blockData.SW_RECEIVER_PK

    val name: String = blockData.APP_NAME
    val description: String = blockData.APP_DESCRIPTION
    val category: String = blockData.APP_CATEGORY
    val magnetLink: MagnetLink = MagnetUtils.parseMagnet(blockData.APP_MAGNET_LINK)
    val featureRequestId = blockData.FEATURE_REQUEST_ID

    /**
     * A bitcoin wallet address? Where the reward funds should end up after this transaction.
     */
    private val rewardDestination = blockData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED

    /**
     * How much does the developer get transferred to their wallet (in Satoshi's) if this update is
     * accepted? Note that this should be identical to the reward amount in the corresponding
     * feature request.
     */
    val rewardAmount = blockData.SW_TRANSFER_FUNDS_AMOUNT

    override fun isUserDeveloper(): Boolean {
        return trustChain.myPeer.publicKey.pub().toString() == blockData.DEVELOPER_PUBLIC_KEY
    }

    /**
     * Checks if there has actually an UPDATE_ACCEPTED block been created for this proposal. Note
     * that is can only have happened after enough up votes were collected.
     */
    fun hasBeenReleased(): Boolean {
        return trustChain.database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)
            .filter { b -> b.insertTime!! > this.block.insertTime!! }
            .mapNotNull { b ->
                try {
                    P2playApp(b)
                } catch (err: Throwable) {
                    null
                }
            }
            .filter { app -> app.daoId == this.daoId }
            .any { app -> app.magnetLink == this.magnetLink }
    }

    /**
     * Publishes the update by initiating the Bitcoin transfer of reward funds.
     * This method contains the core logic for the transfer process.
     */
    fun triggerRewardTransfer(
        context: Context,
        activity: Activity
    ) {
        Log.d("UpdateProposalPoll", "triggerRewardTransfer called for poll $proposalId")

        val walletManager = WalletManagerAndroid.getInstance()
        val p2pStoreCommunity = P2pStoreCommunity()

        // Get votes
        val yesVotes = p2pStoreCommunity.fetchProposalResponses(daoId, proposalId)
        val noVotes = p2pStoreCommunity.fetchNegativeProposalResponses(daoId, proposalId)
        val allVoteResponses: List<BaseData> = yesVotes + noVotes

        // Get latest JOIN block for DAO state
        val latestJoinBlock = p2pStoreCommunity.fetchLatestJoinBlockByDaoId(daoId)
            ?: run {
                Log.e("UpdateProposalPoll", "Cannot find latest JOIN block for DAO $daoId")
                activity.runOnUiThread {
                    Toast.makeText(context, "Error: Could not find latest DAO join state.", Toast.LENGTH_SHORT).show()
                }
                throw IllegalStateException("Latest JOIN block not found")
            }

        val daoWalletStateForTransfer = JoinDaoTransactionData(latestJoinBlock.transaction).getData()
        val totalDaoMembers = daoWalletStateForTransfer.SW_TRUSTCHAIN_PKS.size
        val votingThresholdPercentage = daoWalletStateForTransfer.SW_VOTING_THRESHOLD
        val requiredSignaturesForApproval = BlockUtils.percentageToIntThreshold(totalDaoMembers, votingThresholdPercentage)

        // Verify enough votes
        if (yesVotes.size < requiredSignaturesForApproval) {
            Log.w("UpdateProposalPoll", "Insufficient YES votes. Needed: $requiredSignaturesForApproval, Have: ${yesVotes.size}")
            activity.runOnUiThread {
                Toast.makeText(context, "Reward transfer not yet possible. Need $requiredSignaturesForApproval YES votes, have ${yesVotes.size}.", Toast.LENGTH_LONG).show()
            }
            throw IllegalStateException("Insufficient YES votes for approval")
        }

        // Check DAO balance
        val currentDaoBalance = try {
            val latestDaoWalletBlock = p2pStoreCommunity.fetchLatestSharedWalletBlockByDaoId(daoId)
            if (latestDaoWalletBlock != null) {
                val serializedTx = when (latestDaoWalletBlock.type) {
                    P2pStoreCommunity.JOIN_BLOCK -> JoinDaoTransactionData(latestDaoWalletBlock.transaction).getData().SW_TRANSACTION_SERIALIZED
                    UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(latestDaoWalletBlock.transaction).getData().SW_TRANSACTION_SERIALIZED
                    P2pStoreCommunity.PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(latestDaoWalletBlock.transaction).getData().SW_TRANSACTION_SERIALIZED // For robustness
                    else -> null
                }
                if (serializedTx != null) {
                    CTransaction().deserialize(serializedTx.hexToBytes()).vout.find { it.scriptPubKey.size == 35 }?.nValue ?: 0L
                } else {
                    0L
                }
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e("UpdateProposalPoll", "Error fetching DAO balance: ${e.message}")
            0L
        }

        if (currentDaoBalance < rewardAmount) {
            Log.w("UpdateProposalPoll", "Insufficient DAO funds. Current: $currentDaoBalance, Needed: $rewardAmount")
            activity.runOnUiThread {
                Toast.makeText(context, "DAO wallet has insufficient funds ($currentDaoBalance satoshis) to pay the reward ($rewardAmount satoshis).", Toast.LENGTH_LONG).show()
            }
            throw IllegalStateException("Insufficient DAO funds for reward")
        }

        // Validate Bitcoin address
        try {
            val params = walletManager.params
            Address.fromString(params, rewardDestination)
        } catch (e: Exception) {
            Log.e("UpdateProposalPoll", "Invalid Bitcoin address: ${e.message}")
            activity.runOnUiThread {
                Toast.makeText(context, "Invalid developer Bitcoin address format.", Toast.LENGTH_LONG).show()
            }
            throw IllegalArgumentException("Invalid Bitcoin address format")
        }

        // Execute transfer
        val overallLatestDaoBlock = p2pStoreCommunity.fetchLatestSharedWalletBlockByDaoId(daoId)
            ?: run {
                Log.e("UpdateProposalPoll", "Cannot find overall latest DAO block")
                activity.runOnUiThread {
                    Toast.makeText(context, "Error: Could not find overall latest DAO state.", Toast.LENGTH_SHORT).show()
                }
                throw IllegalStateException("Overall latest DAO block not found")
            }

        p2pStoreCommunity.transferFunds(
            walletData = daoWalletStateForTransfer,
            walletBlockData = overallLatestDaoBlock.transaction,
            blockData = blockData,
            voteResponses = allVoteResponses,
            receiverAddress = rewardDestination,
            satoshiAmount = rewardAmount,
            context = context,
            activity = activity
        )

        Log.i("UpdateProposalPoll", "DAO fund transfer for reward initiated successfully for proposal $proposalId")
        activity.runOnUiThread {
            Toast.makeText(context, "Reward transfer from DAO wallet initiated.", Toast.LENGTH_LONG).show()
        }
    }

    fun publishUpdate(
        context: Context,
        activity: Activity // Activity context needed by underlying bitcoinj calls
    ) {
        val app = this.getApp()
        val latestJoin = app.getLatestJoin()
        val joinData = JoinDaoTransactionData(latestJoin.transaction).getData()
        val voteResponses = this.getYesVotes() + this.getNoVotes()
        this.transferFunds(
            joinData,
            voteResponses,
            this.rewardDestination,
            this.rewardAmount,
            context,
            activity
        )
    }

    /**
     * Transfer funds from an existing shared wallet to a third-party.
     * Broadcast bitcoin transaction.
     */
    private fun transferFunds(
        walletData: JoinDaoData, // Data from the latest JOIN block
        voteResponses: List<BaseData>, // All vote responses (including NO)
        receiverAddress: String,
        paymentAmount: Long,
        context: Context,
        activity: Activity // Activity context needed by underlying bitcoinj calls
    ) {
        val walletManager = WalletManagerAndroid.getInstance()

        // Collect signatures and nonces from ALL vote responses (YES and NO) as every member contributes
        val swResponses = voteResponses.mapNotNull { vote ->
            when (vote) {
                is VoteYesData -> SWResponseSignatureBlockTD(
                    SW_UNIQUE_ID = vote.DAO_ID,
                    SW_UNIQUE_PROPOSAL_ID = vote.SW_UNIQUE_PROPOSAL_ID,
                    SW_SIGNATURE_SERIALIZED = vote.SW_SIGNATURE_SERIALIZED,
                    SW_BITCOIN_PK = vote.SW_BITCOIN_PK,
                    SW_NONCE = vote.SW_NONCE
                )
                is VoteNoData -> SWResponseSignatureBlockTD(
                    SW_UNIQUE_ID = vote.DAO_ID, // Use DAO_ID from BaseData
                    SW_UNIQUE_PROPOSAL_ID = vote.SW_UNIQUE_PROPOSAL_ID,
                    SW_SIGNATURE_SERIALIZED = vote.SW_SIGNATURE_SERIALIZED,
                    SW_BITCOIN_PK = vote.SW_BITCOIN_PK,
                    SW_NONCE = vote.SW_NONCE
                )
                else -> null
            }
        }


        val signaturesOfOldOwners = swResponses.map {
            BigInteger(1, it.SW_SIGNATURE_SERIALIZED.hexToBytes())
        }

        // Use the transaction serialized in the proposal block
        val oldTransactionSerialized = this.blockData.SW_TRANSACTION_SERIALIZED

        // Use nonce PKs from the latest JOIN_BLOCK data to aggregate nonces
        val noncePoints =
            walletData.SW_NONCE_PKS.map {
                ECKey.fromPublicOnly(it.hexToBytes())
            }

        val newNonces: ArrayList<String> = ArrayList(swResponses.map { it.SW_NONCE })

        val (aggregateNoncePoint, _) = MuSig.aggregateSchnorrNonces(noncePoints)

        Log.d("P2P.DAOTransfer", "Sending transaction inputs:")
        Log.d("P2P.DAOTransfer", "  daoBitcoinPks: ${walletData.SW_BITCOIN_PKS.size} keys")
        Log.d("P2P.DAOTransfer", "  signaturesOfOldOwners: ${signaturesOfOldOwners.size} signatures")
        Log.d("P2P.DAOTransfer", "  aggregateNoncePoint: ${aggregateNoncePoint.getEncoded(true).toHex()}")
        Log.d("P2P.DAOTransfer", "  oldTransactionSerialized: ${oldTransactionSerialized.take(64)}...")
        Log.d("P2P.DAOTransfer", "  receiverAddress: $receiverAddress")
        Log.d("P2P.DAOTransfer", "  paymentAmount: $paymentAmount")
        Log.d("P2P.DAOTransfer", "  DAO_ID: ${walletData.DAO_ID}")

        val (status, serializedTransaction) =
            walletManager.safeSendingTransactionFromMultiSig(
                walletData.SW_BITCOIN_PKS.map { ECKey.fromPublicOnly(it.hexToBytes()) },
                signaturesOfOldOwners,
                aggregateNoncePoint,
                oldTransactionSerialized,
                Address.fromString(walletManager.params, receiverAddress),
                paymentAmount // The reward amount
            )

        if (status) {
            Log.d("MVDAO", "successfully submitted taproot transaction to server")
            activity.runOnUiThread {
                Toast.makeText(context, "Successfully submitted the transaction", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("MVDAO", "taproot transaction submission to server failed")
            activity.runOnUiThread {
                Toast.makeText(context, "Failed to submit the transaction to the server", Toast.LENGTH_SHORT).show()
            }
        }

        // Update the wallet data with the new nonces for the next transaction
        walletData.SW_NONCE_PKS = newNonces

        // Broadcast the result using the P2PlayStore specific block type
        broadcastTransferFundSuccessful(
            trustChain.myPeer,
            walletData,
            serializedTransaction,
        )
    }

    /**
     * 3.3 Everything is done, publish the final serialized bitcoin transaction data on trustchain.
     */
    private fun broadcastTransferFundSuccessful(
        myPeer: Peer,
        latestJoinBlockData: JoinDaoData, // Data from the latest JOIN block (P2PStore specific)
        serializedTransaction: String, // The new transaction
    ) {
        // Create an UPDATE_ACCEPTED_BLOCK for the successful transfer with app metadata
        val updateAcceptedData = UpdateAcceptedTransactionData(
            this.daoId,
            this.featureRequestId,
            serializedTransaction,
            this.rewardAmount,
            latestJoinBlockData.SW_TRUSTCHAIN_PKS,
            latestJoinBlockData.SW_BITCOIN_PKS,
            latestJoinBlockData.SW_NONCE_PKS,
            this.rewardDestination,
            this.proposalId,

            // TODO: This should use the name and description of the update not the previous join
            // block.
            latestJoinBlockData.APP_NAME,
            latestJoinBlockData.APP_DESCRIPTION,
            latestJoinBlockData.APP_CATEGORY,
            latestJoinBlockData.APP_ICON,
            this.magnetLink.raw
        )

        // Broadcast the UPDATE_ACCEPTED_BLOCK
        trustChain.createProposalBlock(
            updateAcceptedData.blockType,
            updateAcceptedData.getTransactionData(),
            myPeer.publicKey.keyToBin()
        )
    }

    companion object {
        /**
         * Tries to find a join proposal with the given proposal ID.
         */
        fun findByProposalId(proposalId: String): UpdateProposalPoll? {
            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
            val proposals = trustChain.database
                .getBlocksWithType(P2pStoreCommunity.PROPOSE_UPDATE_BLOCK)
                .mapNotNull { b ->
                    try { UpdateProposalPoll(b) }
                    catch (err: Throwable) { null}
                }
                .filter { p -> p.proposalId == proposalId }

            // Get the block that this user is allowed to vote on, if one exists.
            val myProposal = proposals.find { p -> p.isReceivingUser }
            return myProposal ?: proposals.firstOrNull()
        }
    }
}

