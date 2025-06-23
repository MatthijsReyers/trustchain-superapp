package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.View
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.FeatureRequestPreviewBinding
import nl.tudelft.trustchain.p2playstore.models.FeatureRequest

class FeatureRequestPreviewHolder(
    private val binding: FeatureRequestPreviewBinding,
    private val destBtn: Int = R.id.action_featureListFragment_to_featureSolutionFragment,
    // private val dest: Int = R.id.action_featureListFragment_to_featureSolutionFragment,
) : RecyclerView.ViewHolder(binding.root) {

    fun hide() {
        binding.root.visibility = View.GONE
    }

    fun bind(request: FeatureRequest) {
        binding.root.visibility = View.VISIBLE
        binding.title.text = request.title
        binding.description.text = request.description
        binding.reward.text = "Reward: ${request.reward} sats"

        val solutions = request.getSolutions()
        if (solutions.size == 1) {
            binding.solutionCount.text = "1 solution"
        } else {
            binding.solutionCount.text = "${solutions.size} solutions"
        }

        // binding.card.setOnClickListener {
        //     itemView.findNavController()
        //         .navigate(
        //             dest,
        //             Bundle().apply {
        //                 putString("daoId", request.doaId)
        //                 putString("featureRequestId", request.featureRequestId)
        //             }
        //         )
        // }

        val open = solutions.any { s -> !s.isPending } || solutions.isEmpty()
        binding.submitBtn.isEnabled = open
        binding.submitBtn.alpha = if (open) { 1.0f } else { 0.3f }
        binding.submitBtn.setOnClickListener {
            if (open) {
                itemView.findNavController()
                    .navigate(
                        destBtn,
                        Bundle().apply {
                            putString("daoId", request.doaId)
                            putString("featureRequestId", request.featureRequestId)
                        }
                    )
            }
        }

        binding.status.text = if (open) { "Open for solutions" } else { "Solution found" }
    }
}
