package nl.tudelft.trustchain.p2playstore.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.databinding.ItemVotingPollBinding
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll

class VotingPollsAdapter(
    private val onPollClick: (VotingPoll) -> Unit
) : RecyclerView.Adapter<VotingPollsAdapter.PollViewHolder>() {

    private var polls = listOf<VotingPoll>()

    fun updatePolls(newPolls: List<VotingPoll>) {
        polls = newPolls
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        val binding = ItemVotingPollBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PollViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        holder.bind(polls[position])
    }

    override fun getItemCount(): Int = polls.size

    inner class PollViewHolder(private val binding: ItemVotingPollBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(poll: VotingPoll) {
            binding.updateTitle.text = poll.title
            binding.votingQuestion.text = poll.question
            // Use votesNeeded from the poll data class
            binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed"

            binding.yesPercentage.text = "${poll.yesPercentage}%"
            binding.noPercentage.text = "${poll.noPercentage}%"
            binding.pendingPercentage.text = "${poll.pendingPercentage}%"

            binding.totalVotes.text = "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted"

            // Update progress bars
            updateProgressBars(poll)

            // Set status
            when {
                !poll.isActive && poll.yesVotes >= poll.votesNeeded -> {
                    binding.votingStatus.text = "Approved"
                    binding.votingStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark))
                }
                !poll.isActive -> {
                    binding.votingStatus.text = "Closed"
                    binding.votingStatus.setTextColor(binding.root.context.getColor(android.R.color.darker_gray))
                }
                poll.hasUserVoted -> {
                    binding.votingStatus.text = "✓ Voted"
                    binding.votingStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark))
                }
                else -> {
                    binding.votingStatus.text = "Active"
                    binding.votingStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_blue_bright))
                }
            }

            // Set click listener
            binding.root.setOnClickListener {
                onPollClick(poll)
            }
        }

//        TODO: needs heavy fixing for width and flow
        private fun updateProgressBars(poll: VotingPoll) {
            binding.root.post {
                val containerWidth = binding.root.width
                if (containerWidth > 0) {
                    val availableWidth = containerWidth - binding.root.context.resources.getDimensionPixelSize(nl.tudelft.trustchain.p2playstore.R.dimen.progress_bar_margin_horizontal) * 2


                    val yesWidth = maxOf(1, (availableWidth * poll.yesPercentage / 100))
                    val noWidth = maxOf(1, (availableWidth * poll.noPercentage / 100))
                    val pendingWidth = maxOf(1, (availableWidth * poll.pendingPercentage / 100))

                    binding.yesProgressBar.layoutParams.width = yesWidth
                    binding.noProgressBar.layoutParams.width = noWidth
                    binding.pendingProgressBar.layoutParams.width = pendingWidth

                    binding.yesProgressBar.requestLayout()
                    binding.noProgressBar.requestLayout()
                    binding.pendingProgressBar.requestLayout()
                }
            }
        }
    }
}
