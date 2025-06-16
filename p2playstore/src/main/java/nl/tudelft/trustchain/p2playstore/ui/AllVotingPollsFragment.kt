package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTD
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

import nl.tudelft.trustchain.p2playstore.transactionData.*

import nl.tudelft.trustchain.p2playstore.databinding.FragmentAllVotingPollsBinding

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
            try {
                if (daoUniqueId.isEmpty()) {
                    android.util.Log.e("AllVotingPollsFragment", "daoUniqueId is empty. Cannot load voting polls.")
                    showError("Error loading voting polls: Missing DAO ID.")
                    return@launch
                }

                // Get all relevant blocks for this DAO
                val allRelevantBlocks = withContext(Dispatchers.IO) {
                    val joinRequestBlocks = getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.JOIN_REQUEST_BLOCK)
                        .filter { block ->
                            try {
                                JoinRequestTransactionData(block.transaction).getData().DAO_ID == daoUniqueId
                            } catch (e: Exception) { false }
                        }

                    val featureSolutionBlocks = getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.PROPOSE_UPDATE_BLOCK)
                        .filter { block ->
                            try {
                                val data = ProposeUpdateTransactionData(block.transaction).getData()
                                data.DAO_ID == daoUniqueId && data.FEATURE_REQUEST_ID != null // Filter for feature solutions
                            } catch (e: Exception) { false }
                        }
                    joinRequestBlocks + featureSolutionBlocks
                }

                // Group blocks by their unique proposal ID and take the latest block for each ID
                val latestBlocksByProposal = allRelevantBlocks
                    .groupBy { block ->
                        try {
                            // Determine the unique proposal ID based on block type
                            when (block.type) {
                                P2pStoreCommunity.JOIN_REQUEST_BLOCK -> JoinRequestTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
                                P2pStoreCommunity.PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
                                else -> null // Should not happen due to initial filtering
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AllVotingPollsFragment", "Error getting proposal ID from block ${block.blockId}: ${e.message}")
                            null
                        }
                    }
                    .filterKeys { it != null } // Remove any blocks that failed to yield a proposal ID
                    .mapValues { (_, blocks) -> // For each group of blocks with the same proposal ID
                        blocks.maxByOrNull { it.insertTime!! } // Find the latest block by insert time
                    }
                    .values // Get the list of the latest blocks

                // Create voting polls for each unique, latest proposal block
                val votingPolls = latestBlocksByProposal.mapNotNull { block ->
                    if (block == null) return@mapNotNull null // Should not happen if filtering worked
                    try {
                        when (block.type) {
                            P2pStoreCommunity.JOIN_REQUEST_BLOCK -> {
                                val data = JoinRequestTransactionData(block.transaction).getData()
                                p2playStore.getVotingPoll(daoUniqueId, data.SW_UNIQUE_PROPOSAL_ID)
                            }
                            P2pStoreCommunity.PROPOSE_UPDATE_BLOCK -> {
                                val data = ProposeUpdateTransactionData(block.transaction).getData()
                                p2playStore.getVotingPoll(daoUniqueId, data.SW_UNIQUE_PROPOSAL_ID)
                            }
                            else -> null // Should not happen
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AllVotingPollsFragment", "Error creating voting poll from latest block ${block.blockId}: ${e.message}")
                        null
                    }
                }

                if (votingPolls.isEmpty()) {
                    binding.recyclerViewPolls.visibility = View.GONE
                    binding.tvNoPolls.text = "No voting polls available.\nPolls will appear here when updates are submitted."
                    binding.tvNoPolls.visibility = View.VISIBLE
                } else {
                    binding.recyclerViewPolls.visibility = View.VISIBLE
                    binding.tvNoPolls.visibility = View.GONE
                    pollsAdapter.updatePolls(votingPolls)
                }

            } catch (e: Exception) {
                android.util.Log.e("AllVotingPollsFragment", "Error loading polls: ${e.message}")
                showError("Error loading voting polls: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        binding.recyclerViewPolls.visibility = View.GONE
        binding.tvNoPolls.visibility = View.VISIBLE
        binding.tvNoPolls.text = message
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

