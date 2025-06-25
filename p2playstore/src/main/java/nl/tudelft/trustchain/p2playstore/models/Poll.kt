package nl.tudelft.trustchain.p2playstore.models

import android.content.res.Resources
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData

abstract class Poll(val block: TrustChainBlock) {
    protected val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!

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
    abstract val requestingUser: String

    /**
     * Public key of the user which needs to vote yes/no on this specific block.
     */
    abstract val receivingUser: String

    /**
     * Number of votes required for the proposal in this poll to go through.
     */
    abstract val votesRequired: Int

    /**
     * Amount of people/peers that have voted in this poll.
     */
    val votes: Int get() = getYesVotes().size + getNoVotes().size

    /**
     * Is this user the receiver of this proposal? I.e. is the user even allowed to respond to this
     * block at all?
     */
    val isReceivingUser: Boolean get() {
        return trustChain.myPeer.publicKey.keyToBin().toHex() == receivingUser
    }

    /**
     * Amount of people/peers that have not yet voted out of all the people that can vote.
     *
     * Note: remember not everyone needs to vote in order for a vote to go through.
     */
    private val pendingVotes: Int get() = votesRequired - votes

    val yesPercentage: Float get() = (getYesVotes().size) / votesRequired.toFloat()

    val noPercentage: Float get() = (getNoVotes().size) / votesRequired.toFloat()

    val pendingPercentage: Float get() = (pendingVotes) / votesRequired.toFloat()

    /**
     * Checks if the poll/request has been accepted/approved by the community.
     */
    val isApproved: Boolean get() = getYesVotes().size >= this.votesRequired

    /**
     * Checks if the join request has been denied
     */
    val isDenied: Boolean get() = getNoVotes().size >= this.votesRequired

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

        val myKey = trustChain.myPeer.publicKey.keyToBin()
        return votes.firstOrNull { b -> b.publicKey.contentEquals(myKey) }
    }

    /**
     * Has this user already voted in the poll?
     */
    fun hasUserVoted(): Boolean = this.getMyVote() != null

    /**
     * Gets all the agreement signatures/votes.
     */
    fun getYesVotes(): List<VoteYesData> {
        val votes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_YES_BLOCK)
        return votes
            .mapNotNull { vote ->
                try { VoteYesTransactionData(vote.transaction).getData() }
                catch (err: Throwable) { null }
            }
            .filter { vote -> vote.SW_UNIQUE_PROPOSAL_ID == proposalId }
    }

    /**
     * Gets all the disagreement/denied votes.
     */
    fun getNoVotes(): List<VoteNoData> {
        val votes = trustChain.database.getBlocksWithType(P2pStoreCommunity.VOTE_NO_BLOCK)
        return votes
            .mapNotNull { vote ->
                try { VoteNoTransactionData(vote.transaction).getData() }
                catch (err: Throwable) { null }
            }
            .filter { vote -> vote.SW_UNIQUE_PROPOSAL_ID == proposalId }
    }

    /**
     * Gets the app that this poll relates to.
     * Note that this is an update proposal the returned app will actually be that new version.
     */
    fun getApp(): P2playApp {
        if (this.block.type == PROPOSE_UPDATE_BLOCK) {
            return P2playApp(this.block)
        }
        // Null safety: such an app must exist otherwise this poll class could never actually be
        // instantiated.
        return P2playApp.findByDaoId(this.daoId)!!
    }

    fun getAllVotes() = (this.getYesVotes() + this.getNoVotes())

    fun isUserMember(): Boolean {
        val myPublicKey = trustChain.myPeer.publicKey.keyToBin().toHex()
        // Get the latest P2playApp instance to get the most up-to-date member list
        val latestApp = getApp().getLatestVersion()
        return latestApp.isDaoMember()
    }

    open fun isUserDeveloper(): Boolean = false // Default for general polls

    fun canUserVote(): Boolean {
        return isReceivingUser && isPending && !hasUserVoted() && !isUserDeveloper()
    }

    fun getPollStatusText(resources: Resources): String {
        return when {
            isApproved -> "Approved"
            isDenied -> "Voting Closed - Not Approved"
            hasUserVoted() -> "✓ You voted ${if (getMyVote()?.type == P2pStoreCommunity.VOTE_YES_BLOCK) "Yes" else "No"}"
            isUserMember() -> "$votes of ${getApp().getDaoMemberCount()} members voted"
            else -> "$votes of ${getApp().getDaoMemberCount()} members voted"
        }
    }

    fun getPollStatusColor(resources: Resources): Int {
        return when {
            isApproved || hasUserVoted() -> resources.getColor(android.R.color.holo_green_dark, null)
            isDenied -> resources.getColor(android.R.color.darker_gray, null)
            else -> resources.getColor(android.R.color.darker_gray, null)
        }
    }
}
