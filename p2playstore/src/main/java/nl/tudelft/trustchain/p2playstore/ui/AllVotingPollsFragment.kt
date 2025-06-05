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
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureVoteTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTransactionData

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
                    // Determine if it's a join request based on the poll data (e.g., title or structure)
                    // A simple check could be if the title contains "Join Request" or if question implies it.
                    // A more robust way is to pass the type information in the VotingPoll data class itself.
                    // For now, let's assume the 'id' can distinguish them if needed later,
                    // but the FeatureVotingFragment uses isJoinRequest flag, which we need to set.
                    // Let's check the question or title as a temporary workaround.
                    val isJoinRequestPoll = poll.title.contains("Join Request") || poll.question.contains("allowed to join the DAO")

                    putBoolean("isJoinRequest", isJoinRequestPoll)
                    if (isJoinRequestPoll) {
                        putString("featureId", poll.id) // Pass featureId for join requests
                    } else {
                        putString("solutionId", poll.id) // Pass solutionId for standard features
                    }
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

                    // Get all vote blocks for this DAO (for both standard feature solutions and join requests)
                    val allVoteBlocks = withContext(Dispatchers.IO) {
                        getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.FEATURE_VOTE_BLOCK)
                            .filter {
                                try {
                                    // Ensure the vote block is for this DAO
                                    FeatureVoteTransactionData(it.transaction).getData().daoId == daoUniqueId
                                } catch (e: Exception) {
                                    android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureVote block for DAO filter: ${e.message}")
                                    false
                                }
                            }
                    }
                    android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): Found ${allVoteBlocks.size} total vote blocks for DAO $daoUniqueId.")


                    val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                    val votingThreshold = daoData.SW_VOTING_THRESHOLD
                    val myPublicKey = p2playStore.myPeer.publicKey.keyToBin().toHex()

                    // Create a list to hold all VotingPolls (for solutions and join requests)
                    val votingPolls = mutableListOf<VotingPoll>()


                    // --- 1. Create polls for standard feature solutions that are linked to OPEN requests ---
                    val standardFeatureRequests = featureRequests.filter { it.requestType != P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE }
                    val votableSolutionBlocks = allSolutionBlocks
                        .filter { block ->
                            try {
                                val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                                // Only include solutions linked to OPEN standard feature requests
                                standardFeatureRequests.any { it.featureId == solutionData.featureId && it.status == "OPEN" }
                            } catch (e: Exception) {
                                android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureSolution block for votable filter: ${e.message}")
                                false
                            }
                        }

                    votableSolutionBlocks.forEach { block ->
                        try {
                            val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                            val correspondingRequest = standardFeatureRequests.find { it.featureId == solutionData.featureId }

                            if (correspondingRequest != null) {
                                // Filter votes for this specific solution
                                val votesForSolution = allVoteBlocks.filter { voteBlock ->
                                    try {
                                        FeatureVoteTransactionData(voteBlock.transaction).getData().solutionId == solutionData.solutionId
                                    } catch (e: Exception) {
                                        android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureVote block for solution filter: ${e.message}")
                                        false
                                    }
                                } .map { FeatureVoteTransactionData(it.transaction).getData() } // Map blocks to data

                                val hasUserVoted = votesForSolution.any { it.voterPublicKey == myPublicKey }
                                val userVote = votesForSolution.find { it.voterPublicKey == myPublicKey }?.isYes

                                val isVotingActive = correspondingRequest.status == "OPEN"


                                votingPolls.add(VotingPollHelper.createVotingPoll(
                                    correspondingRequest,
                                    solutionData,
                                    votesForSolution,
                                    totalMembers,
                                    votingThreshold,
                                    hasUserVoted = hasUserVoted,
                                    userVote = userVote
                                ))
                                android.util.Log.d("AllVotingPollsFragment", "  loadVotingPolls: Created poll for standard solution ${solutionData.solutionId} (Feature ${solutionData.featureId}). Active: $isVotingActive, UserVoted: $hasUserVoted")

                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureSolution block while creating poll: ${e.message}")
                        }
                    }

                    // --- 2. Create polls for pending Join Request features ---
                    val joinRequestFeatures = featureRequests.filter { it.requestType == P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE && it.status == "OPEN" }

                    joinRequestFeatures.forEach { joinRequest ->
                        // Check if there are any votes for this specific join request feature
                        // For join requests, the vote's solutionId is the featureId
                        val votesForJoinRequest = allVoteBlocks.filter { voteBlock ->
                            try {
                                val voteData = FeatureVoteTransactionData(voteBlock.transaction).getData()
                                voteData.featureId == joinRequest.featureId && voteData.solutionId == joinRequest.featureId
                            } catch (e: Exception) {
                                android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureVote block for join request filter: ${e.message}")
                                false
                            }
                        } .map { FeatureVoteTransactionData(it.transaction).getData() } // Map blocks to data


                        // Only create a poll if voting is still needed (e.g., not enough votes to meet threshold or threshold check needs to be added)
                        // For simplicity now, let's just display all open join requests as polls
                        // A more complex check might involve checking current vote counts against the threshold.
                        // Let's create the poll and rely on the VotingPoll logic to show status.

                        val hasUserVoted = votesForJoinRequest.any { it.voterPublicKey == myPublicKey }
                        val userVote = votesForJoinRequest.find { it.voterPublicKey == myPublicKey }?.isYes
                        val isVotingActive = joinRequest.status == "OPEN" // Assume active if status is OPEN

                        val yesVotes = votesForJoinRequest.count { it.isYes }
                        val noVotes = votesForJoinRequest.count { !it.isYes }


                        val votingPoll = VotingPoll(
                            id = joinRequest.featureId, // Use featureId as the poll ID
                            title = "Vote on Join Request",
                            question = "Should ${joinRequest.requesterPublicKey.take(8)}... be allowed to join the DAO? Entrance Fee: ${joinRequest.reward} sats.",
                            yesVotes = yesVotes,
                            noVotes = noVotes,
                            totalMembers = totalMembers,
                            votingThreshold = votingThreshold, // Use DAO threshold
                            isActive = isVotingActive,
                            hasUserVoted = hasUserVoted,
                            userVote = userVote
                        )
                        votingPolls.add(votingPoll)
                        android.util.Log.d("AllVotingPollsFragment", "  loadVotingPolls: Created poll for join request feature ${joinRequest.featureId}. Active: $isVotingActive, UserVoted: $hasUserVoted")

                    }


                    // Sort all combined polls by timestamp (latest associated block or request block)
                    // This sorting logic is complex as it requires finding the block for each item.
                    // For simplicity now, let's just display them without strict timestamp sorting relative to each other.
                    // The adapter will display them in the order they are added to the list.
                    // A more robust solution would involve adding timestamp to VotingPoll data class or sorting the source blocks first.
                    // Let's sort the feature requests and solutions blocks first and then create polls.
                    val featureRequestBlocks = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestBlocksForDao(daoUniqueId)
                    }
                    android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): Found ${featureRequestBlocks.size} feature request blocks for DAO $daoUniqueId")

                    val featureRequestsWithBlocks = featureRequestBlocks.mapNotNull { reqBlock ->
                        try {
                            FeatureRequestTransactionData(reqBlock.transaction).getData() to reqBlock // Pair data with its block
                        } catch (e: Exception) {
                            android.util.Log.e("AllVotingPollsFragment", "Failed to parse FeatureRequest block: ${e.message}")
                            null
                        }
                    }
                    val allVotableItemsWithBlocks = votableSolutionBlocks.map { it to false } + // Pair block with a flag indicating it's a solution block
                        featureRequestsWithBlocks.filter { it.first.requestType == P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE && it.first.status == "OPEN" } .map { it.second to true } // Pair join request block with a flag


                    val sortedVotableBlocks = allVotableItemsWithBlocks.sortedByDescending { it.first.timestamp.time }

                    val sortedVotingPolls = sortedVotableBlocks.mapNotNull { (block, isJoinRequestFlag) ->
                        if (isJoinRequestFlag) {
                            // Find the corresponding join request data
                            val joinRequest = featureRequests.find {
                                try {
                                    FeatureRequestTransactionData(block.transaction).getData().featureId == it.featureId
                                } catch (e: Exception) { false }
                            }

                            joinRequest?.let { req ->
                                val votesForJoinRequest = allVoteBlocks.filter { voteBlock ->
                                    try {
                                        val voteData = FeatureVoteTransactionData(voteBlock.transaction).getData()
                                        voteData.featureId == req.featureId && voteData.solutionId == req.featureId
                                    } catch (e: Exception) { false }
                                } .map { FeatureVoteTransactionData(it.transaction).getData() }

                                val hasUserVoted = votesForJoinRequest.any { it.voterPublicKey == myPublicKey }
                                val userVote = votesForJoinRequest.find { it.voterPublicKey == myPublicKey }?.isYes
                                val yesVotes = votesForJoinRequest.count { it.isYes }
                                val noVotes = votesForJoinRequest.count { !it.isYes }

                                VotingPoll(
                                    id = req.featureId,
                                    title = "Vote on Join Request",
                                    question = "Should ${req.requesterPublicKey.take(8)}... be allowed to join the DAO? Entrance Fee: ${req.reward} sats.",
                                    yesVotes = yesVotes,
                                    noVotes = noVotes,
                                    totalMembers = totalMembers,
                                    votingThreshold = votingThreshold,
                                    isActive = req.status == "OPEN",
                                    hasUserVoted = hasUserVoted,
                                    userVote = userVote
                                )
                            }
                        } else {
                            // Find the corresponding solution data
                            try {
                                val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                                val correspondingRequest = standardFeatureRequests.find { it.featureId == solutionData.featureId }

                                correspondingRequest?.let { req ->
                                    val votesForSolution = allVoteBlocks.filter { voteBlock ->
                                        try {
                                            FeatureVoteTransactionData(voteBlock.transaction).getData().solutionId == solutionData.solutionId
                                        } catch (e: Exception) { false }
                                    } .map { FeatureVoteTransactionData(it.transaction).getData() }

                                    val hasUserVoted = votesForSolution.any { it.voterPublicKey == myPublicKey }
                                    val userVote = votesForSolution.find { it.voterPublicKey == myPublicKey }?.isYes
                                    val yesVotes = votesForSolution.count { it.isYes }
                                    val noVotes = votesForSolution.count { !it.isYes }


                                    VotingPollHelper.createVotingPoll(
                                        req,
                                        solutionData,
                                        votesForSolution,
                                        totalMembers,
                                        votingThreshold,
                                        hasUserVoted = hasUserVoted,
                                        userVote = userVote
                                    )
                                }
                            } catch (e: Exception) { null }
                        }
                    }


                    if (sortedVotingPolls.isEmpty()) {
                        android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls (Attempt ${retry + 1}): No voting polls to display for DAO $daoUniqueId.")
                        // If no polls found, wait and retry
                        if (retry < maxRetries) {
                            android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls: Retrying in ${retryDelayMillis}ms...")
                        } else {
                            binding.recyclerViewPolls.visibility = View.GONE
                            binding.tvNoPolls.text = "No voting polls available.\nPolls will appear here when solutions are submitted for features marked as OPEN or when join requests are made."
                            binding.tvNoPolls.visibility = View.VISIBLE
                        }

                    } else {
                        // Found polls, update UI and break out of retry loop
                        binding.recyclerViewPolls.visibility = View.VISIBLE
                        binding.tvNoPolls.visibility = View.GONE
                        pollsAdapter.updatePolls(sortedVotingPolls)
                        android.util.Log.d("AllVotingPollsFragment", "loadVotingPolls: Displayed ${sortedVotingPolls.size} voting polls for DAO $daoUniqueId.")
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

