import android.content.Context
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.models.DaoJoinPoll
import nl.tudelft.trustchain.p2playstore.models.Poll
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.utils.DAOJoinHelper
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils

class UpdateProposalPoll(block: TrustChainBlock) : Poll(block) {

    init {
        assert(block.type == P2pStoreCommunity.PROPOSE_UPDATE_BLOCK)
    }

    private val daoData = ProposeUpdateTransactionData(block.transaction).getData()

    // See `Poll` class
    override val daoId = daoData.DAO_ID
    override val proposalId = daoData.SW_UNIQUE_PROPOSAL_ID
    override val requestingUser = block.publicKey.toHex()
    override val votesRequired: Int = daoData.SW_SIGNATURES_REQUIRED

    val name: String = daoData.APP_NAME;
    val description: String = daoData.APP_DESCRIPTION;
    val category: String = daoData.APP_CATEGORY;
    val magnetLink: MagnetLink = MagnetUtils.parseMagnet(daoData.APP_MAGNET_LINK);

    override fun submitVote(isYes: Boolean, context: Context) {

    }

    companion object {
        /**
         * Tries to find a join proposal with the given proposal ID.
         */
        fun findByProposalId(proposalId: String): UpdateProposalPoll? {
            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
            val blocks = trustChain.database
                .getBlocksWithType(P2pStoreCommunity.PROPOSE_UPDATE_BLOCK)
                .filter { b ->
                    val data = ProposeUpdateTransactionData(b.transaction).getData()
                    data.SW_UNIQUE_PROPOSAL_ID == proposalId
                }
            if (blocks.isNotEmpty()) {
                return UpdateProposalPoll(blocks[0])
            }
            return null;
        }
    }
}

