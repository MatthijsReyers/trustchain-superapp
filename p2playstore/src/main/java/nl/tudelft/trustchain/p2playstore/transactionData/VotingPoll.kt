package nl.tudelft.trustchain.p2playstore.transactionData

import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.toHex

/**
 * Represents a voting poll for any type of DAO proposal
 */
data class VotingPoll(
    val id: String,
    val daoId: String,
    val type: VotingPollType,
    val title: String,
    val description: String,
    val yesVotes: Int,
    val noVotes: Int,
    val totalMembers: Int,
    val votingThreshold: Int, // This is the integer number of votes needed, not percentage
    val isActive: Boolean,
    var hasUserVoted: Boolean,
    val userVote: Boolean? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    val pendingVotes: Int
        get() = totalMembers - (yesVotes + noVotes)

    val yesPercentage: Int
        get() = if (totalMembers > 0) (yesVotes * 100) / totalMembers else 0

    val noPercentage: Int
        get() = if (totalMembers > 0) (noVotes * 100) / totalMembers else 0

    val pendingPercentage: Int
        get() = if (totalMembers > 0) (pendingVotes * 100) / totalMembers else 100

    val votesNeeded: Int
        get() = votingThreshold

    val isApproved: Boolean
        get() = yesVotes >= votesNeeded && !isActive

    val canStillPass: Boolean
        get() = yesVotes + pendingVotes >= votesNeeded

    val totalVotesCast: Int
        get() = yesVotes + noVotes
}

enum class VotingPollType {
    JOIN_REQUEST,
    FEATURE_SOLUTION
}

object CreateVotingPoll {

    /**
     * Creates a voting poll for a join request
     */
    fun createJoinRequestPoll(
        joinRequestBlock: TrustChainBlock,
        joinRequestData: JoinRequestData,
        yesVotes: List<VoteYesData>,
        noVotes: List<VoteNoData>,
        totalMembers: Int,
        votesNeeded: Int,
        hasUserVoted: Boolean,
        userVote: Boolean? = null
    ): VotingPoll {
        val requesterPk = joinRequestBlock.publicKey.toString()

        // Active if not yet approved AND can still reach the required votes
        val canStillPass = yesVotes.size + (totalMembers - (yesVotes.size + noVotes.size)) >= votesNeeded
        val isActive = yesVotes.size < votesNeeded && canStillPass


        return VotingPoll(
            id = joinRequestData.SW_UNIQUE_PROPOSAL_ID,
            daoId = joinRequestData.DAO_ID,
            type = VotingPollType.JOIN_REQUEST,
            title = "Join Request",
            description = "User ${requesterPk.take(8)}... wants to join the DAO",
            yesVotes = yesVotes.size,
            noVotes = noVotes.size,
            totalMembers = totalMembers,
            votingThreshold = votesNeeded,
            isActive = isActive,
            hasUserVoted = hasUserVoted,
            userVote = userVote,
            metadata = mapOf(
                "requesterPublicKey" to requesterPk
            )
        )
    }

    /**
     * Creates a voting poll for a feature solution
     */
    fun createFeatureSolutionPoll(
        featureRequest: FeatureRequestData,
        solution: ProposeUpdateData,
        yesVotes: List<VoteYesData>,
        noVotes: List<VoteNoData>,
        totalMembers: Int,
        votesNeeded: Int,
        hasUserVoted: Boolean,
        userVote: Boolean? = null
    ): VotingPoll {
        // Active if not yet approved AND can still reach the required votes
        val canStillPass = yesVotes.size + (totalMembers - (yesVotes.size + noVotes.size)) >= votesNeeded
        val isActive = yesVotes.size < votesNeeded && canStillPass


        return VotingPoll(
            id = solution.SW_UNIQUE_PROPOSAL_ID,
            daoId = solution.DAO_ID,
            type = VotingPollType.FEATURE_SOLUTION,
            title = "Feature Solution: \"${featureRequest.FEATURE_TITLE}\"",
            description = "Solution: ${solution.SOLUTION_TITLE}\n${solution.SOLUTION_DESCRIPTION}",
            yesVotes = yesVotes.size,
            noVotes = noVotes.size,
            totalMembers = totalMembers,
            votingThreshold = votesNeeded,
            isActive = isActive,
            hasUserVoted = hasUserVoted,
            userVote = userVote,
            metadata = mapOf(
                "featureTitle" to featureRequest.FEATURE_TITLE,
                "featureReward" to featureRequest.FEATURE_REWARD,
                "solutionTitle" to (solution.SOLUTION_TITLE ?: ""),
                "developerPublicKey" to (solution.DEVELOPER_PUBLIC_KEY ?: ""),
                "apkMagnetLink" to solution.APP_MAGNET_LINK
            )
        )
    }
}
