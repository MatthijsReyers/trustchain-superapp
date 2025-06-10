package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureListBinding

class FeatureListFragment : BaseFragment() {
    private var _binding: FragmentFeatureListBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var daoBlockId: String
    private lateinit var daoUniqueId: String
    private lateinit var adapter: FeatureListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatureListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        daoBlockId = arguments?.getString("blockId") ?: ""
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""
        setupRecyclerView()
        setupClickListeners()
        loadFeatureRequests()
    }

    private fun setupRecyclerView() {
        adapter = FeatureListAdapter { featureRequest, action ->
            when (action) {
                "submit_solution" -> navigateToSubmitSolution(featureRequest)
                "view_solutions" -> navigateToViewSolutions(featureRequest)
            }
        }

        binding.recyclerViewFeatures.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewFeatures.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnRequestFeature.setOnClickListener {
            val bundle = Bundle().apply {
                putString("blockId", daoBlockId)
                putString("daoUniqueId", daoUniqueId)
            }
            findNavController()
                .navigate(R.id.action_featureListFragment_to_featureRequestFragment, bundle)
        }
    }

    private fun loadFeatureRequests() {
        lifecycleScope.launch {
            try {
                if (daoUniqueId.isEmpty()) {
                    android.util.Log.e("FeatureListFragment", "daoUniqueId is empty. Cannot load feature requests.")
                    binding.tvNoFeatures.text = "Error loading DAO features."
                    binding.tvNoFeatures.visibility = View.VISIBLE
                    binding.recyclerViewFeatures.visibility = View.GONE
                    return@launch
                }
                // Get all feature request blocks for this DAO
                val featureRequestBlocks = withContext(Dispatchers.IO) {
                    p2playStore.getFeatureRequestBlocksForDao(daoUniqueId)
                }
                android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${featureRequestBlocks.size} feature request blocks for DAO $daoUniqueId")


                // Get all solution blocks for this DAO
                val allSolutionBlocks = withContext(Dispatchers.IO) {
                    p2playStore.getSolutionBlocksForDaoAndFeature(daoUniqueId)
                }
                android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${allSolutionBlocks.size} total solution blocks for DAO $daoUniqueId.")

                // Map solution blocks to pairs of (SolutionData, Block) and group by featureId
                val solutionsGroupedByFeatureId = allSolutionBlocks.mapNotNull { block ->
                    try {
                        val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                        solutionData to block // Pair of SolutionData and its Block
                    } catch (e: Exception) {
                        android.util.Log.e("FeatureListFragment", "Failed to parse FeatureSolution block: ${e.message}")
                        null
                    }
                }.groupBy { it.first.featureId }


                // Combine feature requests with their solutions and calculate latest timestamp
                val featuresWithSolutionsAndTimestamp = featureRequestBlocks.mapNotNull { reqBlock ->
                    try {
                        val featureRequest = FeatureRequestTransactionData(reqBlock.transaction).getData()
                        val solutionsForRequest = solutionsGroupedByFeatureId[featureRequest.featureId]?.map { it.first } ?: emptyList() // Get only the SolutionData
                        val latestSolutionBlockForRequest = solutionsGroupedByFeatureId[featureRequest.featureId]?.maxByOrNull { it.second.timestamp.time } // Get the block of the latest solution for this feature

                        // Determine the timestamp for sorting: latest of request block or latest solution block
                        val sortTimestamp = latestSolutionBlockForRequest?.second?.timestamp?.time ?: reqBlock.timestamp.time

                        FeatureRequestWithSolutionsAndTimestamp(featureRequest, solutionsForRequest, sortTimestamp)
                    } catch (e: Exception) {
                        android.util.Log.e("FeatureListFragment", "Failed to process feature request block ${reqBlock.blockId}: ${e.message}")
                        null
                    }
                }

                val sortedFeatures = featuresWithSolutionsAndTimestamp.sortedByDescending { it.latestTimestamp }

                // Map back to the original adapter data format
                val featuresForAdapter = sortedFeatures.map { FeatureRequestWithSolutions(it.featureRequest, it.solutions) }


                adapter.updateFeatures(featuresForAdapter)

                if (featuresForAdapter.isEmpty()) {
                    binding.tvNoFeatures.visibility = View.VISIBLE
                    binding.recyclerViewFeatures.visibility = View.GONE
                } else {
                    binding.tvNoFeatures.visibility = View.GONE
                    binding.recyclerViewFeatures.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                android.util.Log.e("FeatureListFragment", "Error loading features: ${e.message}")
                binding.tvNoFeatures.text = "Error loading DAO features: ${e.message}"
                binding.tvNoFeatures.visibility = View.VISIBLE
                binding.recyclerViewFeatures.visibility = View.GONE
            }
        }
    }



    private fun navigateToSubmitSolution(featureRequest: FeatureRequestTD) {
        val bundle = Bundle().apply {
            putString("featureId", featureRequest.featureId)
//            putString("daoId", featureRequest.daoId)
            putString("daoUniqueId", featureRequest.daoId)
            putString("featureTitle", featureRequest.title)
            putString("featureDescription", featureRequest.description)
        }
        findNavController().navigate(R.id.action_featureListFragment_to_featureSolutionFragment, bundle)
    }

    private fun navigateToViewSolutions(featureRequest: FeatureRequestTD) {
        lifecycleScope.launch {
            if (daoUniqueId.isEmpty()) {
                android.util.Log.e("FeatureListFragment", "daoUniqueId is empty. Cannot navigate to view solutions.")
                Toast.makeText(context, "Error: Missing DAO information.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Use the helper function to get solution blocks for this feature and sort by timestamp
            val solutionBlocksForRequest = withContext(Dispatchers.IO) {
                p2playStore.getSolutionBlocksForFeature(daoUniqueId, featureRequest.featureId)
            }
            android.util.Log.d("FeatureListFragment", "navigateToViewSolutions: Found ${solutionBlocksForRequest.size} solution blocks for feature ${featureRequest.featureId} in DAO ${daoUniqueId}")

            if (solutionBlocksForRequest.isNotEmpty()) {
                // The list is already sorted by timestamp descending by getSolutionBlocksForFeature
                val latestSolutionBlock = solutionBlocksForRequest.first()
                val latestSolution = latestSolutionBlock.second // Extract SolutionData from the pair
                android.util.Log.d("FeatureListFragment", "navigateToViewSolutions: Navigating to vote on latest solution ${latestSolution.solutionId} in DAO ${daoUniqueId}")

                val bundle = Bundle().apply {
                    putString("blockId", daoBlockId)
                    putString("solutionId", latestSolution.solutionId)
                    putString("daoUniqueId", daoUniqueId)
                }
                findNavController().navigate(R.id.action_featureListFragment_to_featureVotingFragment, bundle)
            } else {
                // This case should ideally not be reached if the adapter logic is correct,
                // but handle defensively.
                android.util.Log.w("FeatureListFragment", "navigateToViewSolutions called but no solutions found for feature ${featureRequest.featureId} in DAO ${daoUniqueId}. Cannot navigate to voting.")
                Toast.makeText(context, "No solutions found for this feature yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload feature requests whenever the fragment is resumed
        android.util.Log.d("FeatureListFragment", "onResume: Reloading feature requests.")
        loadFeatureRequests()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class FeatureRequestWithSolutions(
    val featureRequest: FeatureRequestTD,
    val solutions: List<FeatureSolutionTD>
)

// Helper data class for sorting
data class FeatureRequestWithSolutionsAndTimestamp(
    val featureRequest: FeatureRequestTD,
    val solutions: List<FeatureSolutionTD>,
    val latestTimestamp: Long
)
