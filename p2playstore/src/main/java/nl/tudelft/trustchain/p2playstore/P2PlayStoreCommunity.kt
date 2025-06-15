package nl.tudelft.trustchain.p2playstore

import android.app.Activity
import android.content.Context
import android.util.Log
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
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

    private val daoCreateHelper = DAOCreateHelper()
    private val daoJoinHelper = DAOJoinHelper()
    private val daoTransferFundsHelper = DAOTransferFundsHelper()

    /**
     * Create a bitcoin genesis wallet and broadcast the result on trust chain. The bitcoin
     * transaction may take some time to finish.
     * @throws
     * - exception if something goes wrong with creating or broadcasting bitcoin transaction.
     * @param entranceFee
     * - Long, the entrance fee for joining the DAO.
     * @param threshold
     * - Int, the percentage of members that need to vote before allowing someone in the DAO.
     */
    fun createBitcoinGenesisWallet(
            entranceFee: Long,
            iconIndex: Int,
            name: String,
            description: String,
            magnetLink: String,
            category: String,
            threshold: Int,
            context: Context
    ): JoinDaoTransactionData {
        return daoCreateHelper.createBitcoinGenesisWallet(
                myPeer,
                entranceFee,
                iconIndex,
                name,
                description,
                magnetLink,
                category,
                threshold,
                context
        )
    }

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
     * 3.1 Send a proposal block on trustchain to ask for the signatures. Assumed that people agreed
     * to the transfer.
     * @param walletBlock
     * - TrustChainBlock, describes the wallet where the transfer is from
     * @param receiverAddressSerialized
     * - String, the address where the transaction needs to go
     * @param satoshiAmount
     * - Long, the amount that needs to be transferred
     * @return the proposal block
     */
    fun proposeTransferFunds(
            walletBlock: TrustChainBlock,
            receiverAddressSerialized: String,
            satoshiAmount: Long
    ): ProposeUpdateTransactionData {
        return daoTransferFundsHelper.proposeTransferFunds(
                myPeer,
                walletBlock,
                receiverAddressSerialized,
                satoshiAmount
        )
    }

    /**
     * 3.2 Transfer funds from an existing shared wallet to a third-party. Broadcast bitcoin
     * transaction.
     * @param walletData
     * - SWJoinBlockTD, the data about the wallet when joining the wallet
     * @param walletBlockData
     * - TrustChainTransaction, describes the wallet where the transfer is from
     * @param blockData
     * - SWTransferFundsAskBlockTD, the block where the other users are voting on
     * @param responses
     * - List<SWResponseSignatureBlockTD>, the list with positive responses on the voting
     * @param receiverAddress
     * - String, the address where the transfer needs to go
     * @param satoshiAmount
     * - Long, the amount that needs to be transferred
     */
    fun transferFunds(
            walletData: JoinDoaData,
            walletBlockData: TrustChainTransaction,
            blockData: ProposeUpdateData,
            responses: List<VoteYesData>,
            receiverAddress: String,
            satoshiAmount: Long,
            context: Context,
            activity: Activity
    ) {
        daoTransferFundsHelper.transferFunds(
                myPeer,
                walletData,
                walletBlockData,
                blockData,
                responses,
                receiverAddress,
                satoshiAmount,
                context,
                activity
        )
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
                    .maxByOrNull { b -> b.insertTime!! }
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
                    val data = JoinDaoTransactionData(block.transaction).getData()
                    data.DAO_ID == daoId
                } catch (e: Exception) {
                    false
                }
            }
            .maxByOrNull { it.insertTime!! }
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
     * Get the public key of the one that is receiving the request
     * @return string
     */
    private fun fetchSignatureRequestReceiver(block: TrustChainBlock): String {
        if (block.type == JOIN_REQUEST_BLOCK) {
            return JoinRequestTransactionData(block.transaction).getData().SW_RECEIVER_PK
        }

        if (block.type == PROPOSE_UPDATE_BLOCK) {
            return ProposeUpdateTransactionData(block.transaction).getData().SW_RECEIVER_PK
        }

        return "invalid-pk"
    }

    fun fetchSignatureRequestProposalId(block: TrustChainBlock): String {
        if (block.type == JOIN_REQUEST_BLOCK) {
            return JoinRequestTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
        }
        if (block.type == PROPOSE_UPDATE_BLOCK) {
            return ProposeUpdateTransactionData(block.transaction)
                .getData()
                .SW_UNIQUE_PROPOSAL_ID
        }

        return "invalid-proposal-id"
    }

    /**
     * Fetch all join and transfer proposals in descending timestamp order. Speed assumption: each
     * proposal has a unique proposal ID (distinct by unique proposal id, without taking the unique
     * wallet id into account).
     */
    fun fetchProposalBlocks(): List<TrustChainBlock> {
        val joinProposals = getTrustChainCommunity().database.getBlocksWithType(JOIN_REQUEST_BLOCK)
        val transferProposals =
            getTrustChainCommunity().database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)
        return joinProposals
            .union(transferProposals)
            .filter {
                fetchSignatureRequestReceiver(it) == myPeer.publicKey.keyToBin().toHex() &&
                    !checkEnoughFavorSignatures(it)
            }
            .distinctBy { fetchSignatureRequestProposalId(it) }
            .sortedByDescending { it.timestamp }
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
     * Given a shared wallet proposal block, calculate the signature and respond with a trust chain
     * block.
     */
    fun joinAskBlockReceived(
        block: TrustChainBlock,
        myPublicKey: ByteArray,
        votedInFavor: Boolean,
        context: Context
    ) {
        val latestHash =
            JoinRequestTransactionData(block.transaction).getData().SW_PREVIOUS_BLOCK_HASH
        val mostRecentSWBlock =
            fetchLatestSharedWalletBlock(latestHash.hexToBytes())
                ?: throw IllegalStateException("Most recent DAO block not found")
        val joinBlock = JoinDaoTransactionData(mostRecentSWBlock.transaction).getData()
        val oldTransaction = joinBlock.SW_TRANSACTION_SERIALIZED

        DAOJoinHelper.joinAskBlockReceived(
            oldTransaction,
            block,
            joinBlock,
            myPublicKey,
            votedInFavor,
            context
        )
    }

    /**
     * Given a shared wallet transfer fund proposal block, calculate the signature and respond with
     * a trust chain block.
     */
    fun transferFundsBlockReceived(
        block: TrustChainBlock,
        myPublicKey: ByteArray,
        votedInFavor: Boolean,
        context: Context
    ) {
        val latestHash =
            ProposeUpdateTransactionData(block.transaction)
                .getData()
                .SW_PREVIOUS_BLOCK_HASH
        val mostRecentSWBlock =
            fetchLatestSharedWalletBlock(latestHash.hexToBytes())
                ?: throw IllegalStateException("Most recent DAO block not found")
        val transferBlock = UpdateAcceptedTransactionData(mostRecentSWBlock.transaction).getData()
        val oldTransaction = transferBlock.SW_TRANSACTION_SERIALIZED

        DAOTransferFundsHelper.transferFundsBlockReceived(
            oldTransaction,
            block,
            transferBlock,
            myPublicKey,
            votedInFavor,
            context
        )
    }

    /** Given a proposal, check if the number of signatures required is met */
    fun checkEnoughFavorSignatures(block: TrustChainBlock): Boolean {
        if (block.type == JOIN_REQUEST_BLOCK) {
            val data = JoinRequestTransactionData(block.transaction).getData()
            val signatures =
                ArrayList(fetchProposalResponses(data.DAO_ID, data.SW_UNIQUE_PROPOSAL_ID))
            return data.SW_SIGNATURES_REQUIRED <= signatures.size
        }
        if (block.type == PROPOSE_UPDATE_BLOCK) {
            val data = ProposeUpdateTransactionData(block.transaction).getData()
            val signatures =
                ArrayList(fetchProposalResponses(data.DAO_ID, data.SW_UNIQUE_PROPOSAL_ID))
            return data.SW_SIGNATURES_REQUIRED <= signatures.size
        }

        return false
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
            requesterPublicKey = myPeer.publicKey.pub().toString()
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
    suspend fun createFeatureSolution(
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

        val daoData = JoinDaoTransactionData(latestDaoBlock.transaction).getData()

        // Get the feature request to know the reward amount
        val featureRequest = getFeatureRequestsForDao(daoId)
            .find { it.FEATURE_REQUEST_ID == featureRequestId }
            ?: throw IllegalStateException("Feature request not found: $featureRequestId")

        val proposalId = BlockUtils.randomUUID()
        val requiredSignatures = BlockUtils.percentageToIntThreshold(
            daoData.SW_BITCOIN_PKS.size,
            daoData.SW_VOTING_THRESHOLD
        )

        val featureSolutionData = ProposeUpdateTransactionData(
            daoId = daoId,
            featureRequestId = featureRequestId,
            solutionTitle = solutionTitle,
            solutionDescription = solutionDescription,
            developerPublicKey = myPeer.publicKey.pub().toString(),
            apkMagnetLink = apkMagnetLink,
            previousWalletBlockHash = latestDaoBlock.calculateHash().toHex(),
            requiredSignatures = requiredSignatures,
            rewardAmount = featureRequest.FEATURE_REWARD,
            bitcoinPks = daoData.SW_BITCOIN_PKS,
            developerBitcoinAddress = developerBitcoinAddress,
            receiverPk = "", // Will be set for each member
            uniqueProposalId = proposalId,
            transactionSerialized = daoData.SW_TRANSACTION_SERIALIZED,
            appName = daoData.APP_NAME,
            appDescription = daoData.APP_DESCRIPTION,
            appCategory = daoData.APP_CATEGORY,
            appIcon = daoData.APP_ICON
        )

        // Send proposal to all DAO members
        for (memberPk in daoData.SW_TRUSTCHAIN_PKS) {
            val memberSpecificProposal = ProposeUpdateTransactionData(
                daoId = daoId,
                featureRequestId = featureRequestId,
                solutionTitle = solutionTitle,
                solutionDescription = solutionDescription,
                developerPublicKey = myPeer.publicKey.pub().toString(),
                apkMagnetLink = apkMagnetLink,
                previousWalletBlockHash = latestDaoBlock.calculateHash().toHex(),
                requiredSignatures = requiredSignatures,
                rewardAmount = featureRequest.FEATURE_REWARD,
                bitcoinPks = daoData.SW_BITCOIN_PKS,
                developerBitcoinAddress = developerBitcoinAddress,
                receiverPk = memberPk, // Set specific receiver
                uniqueProposalId = proposalId,
                transactionSerialized = daoData.SW_TRANSACTION_SERIALIZED,
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


//    fun createFeatureVote(
//            daoId: String,
//            featureId: String,
//            solutionId: String,
//            isYes: Boolean
//    ) {
//        val voteId = BlockUtils.randomUUID()
//
//        val featureVoteData =
//                FeatureVoteTransactionData(
//                        voteId = voteId,
//                        solutionId = solutionId,
//                        featureId = featureId,
//                        daoId = daoId,
//                        isYes = isYes,
//                        voterPublicKey = myPeer.publicKey.pub().toString()
//                )
//
//        val transaction = featureVoteData.getTransactionData()
//
//        getTrustChainCommunity()
//                .createProposalBlock(
//                        featureVoteData.blockType,
//                        transaction,
//                        myPeer.publicKey.keyToBin() //TODO: now sending to myself fix
//                )
//        Log.d(
//                "P2PlayStore",
//                "Created Feature Vote block for Solution $solutionId (Feature $featureId) in DAO $daoId. Vote: $isYes"
//        )
//    }


    /**
     * Vote on any proposal (join request, feature solution, fund transfer)
     */
    fun voteOnProposal(
        daoId: String,
        proposalId: String,
        isYes: Boolean,
        context: Context
    ) {
        // Find the proposal block
        val proposalBlock = findProposalBlock(daoId, proposalId)
            ?: throw IllegalStateException("Proposal not found: $proposalId")

        when (proposalBlock.type) {
            JOIN_REQUEST_BLOCK -> {
                joinAskBlockReceived(
                    proposalBlock,
                    myPeer.publicKey.keyToBin(),
                    isYes,
                    context
                )
            }
            PROPOSE_UPDATE_BLOCK -> {
                transferFundsBlockReceived(
                    proposalBlock,
                    myPeer.publicKey.keyToBin(),
                    isYes,
                    context
                )
            }
            else -> throw IllegalArgumentException("Unknown proposal type: ${proposalBlock.type}")
        }

//        Log.d("P2PlayStore", "Vote submitted: ${if (isYes) \"Yes\" else \"No\"} for proposal $proposalId")
    }


    /**
     * Check if the number of required votes are more than the number of possible votes minus the
     * negative votes.
     */
    fun canWinTransferRequest(data: ProposeUpdateData): Boolean {
        val againstSignatures =
            ArrayList(
                fetchNegativeProposalResponses(
                    data.DAO_ID,
                    data.SW_UNIQUE_PROPOSAL_ID
                )
            )
        val totalVoters = data.SW_BITCOIN_PKS
        val requiredVotes = data.SW_SIGNATURES_REQUIRED

        return requiredVotes <= totalVoters.size - againstSignatures.size
    }


        /**
         * Get all feature requests for a DAO
         */
        suspend fun getFeatureRequestsForDao(daoId: String): List<FeatureRequestData> {
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
            .filter { it.DAO_ID == daoId }
    }

    /**
     * Fetches all feature request blocks for a specific DAO.
     * Returns a list of TrustChainBlock.
     */
    suspend fun getFeatureRequestBlocksForDao(daoId: String): List<TrustChainBlock> {
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
    /**
     * Get all feature solutions for a DAO
     */
    suspend fun getFeatureSolutionsForDao(daoId: String): List<ProposeUpdateData> {
    val blocks = getTrustChainCommunity().database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)

    return blocks
        .mapNotNull { block ->
            try {
                val proposalData = ProposeUpdateTransactionData(block.transaction).getData()
                // Only return feature solutions (not regular fund transfers)
                if (proposalData.FEATURE_REQUEST_ID != null) proposalData else null
            } catch (e: Exception) {
                Log.e("P2PlayStoreCommunity", "Failed to parse ProposeUpdate block: ${e.message}")
                null
            }
        }
        .filter { it.DAO_ID == daoId }
}


    /**
     * Get voting poll for a specific proposal
     */
    suspend fun getVotingPoll(daoId: String, proposalId: String): VotingPoll? {
    val proposalBlock = findProposalBlock(daoId, proposalId) ?: return null
    val yesVotes = fetchProposalResponses(daoId, proposalId)
    val noVotes = fetchNegativeProposalResponses(daoId, proposalId)

    // Get DAO info for voting thresholds and member count
    val daoBlock = fetchLatestSharedWalletBlockByDaoId(daoId) ?: return null
    val daoData = JoinDaoTransactionData(daoBlock.transaction).getData()

    val myPublicKey = myPeer.publicKey.keyToBin().toHex()
    val hasUserVoted = yesVotes.any { it.SW_BITCOIN_PK == myPublicKey } ||
        noVotes.any { it.SW_BITCOIN_PK == myPublicKey }
    val userVote = when {
        yesVotes.any { it.SW_BITCOIN_PK == myPublicKey } -> true
        noVotes.any { it.SW_BITCOIN_PK == myPublicKey } -> false
        else -> null
    }

    return when (proposalBlock.type) {
        JOIN_REQUEST_BLOCK -> {
            val joinRequestData = JoinRequestTransactionData(proposalBlock.transaction).getData()
            CreateVotingPoll.createJoinRequestPoll(
                proposalBlock, joinRequestData, yesVotes, noVotes,
                daoData.SW_TRUSTCHAIN_PKS.size, daoData.SW_VOTING_THRESHOLD,
                hasUserVoted, userVote
            )
        }
        PROPOSE_UPDATE_BLOCK -> {
            val proposalData = ProposeUpdateTransactionData(proposalBlock.transaction).getData()
            if (proposalData.FEATURE_REQUEST_ID != null) {
                // This is a feature solution
                val featureRequest = getFeatureRequestsForDao(daoId)
                    .find { it.FEATURE_REQUEST_ID == proposalData.FEATURE_REQUEST_ID }
                    ?: return null
                CreateVotingPoll.createFeatureSolutionPoll(
                    featureRequest, proposalData, yesVotes, noVotes,
                    daoData.SW_TRUSTCHAIN_PKS.size, daoData.SW_VOTING_THRESHOLD,
                    hasUserVoted, userVote
                )
            } else {
                android.util.Log.d("P2PlayStore", "Proposal is not a feature solution")
                val featureRequest = getFeatureRequestsForDao(daoId)
                    .find { it.FEATURE_REQUEST_ID == proposalData.FEATURE_REQUEST_ID }
                    ?: return null
                CreateVotingPoll.createFeatureSolutionPoll(
                    featureRequest, proposalData, yesVotes, noVotes,
                    daoData.SW_TRUSTCHAIN_PKS.size, daoData.SW_VOTING_THRESHOLD,
                    hasUserVoted, userVote
                )
//                    // This is a regular fund transfer
//                    CreateVotingPoll.createFundTransferPoll(
//                        proposalData, yesVotes, noVotes,
//                        daoData.SW_TRUSTCHAIN_PKS.size, daoData.SW_VOTING_THRESHOLD,
//                        hasUserVoted, userVote
//                    )
            }
        }
        else -> null
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

//    suspend fun getSolutionsForFeature(
//            daoId: String,
//            featureId: String? = null
//    ): List<FeatureSolutionTD> {
//        return emptyList()
//    }


//    /**
//     * Fetches all solution blocks for a specific feature request within a DAO.
//     * Returns pairs of TrustChainBlock and FeatureSolutionTD.
//     */
//    suspend fun getSolutionBlocksForFeature(daoId: String, featureId: String): List<Pair<TrustChainBlock, FeatureSolutionTD>> {
//        val blocks = getTrustChainCommunity().database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)
//
//        return blocks
//            .mapNotNull { block ->
//                try {
//                    val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
//                    block to solutionData
//                } catch (e: Exception) {
//                    Log.e("P2PlayStoreCommunity", "Failed to parse FeatureSolution block: ${e.message}")
//                    null
//                }
//            }
//            .filter { (_, solutionData) ->
//                // Filter by DAO ID and Feature ID from the block data
//                solutionData.daoId == daoId && solutionData.featureId == featureId
//            }
//            .sortedByDescending { it.first.timestamp.time }
//    }


//    suspend fun getVotesForSolution(
//            daoId: String,
//            solutionId: String? = null
//    ): List<FeatureVoteTD> {
//        val blocks = getTrustChainCommunity().database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)
//
//        return blocks
//                .mapNotNull { block ->
//                    try {
//                        FeatureVoteTransactionData(block.transaction).getData()
//                    } catch (e: Exception) {
//                        Log.e(
//                                "P2PlayStoreCommunity",
//                                "Failed to parse FeatureVote block: ${e.message}"
//                        )
//                        null
//                    }
//                }
//                .filter { vote ->
//                    // Filter by DAO ID and optionally by Solution ID
//                    vote.daoId == daoId && (solutionId == null || vote.solutionId == solutionId)
//                }
//    }
//    /**
//     * Fetches the latest solution block that is linked to an OPEN feature request within a DAO.
//     */
//    suspend fun fetchLatestVotableSolutionBlock(
//        daoId: String,
//        featureRequests: List<FeatureRequestTD>
//    ): Pair<FeatureSolutionTD, TrustChainBlock>? {
//        return getSolutionBlocksForDaoAndFeature(daoId)
//            .mapNotNull { block ->
//                try {
//                    val sol = FeatureSolutionTransactionData(block.transaction).getData()
//                    val req = featureRequests.find { it.featureId == sol.featureId }
//                    // Return pair only if the corresponding feature request is OPEN
//                    if (req?.status == "OPEN") sol to block else null
//                } catch (e: Exception) {
//                    Log.e("P2PlayStoreCommunity", "Failed to parse FeatureSolution block in fetchLatestVotableSolutionBlock: ${e.message}")
//                    null
//                }
//            }
//            // List is already sorted by timestamp descending from getSolutionBlocksForDaoAndFeature
//            .firstOrNull()
//    }

                /**
                //     * Fetches the latest OPEN feature request block within a DAO that has no solutions submitted yet.
                //     */
//    suspend fun fetchLatestPendingRequestBlock(
//        daoId: String
//    ): FeatureRequestTD? {
//        val allReqs = getFeatureRequestsForDao(daoId)
//        val allSolsGrouped = getAllSolutionsGroupedByFeature(daoId)
//
//        return allReqs
//            .filter { it.status == "OPEN" && allSolsGrouped[it.featureId].isNullOrEmpty() }
//            .maxByOrNull { req ->
//                // TODO: If featureId is just a random UUID, we might need another way to get the request block.
//                getDaoBlock(req.featureId)?.timestamp?.time ?: 0L
//            }
//    }
//
    suspend fun getDaoBlock(blockId: String): TrustChainBlock? {
    val parts = blockId.split(".")
    if (parts.size != 2) {
        Log.e("P2PlayStoreCommunity", "Invalid block ID format: $blockId")
        return null
    }
    return try {
        val publicKey = parts[0].hexToBytes()
        val sequenceNumber = parts[1].toUInt()
        getTrustChainCommunity().database.get(publicKey, sequenceNumber)
    } catch (e: Exception) {
        Log.e("P2PlayStoreCommunity", "Error getting DAO block $blockId: ${e.message}")
        null
    }
}
//
//    /**
//     * Fetches all solution data for a specific DAO, grouped by feature ID.
//     * This is a private helper for fetchLatestPendingRequestBlock.
//     */
//    private fun getAllSolutionsGroupedByFeature(daoId: String): Map<String, List<FeatureSolutionTD>> {
//        return getSolutionBlocksForDaoAndFeature(daoId)
//            .mapNotNull { block ->
//                try {
//                    FeatureSolutionTransactionData(block.transaction).getData()
//                } catch (e: Exception) {
//                    Log.e("P2PlayStoreCommunity", "Failed to parse FeatureSolution block in getAllSolutionsGroupedByFeature: ${e.message}")
//                    null
//                }
//            }
//            .filter { it.daoId == daoId }
//            .groupBy { it.featureId } // group by request
//    }

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
    // fix, note that others do NOT vote on the contents of this block to indicate how much
    // they want it or something. Other members can instead propose updates to the app in order
    // to claim the bounty for the feature request and other users will vote on that update
    // block.
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
