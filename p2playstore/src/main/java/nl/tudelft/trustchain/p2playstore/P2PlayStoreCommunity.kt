package nl.tudelft.trustchain.p2playstore

import android.app.Activity
import android.content.Context
import android.util.Log
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.*
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.utils.DAOCreateHelper
import nl.tudelft.trustchain.p2playstore.utils.DAOJoinHelper
import nl.tudelft.trustchain.p2playstore.utils.DAOTransferFundsHelper

class P2pStoreCommunity : Community() {
    override val serviceId: String = "3344FF11BEEF3883287FAC632fc8db5899c5df5b"

    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    val daoJoinHelper = DAOJoinHelper()

    /**
     * 2.1 Send a proposal on the trust chain to join a shared wallet and to collect signatures. The
     * proposal is a serialized bitcoin join transaction. **NOTE** the latest walletBlockData should
     * be given, otherwise the serialized transaction is invalid.
     * @param walletBlock
     * - the latest (that you know of) shared wallet block.
     */
    fun proposeJoinWallet(walletBlock: TrustChainBlock): JoinRequestTransactionData {
        return daoJoinHelper.proposeJoinWallet(myPeer, walletBlock)
    }

    /**
     * 2.2 Commit the join wallet transaction on the bitcoin blockchain and broadcast the result on
     * trust chain.
     *
     * Note: There should be enough sufficient signatures, based on the multisig wallet data.
     * @throws
     * - exceptions if something goes wrong with creating or broadcasting bitcoin transaction.
     * @param walletBlockData
     * - TrustChainTransaction, describes the wallet that is joined
     * @param blockData
     * - SWSignatureAskBlockTD, the block where the other users are voting on
     * @param responses
     * - the positive responses for your request to join the wallet
     */
    fun joinBitcoinWallet(
        walletBlockData: TrustChainTransaction,
        blockData: JoinRequestData,
        responses: List<VoteYesData>,
        context: Context
    ) {
        daoJoinHelper.joinBitcoinWallet(myPeer, walletBlockData, blockData, responses, context)
    }

    /**
     * Searches all known parts of the trust chain for P2PlayStore apps, always returning the
     * latest version of each app inside this user's database.
     */
    fun discoverAllApps(): List<P2playApp> {
        val joinBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        val updateBlocks = getTrustChainCommunity().database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)
        val blocks = joinBlocks + updateBlocks

        // Get a unique list of the shared wallet IDs of the app DAO's that we know about.
        val appIds = blocks
            .map { b -> JoinDaoTransactionData(b.transaction).getData().DAO_ID }
            .distinctBy { id -> id }

        val latestBlocks = appIds
            .map { id ->
                blocks
                    // Get all blocks with this app ID
                    .filter { b ->
                        JoinDaoTransactionData(b.transaction).getData().DAO_ID == id
                    }
                    // Find the newest block
                    .maxByOrNull { b -> b.timestamp }
            }

        return latestBlocks
            .map { b ->
                try { P2playApp(b!!) }
                catch (err: Throwable) {
                    Log.e("P2PlayStore", "Found invalid app block: $err")
                    null
                }
            }
            .filterNotNull()
    }

    fun discoverMyApps(): List<P2playApp> {
        return this.discoverAllApps().filter { app -> app.isDaoMember() }
    }

    internal fun fetchLatestSharedWalletBlockByDaoId(daoId: String): TrustChainBlock? {
        val joinBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        val updateBlocks = getTrustChainCommunity().database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)

        return (joinBlocks + updateBlocks)
            .filter { block ->
                try {
                    when(block.type) {
                        JOIN_BLOCK -> JoinDaoTransactionData(block.transaction).getData().DAO_ID == daoId
                        UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(block.transaction).getData().DAO_ID == daoId
                        else -> false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            .maxByOrNull { it.timestamp }
    }

    /**
     * Fetches the latest JOIN_BLOCK for a specific DAO.
     */
    fun fetchLatestJoinBlockByDaoId(daoId: String): TrustChainBlock? {
        return getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
            .filter { block ->
                try {
                    JoinDaoTransactionData(block.transaction).getData().DAO_ID == daoId
                } catch (e: Exception) {
                    false
                }
            }
            .maxByOrNull { it.timestamp }
    }


    /**
     * Discover shared wallets that you can join, return the latest (known) blocks Fetch the latest
     * block associated with a shared wallet. swBlockHash - the hash of one of the blocks associated
     * with a shared wallet.
     */
    fun fetchLatestSharedWalletBlock(swBlockHash: ByteArray): TrustChainBlock? {
        val swBlock = getTrustChainCommunity().database.getBlockWithHash(swBlockHash) ?: return null
        val swBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        return fetchLatestSharedWalletBlock(swBlock, swBlocks)
    }

    /**
     * Fetch the latest shared wallet block, based on a given block 'block'. The unique shared
     * wallet id is used to find the most recent block in the 'sharedWalletBlocks' list.
     */
    private fun fetchLatestSharedWalletBlock(
        block: TrustChainBlock,
        fromBlocks: List<TrustChainBlock>
    ): TrustChainBlock? {
        if (block.type != JOIN_BLOCK) {
            return null
        }
        val walletId = JoinDaoTransactionData(block.transaction).getData().DAO_ID

        return fromBlocks
            .filter { it.type == JOIN_BLOCK } // make sure the blocks have the correct type!
            .filter {
                JoinDaoTransactionData(it.transaction).getData().DAO_ID == walletId
            }
            .maxByOrNull { it.timestamp.time }
    }

    /**
     * Fetch all DAO blocks that contain a signature. These blocks are the response of a signature
     * request. Signatures are fetched from [VOTE_YES_BLOCK] type blocks.
     */
    fun fetchProposalResponses(
        walletId: String,
        proposalId: String
    ): List<VoteYesData> {
        return getTrustChainCommunity()
            .database
            .getBlocksWithType(VOTE_YES_BLOCK)
            .filter {
                val blockData = VoteYesTransactionData(it.transaction)
                blockData.matchesProposal(walletId, proposalId)
            }
            .map { VoteYesTransactionData(it.transaction).getData() }
    }

    /**
     * Fetch all DAO blocks that contain a negative signature. These blocks are the response of a
     * negative signature request. Signatures are fetched from [VOTE_NO_BLOCK]
     * type blocks.
     */
    fun fetchNegativeProposalResponses(
        walletId: String,
        proposalId: String
    ): List<VoteNoData> {
        return getTrustChainCommunity()
            .database
            .getBlocksWithType(VOTE_NO_BLOCK)
            .filter {
                val blockData = VoteNoTransactionData(it.transaction)
                blockData.matchesProposal(walletId, proposalId)
            }
            .map { VoteNoTransactionData(it.transaction).getData() }
    }

    /**
     * Create a feature request proposal block on trust chain.
     */
    fun createFeatureRequest(daoId: String, title: String, description: String, reward: Long) {
        val featureRequestData = FeatureRequestTransactionData(
            daoId = daoId,
            // FEATURE_REQUEST_ID is generated in the constructor
            title = title,
            description = description,
            reward = reward,
            requesterPublicKey = myPeer.publicKey.pub().toString(),
        )

        val transaction = featureRequestData.getTransactionData()

        getTrustChainCommunity()
            .createProposalBlock(
                featureRequestData.blockType,
                transaction,
                myPeer.publicKey.keyToBin()
            )

        Log.d("P2PlayStore", "Created Feature Request proposal block for DAO $daoId")
    }

    /**
     * Create a feature solution proposal (reusing PROPOSE_UPDATE_BLOCK)
     */
    fun createFeatureSolution(
        daoId: String,
        featureRequestId: String,
        solutionTitle: String,
        solutionDescription: String,
        apkMagnetLink: String,
        developerBitcoinAddress: String
    ) {
        // Get the latest DAO wallet state
        val latestDaoBlock = fetchLatestSharedWalletBlockByDaoId(daoId)
            ?: throw IllegalStateException("DAO not found: $daoId")

        val daoData = when(latestDaoBlock.type) {
            JOIN_BLOCK -> JoinDaoTransactionData(latestDaoBlock.transaction).getData()
            UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(latestDaoBlock.transaction).getData()
            PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(latestDaoBlock.transaction).getData() // Should not happen here, but include for safety
            else -> throw IllegalStateException("Unsupported latest DAO block type for creating solution: ${latestDaoBlock.type}")
        }

        val trustChainPks: List<String>? = when (daoData) {
            is JoinDoaData -> daoData.SW_BITCOIN_PKS
            is UpdateAcceptedData -> daoData.SW_BITCOIN_PKS // Assuming UpdateAcceptedData has it
            is ProposeUpdateData -> daoData.SW_BITCOIN_PKS // Assuming ProposeUpdateData class exists and has it
            else -> null // Or throw an exception if this state is unexpected
        }

        val serialized_transaction: String? = when (daoData) {
            is JoinDoaData -> daoData.SW_TRANSACTION_SERIALIZED
            is UpdateAcceptedData -> daoData.SW_TRANSACTION_SERIALIZED
            is ProposeUpdateData -> daoData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED
            else -> null
        }

        val noncePks: ArrayList<String> = when (daoData) {
            is JoinDoaData -> daoData.SW_NONCE_PKS
            is UpdateAcceptedData -> daoData.SW_NONCE_PKS // Assuming UpdateAcceptedData has it
            else -> arrayListOf() // Should not happen
        }

        // Get the feature request to know the reward amount
        val featureRequest = getFeatureRequestsForDao(daoId)
            .find { it.FEATURE_REQUEST_ID == featureRequestId }
            ?: throw IllegalStateException("Feature request not found: $featureRequestId")

        val proposalId = BlockUtils.randomUUID()
        val requiredSignatures = BlockUtils.percentageToIntThreshold(
            trustChainPks?.size!!, // Use member count from the latest DAO state
            // Need the voting threshold. The latest JOIN block definitely has it.
            fetchLatestJoinBlockByDaoId(daoId)?.let { JoinDaoTransactionData(it.transaction).getData().SW_VOTING_THRESHOLD }
                ?: throw IllegalStateException("Could not determine voting threshold from latest JOIN block for DAO $daoId")
        )
        Log.d("P2PlayStore", "Required signatures: $requiredSignatures")
        // Fetch the transaction serialized from the LATEST JOIN block for the proposal
//        val latestJoinBlock = fetchLatestJoinBlockByDaoId(daoId)
//            ?: throw IllegalStateException("Latest JOIN block not found for DAO ${daoId}. Cannot create solution proposal.")
//        val transactionSerializedForProposal = JoinDaoTransactionData(latestJoinBlock.transaction).getData().SW_TRANSACTION_SERIALIZED

        // Send proposal to all DAO members
        for (memberPk in trustChainPks) {
            val memberSpecificProposal = ProposeUpdateTransactionData(
                daoId = daoId,
                featureRequestId = featureRequestId,
                solutionTitle = solutionTitle,
                solutionDescription = solutionDescription,
                developerPublicKey = myPeer.publicKey.pub().toString(),
                apkMagnetLink = apkMagnetLink,
                previousWalletBlockHash = latestDaoBlock.calculateHash().toHex(), // Use hash of overall latest DAO block
                requiredSignatures = requiredSignatures, // Calculated based on latest members and threshold
                rewardAmount = featureRequest.FEATURE_REWARD,
                bitcoinPks = ArrayList(trustChainPks),
                noncePks = noncePks,
                developerBitcoinAddress = developerBitcoinAddress,
                receiverPk = memberPk,
                uniqueProposalId = proposalId,
                transactionSerialized = serialized_transaction.toString(),
                appName = daoData.APP_NAME,
                appDescription = daoData.APP_DESCRIPTION,
                appCategory = daoData.APP_CATEGORY,
                appIcon = daoData.APP_ICON
            )

            val transaction = memberSpecificProposal.getTransactionData()
            getTrustChainCommunity()
                .createProposalBlock(
                    memberSpecificProposal.blockType,
                    transaction,
                    myPeer.publicKey.keyToBin()
                )
        }

        Log.d("P2PlayStore", "Created Feature Solution block for Feature $featureRequestId in DAO $daoId")
    }


    /**
     * Get all feature requests for a DAO
     */
    fun getFeatureRequestsForDao(daoId: String): List<FeatureRequestData> {
        val blocks = getTrustChainCommunity().database.getBlocksWithType(FEATURE_REQUEST_BLOCK)

        return blocks
            .mapNotNull { block ->
                try {
                    FeatureRequestTransactionData(block.transaction).getData()
                } catch (e: Exception) {
                    Log.e("P2PlayStoreCommunity", "Failed to parse FeatureRequest block: ${e.message}")
                    null
                }
            }
            .filter{it.DAO_ID == daoId}.distinctBy { it.FEATURE_REQUEST_ID }
        }

        /**
         * Fetches all feature request blocks for a specific DAO.
         * Returns a list of TrustChainBlock.
         */
        fun getFeatureRequestBlocksForDao(daoId: String): List<TrustChainBlock> {
        val blocks = getTrustChainCommunity().database.getBlocksWithType(FEATURE_REQUEST_BLOCK)
        return blocks.filter { block ->
            try {
                FeatureRequestTransactionData(block.transaction).getData().DAO_ID == daoId
            } catch (e: Exception) {
                Log.e("P2PlayStoreCommunity", "Failed to parse FeatureRequest block in getFeatureRequestBlocksForDao: ${e.message}")
                false
            }
        }
    }

    // Helper methods
    fun findProposalBlock(daoId: String, proposalId: String): TrustChainBlock? {
    val joinRequests = getTrustChainCommunity().database.getBlocksWithType(JOIN_REQUEST_BLOCK)
    val proposals = getTrustChainCommunity().database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)

    return (joinRequests + proposals).find { block ->
        try {
            when (block.type) {
                JOIN_REQUEST_BLOCK -> {
                    val data = JoinRequestTransactionData(block.transaction).getData()
                    data.DAO_ID == daoId && data.SW_UNIQUE_PROPOSAL_ID == proposalId
                }
                PROPOSE_UPDATE_BLOCK -> {
                    val data = ProposeUpdateTransactionData(block.transaction).getData()
                    data.DAO_ID == daoId && data.SW_UNIQUE_PROPOSAL_ID == proposalId
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}


companion object {
    // Used as genesis block to create a new App DAO, or to indicate that someone has
    // successfully joined the DAO.
    const val JOIN_BLOCK = "P2PLAYSTORE_JOIN_DAO"

    // Used by someone not in the App DAO, when they want to join the app DAO, members of the
    // DAO will respond to this block by voting on it with vote blocks.
    const val JOIN_REQUEST_BLOCK = "P2PLAYSTORE_JOIN_REQUEST"

    // Used by members to vote "yes"/agree to the proposed transaction
    const val VOTE_YES_BLOCK = "P2PLAYSTORE_VOTE_YES"

    // Used by members to vote "no"/disagree to the proposed transaction
    const val VOTE_NO_BLOCK = "P2PLAYSTORE_VOTE_NO"

    // Used by members of the DAO to indicate that they would like a certain feature or bug
    const val FEATURE_REQUEST_BLOCK = "P2PLAYSTORE_FEATURE_REQUEST"

    // Used to propose an update based on a requested feature, practically this an extension of
    // the `currencyii` `TRANSFER_FUNDS_ASK_BLOCK` because along with an updated magnet link
    // the developer proposes to move the feature bounty to their own wallet.
    const val PROPOSE_UPDATE_BLOCK = "P2PLAYSTORE_PROPOSE_UPDATE"

    // Resulting block after an update proposal has received enough votes and the transfer has
    // been completed.
    const val UPDATE_ACCEPTED_BLOCK = "P2PLAYSTORE_UPDATE_ACCEPTED"
    }
}
