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
import nl.tudelft.trustchain.p2playstore.transactionData.FeatureRequestData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateData
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


                // Get all feature requests for this DAO
                val featureRequests = withContext(Dispatchers.IO) {
                    p2playStore.getFeatureRequestsForDao(daoUniqueId)
                }
                android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${featureRequests.size} feature requests for DAO $daoUniqueId")

                // Get all feature solutions for this DAO
                val featureSolutions = withContext(Dispatchers.IO) {
                    p2playStore.getFeatureSolutionsForDao(daoUniqueId)
                }
                android.util.Log.d("FeatureListFragment", "loadFeatureRequests: Found ${featureSolutions.size} feature solutions for DAO $daoUniqueId")

                // Group solutions by feature request ID
                val solutionsGroupedByFeatureId = featureSolutions.groupBy { it.FEATURE_REQUEST_ID }

                // Combine feature requests with their solutions
                val featuresWithSolutions = featureRequests.map { featureRequest ->
                    val solutionsForRequest = solutionsGroupedByFeatureId[featureRequest.FEATURE_REQUEST_ID] ?: emptyList()
                    FeatureRequestWithSolutions(featureRequest, solutionsForRequest)
                }

                adapter.updateFeatures(featuresWithSolutions)

                if (featuresWithSolutions.isEmpty()) {
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

    private fun navigateToSubmitSolution(featureRequest: FeatureRequestData) {
        val bundle = Bundle().apply {
            putString("featureId", featureRequest.FEATURE_REQUEST_ID)
            putString("daoUniqueId", featureRequest.DAO_ID)
            putString("featureTitle", featureRequest.FEATURE_TITLE)
            putString("featureDescription", featureRequest.FEATURE_DESCRIPTION)
        }
        findNavController().navigate(R.id.action_featureListFragment_to_featureSolutionFragment, bundle)
    }

    private fun navigateToViewSolutions(featureRequest: FeatureRequestData) {
        lifecycleScope.launch {
            if (daoUniqueId.isEmpty()) {
                android.util.Log.e("FeatureListFragment", "daoUniqueId is empty. Cannot navigate to view solutions.")
                Toast.makeText(context, "Error: Missing DAO information.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Get solutions for this specific feature
            val solutionsForFeature = withContext(Dispatchers.IO) {
                p2playStore.getFeatureSolutionsForDao(daoUniqueId)
                    .filter { it.FEATURE_REQUEST_ID == featureRequest.FEATURE_REQUEST_ID }
            }

            if (solutionsForFeature.isNotEmpty()) {
                val latestSolution = solutionsForFeature.maxByOrNull { it.SW_UNIQUE_PROPOSAL_ID }
                android.util.Log.d("FeatureListFragment", "navigateToViewSolutions: Navigating to vote on latest solution ${latestSolution?.SW_UNIQUE_PROPOSAL_ID} in DAO ${daoUniqueId}")

                val bundle = Bundle().apply {
                    putString("blockId", daoBlockId)
                    putString("solutionId", latestSolution?.SW_UNIQUE_PROPOSAL_ID)
                    putString("daoUniqueId", daoUniqueId)
                }
                findNavController().navigate(R.id.action_featureListFragment_to_featureVotingFragment, bundle)
            } else {
                android.util.Log.w("FeatureListFragment", "navigateToViewSolutions called but no solutions found for feature ${featureRequest.DAO_ID} in DAO ${daoUniqueId}. Cannot navigate to voting.")
                Toast.makeText(context, "No solutions found for this feature yet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("FeatureListFragment", "onResume: Reloading feature requests.")
        loadFeatureRequests()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class FeatureRequestWithSolutions(
    val featureRequest: FeatureRequestData,
    val solutions: List<ProposeUpdateData>
)
