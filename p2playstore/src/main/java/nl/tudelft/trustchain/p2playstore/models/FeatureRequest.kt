package nl.tudelft.trustchain.p2playstore.models

import UpdateProposalPoll
import android.util.Log
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.FEATURE_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestData
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils

/**
 * This class represents a single feature request for an app
 */
class FeatureRequest(val block: TrustChainBlock) {
    private val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

    init {
        assert(block.type == FEATURE_REQUEST_BLOCK)
    }

    private val blockData: FeatureRequestData = FeatureRequestTransactionData(block.transaction).getData()

    val daoId = blockData.DAO_ID
    val featureRequestId = blockData.FEATURE_REQUEST_ID
    val description = blockData.FEATURE_DESCRIPTION
    val title = blockData.FEATURE_TITLE
    val reward = blockData.FEATURE_REWARD

    /**
     * Gets a list of all the solutions (i.e. software updates) that have been proposed for this
     */
    fun getSolutions(): List<UpdateProposalPoll> {
        // This contains one proposal block for every DAO member which still need to be filtered
        // out in order to not return the same proposal multiple times.
        val allProposals = trustChain.database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)
            .mapNotNull { b ->
                try { UpdateProposalPoll(b) }
                catch (e: Throwable) { null }
            }
            .filter { p -> p.daoId == this.daoId && p.featureRequestId == this.featureRequestId }

        // Find the actual unique proposals
        val proposals = allProposals.distinctBy { p -> p.proposalId }

        // Find all the proposals which this user was requested to vote in.
        val myKey = trustChain.myPeer.publicKey.keyToBin().toHex()
        val myProposals = allProposals.filter { p -> p.receivingUser == myKey }

        // Find all the proposals which this user cannot vote in
        val otherProposals = proposals.filter {
            p -> myProposals.none { prop -> p.proposalId == prop.proposalId }
        }

        return (myProposals + otherProposals).sortedBy { p -> p.block.timestamp }
    }

    /**
     * Has a solution (i.e. an update/feature proposal) been created and accepted for this feature
     * request?
     */
    fun solutionAccepted(): Boolean {
        val solutions = this.getSolutions()
        return solutions.any { s -> s.isApproved } && solutions.isNotEmpty()
    }

    /**
     * Submit a solution (i.e. create an update proposal) for this specific feature request.
     */
    fun submitSolution(
        title: String,
        description: String,
        magnetLink: String,
        developerBitcoinAddress: String
    ) {
        val app = P2playApp.findByDaoId(this.daoId)!!
        val latestDaoBlock = app.getLatestJoin()
        val daoData = JoinDaoTransactionData(latestDaoBlock.transaction).getData()

        val proposalId = BlockUtils.randomUUID()
        val requiredSignatures = BlockUtils.percentageToIntThreshold(
            daoData.SW_BITCOIN_PKS.size,
            daoData.SW_VOTING_THRESHOLD
        )

        // Send proposal to all DAO members
        for (memberPk in daoData.SW_TRUSTCHAIN_PKS) {
            val memberSpecificProposal = ProposeUpdateTransactionData(
                daoId = this.daoId,
                featureRequestId = this.featureRequestId,
                solutionTitle = title,
                solutionDescription = description,
                developerPublicKey = trustChain.myPeer.publicKey.pub().toString(),
                apkMagnetLink = magnetLink,
                previousWalletBlockHash = latestDaoBlock.calculateHash().toHex(),
                requiredSignatures = requiredSignatures,
                rewardAmount = this.blockData.FEATURE_REWARD,
                bitcoinPks = daoData.SW_BITCOIN_PKS,
                noncePks = daoData.SW_NONCE_PKS,                developerBitcoinAddress = developerBitcoinAddress,
                receiverPk = memberPk, // Set specific receiver
                uniqueProposalId = proposalId,
                transactionSerialized = daoData.SW_TRANSACTION_SERIALIZED,
                appName = daoData.APP_NAME,
                appDescription = daoData.APP_DESCRIPTION,
                appCategory = daoData.APP_CATEGORY,
                appIcon = daoData.APP_ICON
            )

            val transaction = memberSpecificProposal.getTransactionData()
            this.trustChain.createProposalBlock(
                memberSpecificProposal.blockType,
                transaction,
                this.trustChain.myPeer.publicKey.keyToBin()
            )
        }
    }

    companion object {
        /**
         * Tries to find a feature request with the given ID
         */
        fun findById(featureRequestId: String): FeatureRequest? {
            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
            val block = trustChain.database.getBlocksWithType(FEATURE_REQUEST_BLOCK)
                .find { b ->
                    val data = FeatureRequestTransactionData(b.transaction).getData()
                    data.FEATURE_REQUEST_ID == featureRequestId
                }
            if (block == null) return null
            return FeatureRequest(block)
        }

        /**
         * Create a feature request proposal block on trust chain.
         */
        fun createFeatureRequest(daoId: String, title: String, description: String, reward: Long) {

            // Does this DAO even exist and is the user a member?
            val app = P2playApp.findByDoaId(daoId)
            if (app == null || !app.isDaoMember()) return

            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

            val featureRequestData = FeatureRequestTransactionData(
                daoId = daoId,
                title = title,
                description = description,
                reward = reward,
                requesterPublicKey = trustChain.myPeer.publicKey.pub().toString(),
            )

            val transaction = featureRequestData.getTransactionData()

            trustChain.createProposalBlock(
                featureRequestData.blockType,
                transaction,
                trustChain.myPeer.publicKey.keyToBin()
            )

            Log.d("P2PlayStore", "Created Feature Request proposal block for DAO $daoId")
        }
    }
}
