package nl.tudelft.trustchain.p2playstore.models

import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinRequestTransactionData

class DaoJoinRequest(private val block: TrustChainBlock) {
    private val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

    private val daoData = JoinRequestTransactionData(block.transaction).getData()

    /**
     * Unique identifier for this specific join request, this ID is also present in all the
     */
    val proposalId = daoData.SW_UNIQUE_PROPOSAL_ID

    /**
     * Public key of the user/ipv8 peer that is requesting to join the DOA
     */
    val requestingUser = block.publicKey.toHex()

    /**
     * The amount of signatures/votes required for this join request to be accepted.
     */
    val votesRequired: Int = daoData.SW_SIGNATURES_REQUIRED

    /**
     * Gets a list of all positive/agreement votes for this join request.
     */
    fun getUpVotes(): List<TrustChainBlock> {
        val votes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_YES_BLOCK)
        return votes.filter { vote ->
            val data = VoteYesTransactionData(vote.transaction).getData()
            data.SW_UNIQUE_PROPOSAL_ID == proposalId
        }
    }

    /**
     * Gets a list of all negative/deny votes for this join request.
     */
    fun getDownVotes(): List<TrustChainBlock> {
        val votes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_NO_BLOCK)
        return votes.filter { vote ->
            val data = VoteNoTransactionData(vote.transaction).getData()
            data.SW_UNIQUE_PROPOSAL_ID == proposalId
        }
    }

    /**
     * Checks if the request is still pending (i.e. waiting for users to vote before a final
     * decision can be made).
     */
    fun isPending(): Boolean {
        return !this.isAccepted() && !this.isDenied()
    }

    /**
     * Checks if the
     */
    fun isAccepted(): Boolean {
        return this.getUpVotes().size >= this.votesRequired
    }

    /**
     * Checks if the join request has been denied
     */
    fun isDenied(): Boolean {
        return this.getDownVotes().size >= this.votesRequired
    }
}
