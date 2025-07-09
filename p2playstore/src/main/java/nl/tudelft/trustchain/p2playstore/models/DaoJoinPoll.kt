package nl.tudelft.trustchain.p2playstore.models

import android.content.Context
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData
import nl.tudelft.trustchain.p2playstore.utils.DAOJoinHelper

class DaoJoinPoll(block: TrustChainBlock): Poll(block) {

    init {
        assert(block.type == JOIN_REQUEST_BLOCK)
    }

    val blockData = JoinRequestTransactionData(block.transaction).getData()

    // See `Poll` class
    override val daoId = blockData.DAO_ID
    override val proposalId = blockData.SW_UNIQUE_PROPOSAL_ID
    override val requestingUser = block.publicKey.toHex()
    override val votesRequired: Int = blockData.SW_SIGNATURES_REQUIRED
    override val receivingUser = blockData.SW_RECEIVER_PK

    /**
     * Actually create a new JOIN block once enough votes have been cast.
     */
    fun finishJoining(context: Context) {
        val app = this.getApp()
        val signatures = this.getYesVotes()
        val daoJoinHelper = DAOJoinHelper()
        daoJoinHelper.joinBitcoinWallet(
            trustChain.myPeer,
            app.block.transaction,
            blockData,
            signatures,
            context
        )
        // Add new nonceKey after joining a DAO
        WalletManagerAndroid.getInstance().addNewNonceKey(
            this.daoId,
            context
        )
    }

    companion object {
        /**
         * Tries to find a join proposal with the given proposal ID.
         */
        fun findByProposalId(proposalId: String): DaoJoinPoll? {
            val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
            val proposals = trustChain.database
                .getBlocksWithType(JOIN_REQUEST_BLOCK)
                .mapNotNull { b ->
                    try { DaoJoinPoll(b) }
                    catch (err: Throwable) { null}
                }
                .filter { p -> p.proposalId == proposalId }

            // Get the block that this user is allowed to vote on, if one exists.
            val myProposal = proposals.find { p -> p.isReceivingUser }
            return myProposal ?: proposals.firstOrNull()
        }
    }
}
