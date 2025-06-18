package nl.tudelft.trustchain.p2playstore.models

import android.content.Context
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import nl.tudelft.trustchain.p2playstore.utils.DAOJoinHelper

class DaoJoinPoll(block: TrustChainBlock): Poll(block) {

    init {
        assert(block.type == P2pStoreCommunity.JOIN_REQUEST_BLOCK)
    }

    val daoData = JoinRequestTransactionData(block.transaction).getData()

    // See `Poll` class
    override val daoId = daoData.DAO_ID
    override val proposalId = daoData.SW_UNIQUE_PROPOSAL_ID
    override val requestingUser = block.publicKey.toHex()
    override val votesRequired: Int = daoData.SW_SIGNATURES_REQUIRED

    override fun submitVote(isYes: Boolean, context: Context) {
        val myPublicKey = this.trustChain.myPeer.publicKey.keyToBin()

        val latestHash = JoinRequestTransactionData(block.transaction)
            .getData().SW_PREVIOUS_BLOCK_HASH

        val mostRecentSWBlock =
            p2pStoreCommunity.fetchLatestSharedWalletBlock(latestHash.hexToBytes())
                ?: throw IllegalStateException("Most recent DAO block not found")

        val joinBlock = JoinDaoTransactionData(mostRecentSWBlock.transaction).getData()
        val oldTransaction = joinBlock.SW_TRANSACTION_SERIALIZED

        DAOJoinHelper.joinAskBlockReceived(
            oldTransaction,
            block,
            joinBlock,
            myPublicKey,
            isYes,
            context
        )
    }

    companion object {
        /**
         * Tries to find a join proposal with the given proposal ID.
         */
        fun findByProposalId(proposalId: String): DaoJoinPoll? {
            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
            val blocks = trustChain.database.getBlocksWithType(P2pStoreCommunity.JOIN_REQUEST_BLOCK)
                .filter { b ->
                    val data = JoinRequestTransactionData(b.transaction).getData()
                    data.SW_UNIQUE_PROPOSAL_ID == proposalId
                }
            if (blocks.isNotEmpty()) {
                return DaoJoinPoll(blocks[0])
            }
            return null;
        }
    }
}
