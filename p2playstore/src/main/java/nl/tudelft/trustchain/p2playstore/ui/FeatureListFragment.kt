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
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureVoteTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureVoteTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTransactionData
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureListBinding
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

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
                "view_solutions" -> navigateToViewSolutions(featureRequest) // Navigate to the latest solution for voting
                "vote_join_request" -> navigateToVoteForJoinRequest(featureRequest) // New action for join requests
            }
        }

        binding.recyclerViewFeatures.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewFeatures.adapter = adapter
    }



    // New function to navigate to the voting fragment for a join request
    private fun navigateToVoteForJoinRequest(featureRequest: FeatureRequestTD) {
        if (daoUniqueId.isEmpty()) {
            android.util.Log.e("FeatureListFragment", "daoUniqueId is empty. Cannot navigate to vote for join request.")
            Toast.makeText(context, "Error: Missing DAO information.", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.d("FeatureListFragment", "navigateToVoteForJoinRequest: Navigating to vote on join request feature ${featureRequest.featureId} in DAO ${daoUniqueId}")

        val bundle = Bundle().apply {
            // Pass blockId of the DAO, featureId of the join request, daoUniqueId, and indicate it IS a join request
            putString("blockId", daoBlockId) // Still need DAO blockId for context in voting fragment
            putString("featureId", featureRequest.featureId)
            putString("daoUniqueId", daoUniqueId)
            putBoolean("isJoinRequest", true)
        }
        findNavController().navigate(R.id.action_featureListFragment_to_featureVotingFragment, bundle)
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
                    android.util.Log.e(
                        "FeatureListFragment",
                        "daoUniqueId is empty. Cannot load feature requests."
                    )
                    binding.tvNoFeatures.text = "Error loading DAO features."
                    binding.tvNoFeatures.visibility = View.VISIBLE
                    binding.recyclerViewFeatures.visibility = View.GONE
                    return@launch
                }

                // 1. Fetch all feature request blocks for this DAO (includes join requests now)
                val featureRequestBlocks = withContext(Dispatchers.IO) {
                    p2playStore.getFeatureRequestBlocksForDao(daoUniqueId)
                }
                android.util.Log.d(
                    "FeatureListFragment",
                    "loadFeatureRequests: Found ${featureRequestBlocks.size} feature request blocks for DAO $daoUniqueId"
                )

                // Parse feature request blocks, pairing with their blocks for timestamp
                val featureRequestsWithBlocks = featureRequestBlocks.mapNotNull { reqBlock ->
                    try {
                        FeatureRequestTransactionData(reqBlock.transaction).getData() to reqBlock // Pair data with its block
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "FeatureListFragment",
                            "Failed to parse FeatureRequest block: ${e.message}"
                        )
                        null
                    }
                }

                // 2. Get all solution blocks for this DAO (only for standard features)
                val allSolutionBlocks = withContext(Dispatchers.IO) {
                    p2playStore.getSolutionBlocksForDaoAndFeature(daoUniqueId)
                }
                android.util.Log.d(
                    "FeatureListFragment",
                    "loadFeatureRequests: Found ${allSolutionBlocks.size} total solution blocks for DAO $daoUniqueId (standard features)."
                )

                // Map solution blocks to pairs of (SolutionData, Block) and group by featureId
                val solutionsGroupedByFeatureId = allSolutionBlocks.mapNotNull { block ->
                    try {
                        val solutionData =
                            FeatureSolutionTransactionData(block.transaction).getData()
                        solutionData to block // Pair of SolutionData and its Block
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "FeatureListFragment",
                            "Failed to parse FeatureSolution block: ${e.message}"
                        )
                        null
                    }
                }.groupBy { it.first.featureId }

                // 3. Get all vote blocks for this DAO (for both standard feature solutions and join requests)
                // We need the blocks to get the timestamps. Let's fetch all vote blocks and parse.
                val allFeatureVoteBlocks = withContext(Dispatchers.IO) {
                    getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.FEATURE_VOTE_BLOCK)
                }
                android.util.Log.d(
                    "FeatureListFragment",
                    "loadFeatureRequests: Found ${allFeatureVoteBlocks.size} total FeatureVote blocks."
                )

                // Map vote blocks to pairs of (VoteData, Block) and group by featureId and daoId
                val votesGroupedByFeatureIdAndDao = allFeatureVoteBlocks.mapNotNull { block ->
                    try {
                        val voteData = FeatureVoteTransactionData(block.transaction).getData()
                        if (voteData.daoId == daoUniqueId) { // Filter for votes in this DAO
                            voteData to block // Pair of VoteData and its Block
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "FeatureListFragment",
                            "Failed to parse FeatureVote block: ${e.message}"
                        )
                        null
                    }
                }.groupBy { it.first.featureId } // Group by featureId


                // Combine feature requests with their associated data and calculate latest timestamp
                val featuresWithAssociatedDataAndTimestamp =
                    featureRequestsWithBlocks.mapNotNull { (featureRequest, reqBlock) ->
                        val latestAssociatedBlockTimestamp: Long? =
                            when (featureRequest.requestType) {
                                P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE -> {
                                    // Find the latest timestamp among vote blocks for this feature request in this DAO
                                    // The vote blocks for join requests use the featureId as the solutionId field.
                                    votesGroupedByFeatureIdAndDao[featureRequest.featureId]?.maxByOrNull { it.second.timestamp.time }?.second?.timestamp?.time
                                }

                                else -> {
                                    // Find the latest timestamp among solution blocks for this feature request in this DAO
                                    solutionsGroupedByFeatureId[featureRequest.featureId]?.maxByOrNull { it.second.timestamp.time }?.second?.timestamp?.time
                                }
                            }

                        // Use the latest timestamp found, or the request block's timestamp if no associated blocks
                        val sortTimestamp =
                            latestAssociatedBlockTimestamp ?: reqBlock.timestamp.time

                        // Get the lists of solutions and votes
                        val solutionsForRequest =
                            solutionsGroupedByFeatureId[featureRequest.featureId]?.map { it.first }
                                ?: emptyList()
                        val votesForRequest =
                            votesGroupedByFeatureIdAndDao[featureRequest.featureId]?.map { it.first }
                                ?: emptyList() // Use the grouped votes

                        FeatureRequestWithSolutionsAndTimestamp(
                            featureRequest,
                            solutionsForRequest,
                            sortTimestamp,
                            votesForRequest
                        )
                    }

                val sortedFeatures =
                    featuresWithAssociatedDataAndTimestamp.sortedByDescending { it.latestTimestamp }

                // Map back to the original adapter data format, adjusting 'solutions' to contain votes for join requests
                val featuresForAdapter = sortedFeatures.map {
                    if (it.featureRequest.requestType == P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE) {
                        // For join requests, pass the votes list explicitly for the count
                        FeatureRequestWithSolutions(it.featureRequest, emptyList(), it.votes) // Pass empty solutions list, pass votes list
                    } else {
                        FeatureRequestWithSolutions(it.featureRequest, it.solutions, emptyList()) // Pass solutions list, pass empty votes list
                    }
                }



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
                    // Pass blockId of the DAO, solutionId, daoUniqueId, and indicate it's NOT a join request
                    putString("blockId", daoBlockId) // Still need DAO blockId for context in voting fragment
                    putString("solutionId", latestSolution.solutionId)
                    putString("daoUniqueId", daoUniqueId)
                    putBoolean("isJoinRequest", false)
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
    val solutions: List<FeatureSolutionTD>,
    val votes: List<FeatureVoteTD>
)

// Helper data class for sorting
data class FeatureRequestWithSolutionsAndTimestamp(
    val featureRequest: FeatureRequestTD,
    val solutions: List<FeatureSolutionTD>,
    val latestTimestamp: Long,
    val votes: List<FeatureVoteTD>
)
