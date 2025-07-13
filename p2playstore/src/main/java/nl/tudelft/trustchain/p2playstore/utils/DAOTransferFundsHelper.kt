package nl.tudelft.trustchain.p2playstore.utils

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
import nl.tudelft.trustchain.currencyii.sharedWallet.SWResponseSignatureBlockTD
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.util.taproot.MuSig
import nl.tudelft.trustchain.p2playstore.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.*
import org.bitcoinj.core.Address
import org.bitcoinj.core.ECKey
import java.math.BigInteger

class DAOTransferFundsHelper {
    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val community: TrustChainCommunity by lazy { getTrustChainCommunity() }


    /**
     * 3.2 Transfer funds from an existing shared wallet to a third-party. Broadcast bitcoin transaction.
     */
    fun transferFunds(
        myPeer: Peer,
        walletData: JoinDaoData, // Data from the latest JOIN block
        proposalData: ProposeUpdateData, // The proposal data (P2PStore specific)
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
        val oldTransactionSerialized = proposalData.SW_TRANSACTION_SERIALIZED

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

        // Broadcast the result using the P2PStore specific block type
        broadcastTransferFundSuccessful(myPeer, walletData, serializedTransaction, proposalData)
    }

    /**
     * 3.3 Everything is done, publish the final serialized bitcoin transaction data on trustchain.
     */
    private fun broadcastTransferFundSuccessful(
        myPeer: Peer,
        latestJoinBlockData: JoinDaoData, // Data from the latest JOIN block (P2PStore specific)
        serializedTransaction: String, // The new transaction
        proposalData: ProposeUpdateData // The proposal data (P2PStore specific)
    ) {
        // Create an UPDATE_ACCEPTED_BLOCK for the successful transfer with app metadata
        val updateAcceptedData = UpdateAcceptedTransactionData(
            latestJoinBlockData.DAO_ID,
            proposalData.FEATURE_REQUEST_ID,
            serializedTransaction,
            proposalData.SW_TRANSFER_FUNDS_AMOUNT,
            latestJoinBlockData.SW_TRUSTCHAIN_PKS,
            latestJoinBlockData.SW_BITCOIN_PKS,
            latestJoinBlockData.SW_NONCE_PKS,
            proposalData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED,
            proposalData.SW_UNIQUE_PROPOSAL_ID,
            latestJoinBlockData.APP_NAME,
            latestJoinBlockData.APP_DESCRIPTION,
            latestJoinBlockData.APP_CATEGORY,
            latestJoinBlockData.APP_ICON,
            proposalData.APP_MAGNET_LINK
        )

        // Broadcast the UPDATE_ACCEPTED_BLOCK
        community.createProposalBlock(
            updateAcceptedData.blockType,
            updateAcceptedData.getTransactionData(),
            myPeer.publicKey.keyToBin()
        )
    }

    companion object {
        /**
         * Given a shared wallet transfer fund proposal block, calculate the signature and send an agreement block.
         */
        fun transferFundsBlockReceived(
            block: TrustChainBlock, // This is the PROPOSE_UPDATE_BLOCK (Feature Solution Proposal)
            myPublicKey: ByteArray,
            votedInFavor: Boolean, // Indicates if the voter agreed with the proposal, not the Bitcoin transaction itself
            context: Context,
            community: TrustChainCommunity
        ) {
            val blockData = ProposeUpdateTransactionData(block.transaction).getData()
            val latestDaoData = blockData

            Log.i("P2P.DAOTransfer", "Signature request for transfer funds: ${blockData.SW_RECEIVER_PK}, me: ${myPublicKey.toHex()}")
//
//            if (blockData.SW_RECEIVER_PK == myPublicKey.toHex()) {
//                return
//            }

            Log.i("P2P.DAOTransfer", "Signing transfer funds transaction: $blockData")

            val walletManager = WalletManagerAndroid.getInstance()

            // Determine the transaction and member data based on the latest DAO state block type
            val daoBitcoinPks = latestDaoData.SW_BITCOIN_PKS.mapNotNull { try { ECKey.fromPublicOnly(it.hexToBytes()) } catch (e: Exception) { Log.e("P2P.DAOTransfer", "Invalid Bitcoin PK from latest DAO state: $it", e); null } }
            val daoNoncePks = latestDaoData.SW_NONCE_PKS.mapNotNull { try { ECKey.fromPublicOnly(it.hexToBytes()) } catch (e: Exception) { Log.e("P2P.DAOTransfer", "Invalid Nonce PK from latest DAO state: $it", e); null } }
            val oldTransactionSerialized = latestDaoData.SW_TRANSACTION_SERIALIZED

            if (daoBitcoinPks.size != latestDaoData.SW_BITCOIN_PKS.size || daoNoncePks.size != latestDaoData.SW_NONCE_PKS.size) {
                Log.e("P2P.DAOTransfer", "transferFundsBlockReceived: Failed to parse all member PKs or Nonce PKs from latest DAO state. Cannot sign.")
                // Optionally broadcast a negative vote due to data inconsistency? Or just log and return.
                return
            }

            if (oldTransactionSerialized.isEmpty()) {
                Log.e("P2P.DAOTransfer", "Old transaction serialized data is null or empty. Cannot sign transfer.")
                return // Cannot sign if we don't have the old transaction data
            }

            Log.d("P2P.DAOTransfer", "Signing transaction inputs:")
            Log.d("P2P.DAOTransfer", "  oldTransactionSerialized: ${oldTransactionSerialized.take(64)}...")
            Log.d("P2P.DAOTransfer", "  daoBitcoinPks: ${daoBitcoinPks.size} keys")
            Log.d("P2P.DAOTransfer", "  daoNoncePks: ${daoNoncePks.size} nonces")
            Log.d("P2P.DAOTransfer", "  myPublicKey: ${myPublicKey.toHex()}")
            Log.d("P2P.DAOTransfer", "  receiverAddress: ${blockData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED}")
            Log.d("P2P.DAOTransfer", "  satoshiAmount: ${blockData.SW_TRANSFER_FUNDS_AMOUNT}")

            // Generate a new nonce *for this vote block*. This is distinct from the nonces in SW_NONCE_PKS.
            // addNewNonceKey stores this new nonce key locally and returns the key pair.
            val newVoteNonceKeyPair = walletManager.addNewNonceKey(blockData.DAO_ID, context)
            val newVoteNoncePointHex = walletManager.nonceECPointHex(newVoteNonceKeyPair)
            Log.d("P2P.DAOTransfer", "transferFundsBlockReceived: Generated new nonce for vote block: $newVoteNoncePointHex")


            // Generate the Bitcoin signature for the proposed transfer transaction.
            // This signature is required regardless of whether the voter votes YES or NO on the proposal,
            // because ALL members' signatures are needed for the multisig transfer once consensus is reached.
            val signature =
                walletManager.safeSigningTransactionFromMultiSig(
                    oldTransactionSerialized, // The serialized transaction from the latest DAO state
                    daoBitcoinPks, // Bitcoin PKs of all current DAO members (from latest JOIN)
                    daoNoncePks, // Nonce PKs of all current DAO members (from latest JOIN)
                    walletManager.protocolECKey(), // My Bitcoin ECKey
                    Address.fromString(walletManager.params,blockData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED), // The recipient address from the proposal
                    blockData.SW_TRANSFER_FUNDS_AMOUNT, // The amount to transfer from the proposal
                    blockData.DAO_ID, // The DAO ID
                    context // Context for nonce key management (needed by safeSigningTransactionFromMultiSig internally)
                )
            val signatureSerialized = signature.toByteArray().toHex()
            Log.d("P2P.DAOTransfer", "transferFundsBlockReceived: Generated Bitcoin signature: ${signatureSerialized.take(64)}...")

            // Create the appropriate vote block based on how the user voted on the proposal.
            // This block contains their signature for the underlying Bitcoin transfer transaction.
            val blockType = if (votedInFavor) VOTE_YES_BLOCK else VOTE_NO_BLOCK
            val transactionData = if (votedInFavor) {
                Log.d("P2P.DAOTransfer", "Vote YES")
                VoteYesTransactionData(
                    blockData.DAO_ID,
                    blockData.SW_UNIQUE_PROPOSAL_ID,
                    signatureSerialized,
                    walletManager.protocolECKey().publicKeyAsHex,
                    newVoteNoncePointHex
                ).getTransactionData()
            } else {
                VoteNoTransactionData(
                    blockData.DAO_ID,
                    blockData.SW_UNIQUE_PROPOSAL_ID,
                    signatureSerialized,
                    walletManager.protocolECKey().publicKeyAsHex,
                    newVoteNoncePointHex
                ).getTransactionData()
            }

            // Broadcast the vote block with the signature
            community.createProposalBlock(
                blockType,
                transactionData,
                myPublicKey
            )
            Log.d("DAOTransferFundsHelper", "createProposalBlock called for ${blockType}. Proposal ID: ${blockData.SW_UNIQUE_PROPOSAL_ID}")
        }
    }
}
