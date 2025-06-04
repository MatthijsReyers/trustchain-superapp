package nl.tudelft.trustchain.p2playstore.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.databinding.ItemFeatureRequestBinding
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTD

class FeatureListAdapter(
    private val onItemClick: (FeatureRequestTD, String) -> Unit
) : RecyclerView.Adapter<FeatureListAdapter.FeatureViewHolder>() {

    private var features = listOf<FeatureRequestWithSolutions>()

    fun updateFeatures(newFeatures: List<FeatureRequestWithSolutions>) {
        features = newFeatures
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val binding = ItemFeatureRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FeatureViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        holder.bind(features[position])
    }

    override fun getItemCount() = features.size

    inner class FeatureViewHolder(
        private val binding: ItemFeatureRequestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FeatureRequestWithSolutions) {
            val feature = item.featureRequest
            val solutions = item.solutions
            val votes = item.votes // Get the votes list

            binding.tvFeatureTitle.text = feature.title
            binding.tvFeatureDescription.text = feature.description
            binding.tvReward.text = "${feature.reward} sats"

            // Set status and button text based on feature type and associated data
            when (feature.requestType) {
                P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE -> {
                    binding.tvFeatureTitle.text = "Join Request: ${feature.title}"
                    binding.tvSolutionCount.text = "${votes.size} votes" // Display actual vote count
                    binding.tvStatus.text = "Voting in Progress" // Assume voting is in progress if listed
                    binding.btnAction.text = "Vote / View Status"

                    binding.btnAction.setOnClickListener {
                        // Navigate to FeatureVotingFragment for this join request
                        onItemClick(feature, "vote_join_request")
                    }
                }
                else -> { // Standard feature request
                    binding.tvSolutionCount.text = "${solutions.size} solution(s)"
                    when {
                        solutions.isEmpty() -> {
                            binding.tvStatus.text = "No Solutions"
                            binding.btnAction.text = "Submit Solution"
                            binding.btnAction.setOnClickListener {
                                onItemClick(feature, "submit_solution")
                            }
                        }
                        else -> {
                            binding.tvStatus.text = "Solutions Submitted"
                            binding.btnAction.text = "View Solutions / Vote"
                            binding.btnAction.setOnClickListener {
                                onItemClick(feature, "view_solutions")
                            }
                        }
                    }
                }
            }
        }
    }
}

