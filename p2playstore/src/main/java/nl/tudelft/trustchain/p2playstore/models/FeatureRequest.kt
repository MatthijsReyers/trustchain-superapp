package nl.tudelft.trustchain.p2playstore.models

import UpdateProposalPoll
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.FEATURE_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestData
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData

/**
 * This class represents a single feature request for an app
 */
class FeatureRequest(val block: TrustChainBlock) {
    private val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

    init {
        assert(block.type == FEATURE_REQUEST_BLOCK)
    }

    val daoData: FeatureRequestData = FeatureRequestTransactionData(block.transaction).getData()

    val featureRequestId = daoData.FEATURE_REQUEST_ID
    val description = daoData.FEATURE_DESCRIPTION
    val title = daoData.FEATURE_TITLE
    val reward = daoData.FEATURE_REWARD

    val doaId = daoData.DAO_ID

    fun hasBeenFulfilled(): Boolean {
        val updates = trustChain.database.getBlocksWithType(UPDATE_ACCEPTED_BLOCK)
            .filter { b ->
                try {
                    val data = UpdateAcceptedTransactionData(b.transaction).getData()
                    return data.DAO_ID == doaId && data.FEATURE_REQUEST_ID == featureRequestId
                }
                catch (e: Throwable) {
                    return false
                }
            }
        return updates.isNotEmpty()
    }

    /**
     * Gets a list of all the solutions (i.e. software updates) that have been proposed for this
     */
    fun getSolutions(): List<UpdateProposalPoll> {
        return trustChain.database.getBlocksWithType(PROPOSE_UPDATE_BLOCK)
            .filter { b ->
                try {
                    val data = ProposeUpdateTransactionData(b.transaction).getData()
                    data.DAO_ID == doaId && data.FEATURE_REQUEST_ID == featureRequestId
                }
                catch (e: Throwable) { false }
            }
            .map { b -> UpdateProposalPoll(b) }
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
    }
}
