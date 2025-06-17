package nl.tudelft.trustchain.p2playstore.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.sharedWallet.SWResponseSignatureBlockTD
import nl.tudelft.trustchain.currencyii.TrustChainHelper
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.util.taproot.MuSig
import nl.tudelft.trustchain.p2playstore.transactionData.*
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import org.bitcoinj.core.Address
import org.bitcoinj.core.ECKey
import java.math.BigInteger

class DAOTransferFundsHelper {
    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val community: TrustChainCommunity by lazy { getTrustChainCommunity() }

//    /**
//     * 3.1 Send a proposal block on trustchain to ask for the signatures.
//     * Assumed that people agreed to the transfer.
//     * @param myPeer - Peer, the user that wants to join the wallet
//     * @param mostRecentWalletBlock - TrustChainBlock, describes the wallet where the transfer is from
//     * @param receiverAddressSerialized - String, the address where the transaction needs to go
//     * @param satoshiAmount - Long, the amount that needs to be transferred
//     * @return the proposal block
//     */
//    fun proposeTransferFunds(
//        myPeer: Peer,
//        mostRecentWalletBlock: TrustChainBlock,
//        receiverAddressSerialized: String,
//        satoshiAmount: Long,
//        // Parameters specific to Feature Solutions
//        featureRequestId: String? = null,
//        solutionTitle: String? = null,
//        solutionDescription: String? = null,
//        developerPublicKey: String? = null,
//        apkMagnetLink: String? = null,
//        appName: String,
//        appDescription: String,
//        appCategory: String,
//        appIcon: Int
//    ): ProposeUpdateTransactionData {
//        val mostRecentBlockHash = mostRecentWalletBlock.calculateHash().toHex()
//        val blockData = JoinDaoTransactionData(mostRecentWalletBlock.transaction).getData()
//
//        val total = blockData.SW_BITCOIN_PKS.size
//        val requiredSignatures =
//            BlockUtils.percentageToIntThreshold(total, blockData.SW_VOTING_THRESHOLD)
//
//
//        val proposalID = BlockUtils.randomUUID()
//
//        var askSignatureBlockDataTemplate =
//            ProposeUpdateTransactionData(
//                uniqueId = blockData.DAO_ID,
//                previousWalletBlockHash = mostRecentBlockHash,
//                requiredSignatures = requiredSignatures,
//                satoshiAmount = satoshiAmount,
//                bitcoinPks = blockData.SW_BITCOIN_PKS,
//                transferFundsAddressSerialized = receiverAddressSerialized,
//                receiverPk = "",
//                uniqueProposalId = proposalID,
//                transactionSerialized = blockData.SW_TRANSACTION_SERIALIZED,
//                name = appName,
//                appDescription = appDescription,
//                appCategory = appCategory,
//                appIcon = appIcon,
//                apkMagnetLink = apkMagnetLink ?: "",
//            )
//
//
//        for (swParticipantPk in blockData.SW_TRUSTCHAIN_PKS) {
//            Log.i(
//                "Coin",
//                "Sending TRANSFER proposal (total: ${blockData.SW_TRUSTCHAIN_PKS.size}) to $swParticipantPk"
//            )
//
//            // Create a copy for each recipient to set their specific receiverPk
//            val askSignatureBlockData = ProposeUpdateTransactionData(
//                daoId = askSignatureBlockDataTemplate.getData().DAO_ID,
//                previousWalletBlockHash = askSignatureBlockDataTemplate.getData().SW_PREVIOUS_BLOCK_HASH,
//                requiredSignatures = askSignatureBlockDataTemplate.getData().SW_SIGNATURES_REQUIRED,
//                rewardAmount = askSignatureBlockDataTemplate.getData().SW_TRANSFER_FUNDS_AMOUNT,
//                bitcoinPks = askSignatureBlockDataTemplate.getData().SW_BITCOIN_PKS,
//                developerBitcoinAddress = askSignatureBlockDataTemplate.getData().SW_TRANSFER_FUNDS_TARGET_SERIALIZED,
//                receiverPk = swParticipantPk, // Set specific receiver
//                uniqueProposalId = askSignatureBlockDataTemplate.getData().SW_UNIQUE_PROPOSAL_ID,
//                transactionSerialized = askSignatureBlockDataTemplate.getData().SW_TRANSACTION_SERIALIZED,
//                appName = askSignatureBlockDataTemplate.getData().APP_NAME,
//                appDescription = askSignatureBlockDataTemplate.getData().APP_DESCRIPTION,
//                appCategory = askSignatureBlockDataTemplate.getData().APP_CATEGORY,
//                appIcon = askSignatureBlockDataTemplate.getData().APP_ICON,
//                apkMagnetLink = askSignatureBlockDataTemplate.getData().APP_MAGNET_LINK,
//                featureRequestId = askSignatureBlockDataTemplate.getData().FEATURE_REQUEST_ID,
//                solutionTitle = askSignatureBlockDataTemplate.getData().SOLUTION_TITLE,
//                solutionDescription = askSignatureBlockDataTemplate.getData().SOLUTION_DESCRIPTION,
//                developerPublicKey = askSignatureBlockDataTemplate.getData().DEVELOPER_PUBLIC_KEY
//            )
//
//
//            val transaction = mapOf("message" to askSignatureBlockData.getJsonString())
//            community.createProposalBlock(
//                askSignatureBlockData.blockType,
//                transaction,
//                myPeer.publicKey.keyToBin()
//            )
//        }
//        return askSignatureBlockDataTemplate
//    }

    /**
     * 3.2 Transfer funds from an existing shared wallet to a third-party. Broadcast bitcoin transaction.
     */
    fun transferFunds(
        myPeer: Peer,
        walletData: JoinDoaData, // Data from the latest JOIN block
        walletBlockData: TrustChainTransaction, // Transaction of the overall latest DAO block (JOIN or UPDATE_ACCEPTED)
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
        latestJoinBlockData: JoinDoaData, // Data from the latest JOIN block (P2PStore specific)
        serializedTransaction: String, // The new transaction
        proposalData: ProposeUpdateData // The proposal data (P2PStore specific)
    ) {
        // Create an UPDATE_ACCEPTED_BLOCK for the successful transfer with app metadata
        val updateAcceptedData = UpdateAcceptedTransactionData(
            latestJoinBlockData.DAO_ID,
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
            latestDaoData: ProposeUpdateData, // Pass the latest DAO state data (should be JoinDoaData from latest JOIN block)
            myPublicKey: ByteArray,
            votedInFavor: Boolean, // Indicates if the voter agreed with the proposal, not the Bitcoin transaction itself
            context: Context,
            community: TrustChainCommunity
        ) {
            val blockData = ProposeUpdateTransactionData(block.transaction).getData()

            Log.i("P2P.DAOTransfer", "Signature request for transfer funds: ${blockData.SW_RECEIVER_PK}, me: ${myPublicKey.toHex()}")
            Log.i("P2P.DAOTransfer", "Signing transfer funds transaction: $blockData")
            Log.i("P2P.DAOTransfer", "Signature request for transfer funds: ${blockData.SW_RECEIVER_PK}, me: ${myPublicKey.toHex()}")
            if (blockData.SW_RECEIVER_PK == myPublicKey.toHex()) {
                return
            }

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

            if (oldTransactionSerialized.isNullOrEmpty()) {
                Log.e("P2P.DAOTransfer", "transferFundsBlockReceived: Old transaction serialized data is null or empty in latest DAO state. Cannot sign transfer.")
                return // Cannot sign if we don't have the old transaction data from the latest state
            }

            Log.d("P2P.DAOTransfer", "transferFundsBlockReceived: Signing transaction inputs:")
            Log.d("P2P.DAOTransfer", "  oldTransactionSerialized (from latest JOIN): ${oldTransactionSerialized.take(64)}...")
            Log.d("P2P.DAOTransfer", "  daoBitcoinPks (from latest JOIN): ${daoBitcoinPks.size} keys")
            Log.d("P2P.DAOTransfer", "  daoNoncePks (from latest JOIN): ${daoNoncePks.size} nonces")
            Log.d("P2P.DAOTransfer", "  myPublicKey: ${myPublicKey.toHex()}")
            Log.d("P2P.DAOTransfer", "  receiverAddress (from proposal): ${blockData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED}")
            Log.d("P2P.DAOTransfer", "  satoshiAmount (from proposal): ${blockData.SW_TRANSFER_FUNDS_AMOUNT}")

            // Generate a new nonce *for this vote block*. This is distinct from the nonces in SW_NONCE_PKS.
            // addNewNonceKey stores this new nonce key locally and returns the key pair.
            val newVoteNonceKeyPair = walletManager.addNewNonceKey(blockData.DAO_ID, context)
            val newVoteNoncePointHex = walletManager.nonceECPointHex(newVoteNonceKeyPair)
            Log.d("P2P.DAOTransfer", "transferFundsBlockReceived: Generated new nonce for vote block: ${newVoteNoncePointHex}")


            // Generate the Bitcoin signature for the proposed transfer transaction.
            // This uses data from the latest DAO state and the proposal, signed with the user's Bitcoin key.
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
            val blockType = if (votedInFavor) P2pStoreCommunity.VOTE_YES_BLOCK else P2pStoreCommunity.VOTE_NO_BLOCK
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
