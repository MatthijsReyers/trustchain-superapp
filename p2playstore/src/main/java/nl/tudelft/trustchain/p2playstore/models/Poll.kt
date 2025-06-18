package nl.tudelft.trustchain.p2playstore.models

import android.content.Context
import android.util.Log
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData

abstract class Poll(val block: TrustChainBlock) {
    protected val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
    protected val p2pStoreCommunity: P2pStoreCommunity = IPv8Android.getInstance().getOverlay()!!

    /**
     * Unique identifier for the DAO that belongs to this app, this ID remains the same across all
     * different versions/updates of the app.
     */
    abstract val daoId: String

    /**
     * Unique identifier for this specific request/poll, this ID is also present in all the vote
     * blocks.
     */
    abstract val proposalId: String

    /**
     * Public key of the user/ipv8 peer that created this poll
     */
    abstract val requestingUser: String;

    /**
     * Number of votes required for the proposal in this poll to go through.
     */
    abstract val votesRequired: Int

    /**
     * Total number of members/peers inside the DAO
     */
    val totalMembers: Int get() {
        val newestJoinBlock = trustChain.database.getBlocksWithType(P2pStoreCommunity.JOIN_BLOCK)
            .filter { b -> JoinDaoTransactionData(b.transaction).getData().DAO_ID == daoId }
            .maxByOrNull { b -> b.insertTime!! }
        val data = JoinDaoTransactionData(newestJoinBlock!!.transaction).getData()
        return data.SW_TRUSTCHAIN_PKS.size
    }

    /**
     * Amount of people/peers that have voted in this poll.
     */
    val votes: Int get() = getUpVotes().size + getDownVotes().size

    /**
     * Amount of people/peers that have not yet voted out of all the people that can vote.
     *
     * Note: remember not everyone needs to vote in order for a vote to go through.
     */
    val pendingVotes: Int get() = votesRequired - votes

    val yesPercentage: Float get() = (getUpVotes().size) / totalMembers.toFloat()

    val noPercentage: Float get() = (getDownVotes().size) / totalMembers.toFloat()

    val pendingPercentage: Float get() = (pendingVotes) / totalMembers.toFloat()

    /**
     * Checks if the poll/request has been accepted/approved by the community.
     */
    val isApproved: Boolean get() = getUpVotes().size >= this.votesRequired

    /**
     * Checks if the join request has been denied
     */
    val isDenied: Boolean get() = getDownVotes().size >= this.votesRequired

    /**
     * Checks if the request is still pending (i.e. waiting for users to vote before a final
     * decision can be made).
     */
    val isPending: Boolean get() = !isApproved && !isDenied

    /**
     * Tries to find the vote of this user, assuming one exists.
     */
    fun getMyVote(): TrustChainBlock? {
        val yesVotes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_YES_BLOCK)
        val noVotes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_NO_BLOCK)

        val votes = (yesVotes + noVotes).filter { vote ->
            val data = VoteYesTransactionData(vote.transaction).getData()
            data.SW_UNIQUE_PROPOSAL_ID == proposalId
        }

        val myKey = trustChain.myPeer.publicKey.keyToBin();
        return votes.firstOrNull { b -> b.publicKey.contentEquals(myKey) }
    }

    /**
     * Has this user already voted in the poll?
     */
    fun hasVoted(): Boolean = this.getMyVote() != null

    /**
     * Gets all the agreement signatures/votes.
     */
    fun getUpVotes(): List<TrustChainBlock> {
        val votes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_YES_BLOCK)
        return votes.filter { vote ->
            val data = VoteYesTransactionData(vote.transaction).getData()
            data.SW_UNIQUE_PROPOSAL_ID == proposalId
        }
    }

    /**
     * Gets all the disagreement/denied votes.
     */
    fun getDownVotes(): List<TrustChainBlock> {
        val votes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_NO_BLOCK)
        return votes.filter { vote ->
            val data = VoteNoTransactionData(vote.transaction).getData()
            data.SW_UNIQUE_PROPOSAL_ID == proposalId
        }
    }

    fun getAllVotes() = (this.getUpVotes() + this.getDownVotes())

    abstract fun submitVote(isYes: Boolean, context: Context)
}
