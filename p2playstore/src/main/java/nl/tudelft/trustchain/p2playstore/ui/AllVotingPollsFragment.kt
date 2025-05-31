package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import nl.tudelft.ipv8.util.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTD
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPollHelper
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAllVotingPollsBinding
import android.util.Log

class AllVotingPollsFragment : BaseFragment() {
    private var _binding: FragmentAllVotingPollsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var pollsAdapter: VotingPollsAdapter
    private lateinit var daoBlock: TrustChainBlock
    private lateinit var daoData: SWJoinBlockTD
    private lateinit var daoUniqueId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllVotingPollsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        TODO: make choice between these
        val blockId = arguments?.getString("blockId")
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""

        if (blockId != null && daoUniqueId.isNotEmpty()) {
            loadDaoBlock(blockId)
            setupRecyclerView()
        } else {
            android.util.Log.e("AllVotingPollsFragment", "No DAO block ID or DAO Unique ID provided")
            binding.recyclerViewPolls.visibility = View.GONE
            binding.tvNoPolls.text = "Error: Missing DAO information."
            binding.tvNoPolls.visibility = View.VISIBLE
            findNavController().navigateUp()
        }
    }

    private fun loadDaoBlock(blockId: String) {
        lifecycleScope.launch {
            try {
                val daoBlock = withContext(Dispatchers.IO) {
                    p2playStore.getDaoBlock(blockId)
                }

                if (daoBlock != null) {
                    this@AllVotingPollsFragment.daoBlock = daoBlock
                    daoData = SWJoinBlockTransactionData(daoBlock.transaction).getData()

                    loadVotingPolls()

                } else {
                    android.util.Log.e("AllVotingPollsFragment", "DAO block not found for ID: $blockId")
                    binding.recyclerViewPolls.visibility = View.GONE
                    binding.tvNoPolls.text = "Error: DAO information not found."
                    binding.tvNoPolls.visibility = View.VISIBLE
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                android.util.Log.e("AllVotingPollsFragment", "Error loading DAO block: ${e.message}")
                binding.recyclerViewPolls.visibility = View.GONE
                binding.tvNoPolls.text = "Error loading DAO information."
                binding.tvNoPolls.visibility = View.VISIBLE
                findNavController().navigateUp()
            }
        }
    }


    private fun setupRecyclerView() {
        pollsAdapter = VotingPollsAdapter { poll ->
            // Navigate to voting fragment for this specific poll (solution)
            val bundle =
                Bundle().apply {
                    putString("blockId", daoBlock.blockId)
                    putString("daoUniqueId", daoUniqueId)
                    putString("solutionId", poll.id)
                }
            findNavController()
                .navigate(R.id.action_allVotingPollsFragment_to_featureVotingFragment, bundle)
        }

        binding.recyclerViewPolls.apply {
            adapter = pollsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun loadVotingPolls() {
        lifecycleScope.launch {
            val maxRetries = 5 // Increased retries
            val retryDelayMillis = 2000L // 2 second delay

            for (retry in 0..maxRetries) {
                try {
                    // Use the retrieved DAO Unique ID
                    if (daoUniqueId.isEmpty()) {
                        android.util.Log.e("AllVotingPollsFragment", "daoUniqueId is empty in loadVotingPolls. Cannot load voting polls.")
                        binding.recyclerViewPolls.visibility = View.GONE
                        binding.tvNoPolls.text = "Error loading voting polls: Missing DAO ID."
                        binding.tvNoPolls.visibility = View.VISIBLE
                        return@launch // Exit retry loop
                    }


                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }
                    android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): Found ${featureRequests.size} feature requests for DAO $daoUniqueId")


                    // Get all solution blocks for this DAO
                    val allSolutionBlocks = withContext(Dispatchers.IO) {
                        p2playStore.getSolutionBlocksForDaoAndFeature(daoUniqueId)
                    }
                    android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): Found ${allSolutionBlocks.size} total solution blocks for DAO $daoUniqueId.")


                    // Map blocks to pairs of (Block, SolutionData) and filter for votable ones
                    val allVotableSolutionsWithBlocks = allSolutionBlocks.mapNotNull { block ->
                        try {
                            val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                            block to solutionData
                        } catch (e: Exception) {
                            android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureSolution block: ${e.message}")
                            null // Exclude blocks that cannot be parsed
                        }
                    }
                        // Filter for solutions linked to OPEN feature requests
                        .filter { (block, solution) ->
                            featureRequests.any { it.featureId == solution.featureId && it.status == "OPEN" }
                        }
                    android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls: Found ${allVotableSolutionsWithBlocks.size} votable solutions.")


                    val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                    val votingThreshold = daoData.SW_VOTING_THRESHOLD
                    val myPublicKey = p2playStore.myPeer.publicKey.keyToBin().toHex()

                    // Create VotingPoll objects for each votable solution block
                    val votingPolls = allVotableSolutionsWithBlocks.mapNotNull { (block, solution) ->
                        // Find the corresponding feature request again (should exist based on filter)
                        val correspondingRequest = featureRequests.find { it.featureId == solution.featureId }

                        if (correspondingRequest != null) {
                            val votesForSolution = withContext(Dispatchers.IO) {
                                // Fetch votes specifically for this solution within this DAO
                                p2playStore.getVotesForSolution(daoUniqueId, solution.solutionId)
                            }
                            val hasUserVoted = votesForSolution.any { it.voterPublicKey == myPublicKey }
                            val userVote = votesForSolution.find { it.voterPublicKey == myPublicKey }?.isYes

                            // Assume voting is active if the feature request status is "OPEN"
                            // This is already filtered, so it should be true for these polls
                            val isVotingActive = correspondingRequest.status == "OPEN"

                            android.util.Log.d("AllVotingPollsFragment", "  loadVotingPolls: Creating poll for solution ${solution.solutionId} (Feature ${solution.featureId}) in DAO ${daoUniqueId}. Active: $isVotingActive, UserVoted: $hasUserVoted")

                            VotingPollHelper.createVotingPoll(
                                correspondingRequest, // Pass the actual request
                                solution,
                                votesForSolution,
                                totalMembers,
                                votingThreshold,
                                hasUserVoted = hasUserVoted,
                                userVote = userVote
                            )
                        } else {
                            null
                        }
                    }

                    if (votingPolls.isEmpty()) {
                        android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): No voting polls to display for DAO $daoUniqueId.")
                        // If no polls found, wait and retry
                        if (retry < maxRetries) {
                            android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls: Retrying in ${retryDelayMillis}ms...")
                        } else {
                            binding.recyclerViewPolls.visibility = View.GONE
                            binding.tvNoPolls.text = "No voting polls available.\nPolls will appear here when solutions are submitted for features marked as OPEN."
                            binding.tvNoPolls.visibility = View.VISIBLE
                        }

                    } else {
                        // Found polls, update UI and break out of retry loop
                        binding.recyclerViewPolls.visibility = View.VISIBLE
                        binding.tvNoPolls.visibility = View.GONE
                        pollsAdapter.updatePolls(votingPolls)
                        android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls: Displayed ${votingPolls.size} voting polls for DAO $daoUniqueId.")
                        return@launch // Exit retry loop
                    }

                } catch (e: Exception) {
                    android.util.Log.e("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): Error loading polls: ${e.message}")
                    if (retry < maxRetries) {
                        android.util.Log.e("AllVotingPollsFragment", "loadVotingPolls: Retrying in ${retryDelayMillis}ms due to error...")
                    } else {
                        // exhausted retries, show error UI
                        binding.recyclerViewPolls.visibility = View.GONE
                        binding.tvNoPolls.visibility = View.VISIBLE
                        binding.tvNoPolls.text = "Error loading voting polls: ${e.message}"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

