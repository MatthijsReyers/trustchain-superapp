package nl.tudelft.trustchain.p2playstore.blockdata

data class VotingPoll(
        val id: String,
        val daoId: String,
        val title: String,
        val question: String,
        val yesVotes: Int,
        val noVotes: Int,
        val totalMembers: Int,
        val votingThreshold: Int,
        val isActive: Boolean,
        val hasUserVoted: Boolean,
        val userVote: Boolean? = null
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
        get() = if (totalMembers > 0) votingThreshold else 0

    val isApproved: Boolean
        get() = yesVotes >= votesNeeded && !isActive // Check if voting is closed and threshold met
}

object VotingPollHelper {
    fun createVotingPoll(
            request: FeatureRequestTD,
            solution: FeatureSolutionTD,
            votes: List<FeatureVoteTD>,
            totalDaoMembers: Int,
            daoVotingThreshold: Int,
            hasUserVoted: Boolean,
            userVote: Boolean? = null
    ): VotingPoll {
        val yesVotes = votes.count { it.isYes }
        val noVotes = votes.count { !it.isYes }

        // TODO: fix logic for this as it is a bit confusing when it is open
        // now open means not enough votes for the threshold yet
        val isActive = request.status == "OPEN"

        return VotingPoll(
                id = solution.solutionId,
                daoId = solution.daoId,
                title = "Vote on \"${request.title}\"",
                question =
                        "Should we approve the implementation: \"${solution.title}\"?\n${solution.description}", // Use solution title and description
                yesVotes = yesVotes,
                noVotes = noVotes,
                totalMembers = totalDaoMembers,
                votingThreshold = daoVotingThreshold,
                isActive = isActive,
                hasUserVoted = hasUserVoted,
                userVote = userVote
        )
    }
}
