package nl.tudelft.trustchain.p2playstore.ui

import UpdateProposalPoll
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.PollPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.DaoJoinPoll
import nl.tudelft.trustchain.p2playstore.models.Poll
import kotlin.math.max
import kotlin.math.roundToInt

class PollPreviewHolder(
    private val binding: PollPreviewBinding,
    private val dest: Int = R.id.action_allVotingPollsFragment_to_featureVotingFragment
) : RecyclerView.ViewHolder(binding.root) {

    fun hide() {
        binding.root.visibility = View.GONE
    }

    fun bind(poll: Poll) {
        binding.root.visibility = View.VISIBLE

        if (poll is DaoJoinPoll) {
            binding.pollTitle.text = "DAO join request"
            val peer = poll.requestingUser.substring(0, 6)
            binding.pollDescription.text = "Should peer $peer be allowed to join the app DAO?"
        }
        else {
            val update = poll as UpdateProposalPoll
            binding.pollTitle.text = "App update '${update.name}'"
            binding.pollDescription.text = "Do you want this feature/does the proposed update " +
                "work as expected?"
        }

        binding.progressBar.post {
            // Clamp value at 1, because 0 maps to 100% for some reason...
            binding.yesProgressBar.layoutParams.width =
                max(1, (binding.progressBar.width * poll.yesPercentage).roundToInt())
            binding.noProgressBar.layoutParams.width =
                max(1, (binding.progressBar.width * poll.noPercentage).roundToInt())
            binding.yesProgressBar.requestLayout()
            binding.noProgressBar.requestLayout()
        }

        binding.votingProgress.text = "${poll.votes} of ${poll.votesRequired} members voted"

        binding.root.setOnClickListener {
            val pollType = if (poll is DaoJoinPoll) "join" else "update"
            itemView.findNavController()
                .navigate(
                    dest,
                    Bundle().apply {
                        putString("proposalId", poll.proposalId)
                        putString("pollType", pollType)
                    }
                )
        }
    }
}
