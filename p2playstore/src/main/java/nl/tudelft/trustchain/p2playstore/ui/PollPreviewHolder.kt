package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.util.Log
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.PollPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.DaoJoinPoll
import nl.tudelft.trustchain.p2playstore.models.Poll
import kotlin.math.max
import kotlin.math.roundToInt

class PollPreviewHolder(
    private val binding: PollPreviewBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(poll: Poll) {
        binding.pollTitle.text = "DAO join request"

        val peer = poll.requestingUser.substring(0, 6)
        binding.pollDescription.text = "Should peer $peer be allowed to join the app DAO?"

        Log.d("Matthijs", "yes: ${poll.yesPercentage}")
        Log.d("Matthijs", "no:  ${poll.noPercentage}")

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
                    R.id.action_allVotingPollsFragment_to_featureVotingFragment,
                    Bundle().apply {
                        putString("proposalId", poll.proposalId)
                        putString("pollType", pollType)
                    }
                )
        }
    }
}
