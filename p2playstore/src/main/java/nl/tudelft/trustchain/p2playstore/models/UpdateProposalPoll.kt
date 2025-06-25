import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.sha1
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.sharedWallet.SWResponseSignatureBlockTD
import nl.tudelft.trustchain.currencyii.util.taproot.MuSig
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.models.Poll
import nl.tudelft.trustchain.p2playstore.transactionData.BaseData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDoaData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.SharedWalletData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesData
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.utils.DAOTransferFundsHelper
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
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
            .filter { b -> b.timestamp > this.block.timestamp }
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

    fun publishUpdate(context: Context, activity: Activity) {
        val latestApp = this.getApp().getLatestVersion()
        val yesVotes = this.getYesVotes()
        val allVotes = yesVotes + this.getNoVotes()
        if (yesVotes.size >= this.votesRequired) {
            this.transferFunds(
                latestApp,
                latestApp.blockData as SharedWalletData,
                allVotes,
                this.rewardDestination,
                this.rewardAmount,
                context,
                activity
            )
        } else {
            Log.d(
                "P2PlayStore",
                "Bug found: someone tried to publish update without enough votes"
            )
        }
    }

    /**
     * Transfer funds from an existing shared wallet to a third-party.
     * Broadcast bitcoin transaction.
     */
    private fun transferFunds(
        latestApp: P2playApp,
        walletData: SharedWalletData, // Data from the latest JOIN block
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
                    SW_UNIQUE_ID = vote.DAO_ID,
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
        val noncePoints = walletData.SW_NONCE_PKS.map {
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
        Log.d("P2P.DAOTransfer", "  DAO_ID: ${latestApp.daoId}")

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
            latestApp,
            walletData,
            serializedTransaction,
        )
    }

    /**
     * Everything is done, publish the final serialized bitcoin transaction data on trustchain.
     */
    private fun broadcastTransferFundSuccessful(
        latestApp: P2playApp,
        latestJoinBlockData: SharedWalletData,
        serializedTransaction: String,
    ) {
        val join = latestApp.getLatestJoin()
        val joinData = JoinDaoTransactionData(join.transaction).getData();
        // Create an UPDATE_ACCEPTED_BLOCK for the successful transfer with app metadata
        val updateAcceptedData = UpdateAcceptedTransactionData(
            this.daoId,
            this.featureRequestId,
            serializedTransaction,
            satoshiAmount = this.rewardAmount,
            trustChainPks = joinData.SW_TRUSTCHAIN_PKS,
            latestJoinBlockData.SW_BITCOIN_PKS,
            latestJoinBlockData.SW_NONCE_PKS,
            this.rewardDestination,
            this.proposalId,

            // TODO: This should use the name and description of the update not the previous join
            // block.
            latestApp.name,
            latestApp.description,
            latestApp.category,
            latestApp.icon,
            this.magnetLink.raw
        )

        // Broadcast the UPDATE_ACCEPTED_BLOCK
        trustChain.createProposalBlock(
            updateAcceptedData.blockType,
            updateAcceptedData.getTransactionData(),
            trustChain.myPeer.publicKey.keyToBin()
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

