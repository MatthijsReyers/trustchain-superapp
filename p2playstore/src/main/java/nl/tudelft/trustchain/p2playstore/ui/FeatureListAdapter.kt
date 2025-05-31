package nl.tudelft.trustchain.p2playstore.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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

            binding.tvFeatureTitle.text = feature.title
            binding.tvFeatureDescription.text = feature.description
            binding.tvReward.text = "${feature.reward} sats"
            binding.tvSolutionCount.text = "${solutions.size} solution(s)"

            // Set status and button text based on solutions
            when {
                solutions.isEmpty() -> {
                    binding.tvStatus.text = "Open for solutions"
                    binding.btnAction.text = "Submit Solution"
                    binding.btnAction.setOnClickListener {
                        onItemClick(feature, "submit_solution")
                    }
                }
                solutions.size == 1 -> {
                    binding.tvStatus.text = "Solution submitted - Voting"
                    binding.btnAction.text = "Vote"
                    binding.btnAction.setOnClickListener {
                        onItemClick(feature, "view_solutions")
                    }
                }
                else -> {
                    binding.tvStatus.text = "Multiple solutions - Voting"
                    binding.btnAction.text = "View Solutions"
                    binding.btnAction.setOnClickListener {
                        onItemClick(feature, "view_solutions")
                    }
                }
            }
        }
    }
}
