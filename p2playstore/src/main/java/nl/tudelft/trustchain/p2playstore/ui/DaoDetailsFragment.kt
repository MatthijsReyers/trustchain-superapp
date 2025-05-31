package nl.tudelft.trustchain.p2playstore.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTD
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTransactionData
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPollHelper
import nl.tudelft.trustchain.p2playstore.databinding.FragmentDaoDeatilsBinding
import org.bitcoinj.core.Coin

class DaoDetailsFragment : BaseFragment() {
    private var _binding: FragmentDaoDeatilsBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var daoBlock: TrustChainBlock
    private lateinit var daoData: SWJoinBlockTD
    private var isUserMember = false

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDaoDeatilsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the block ID from arguments
        val blockId = arguments?.getString("blockId")
        if (blockId != null) {
            loadDaoDetails(blockId)
            setupClickListeners()
        } else {
            android.util.Log.e("DaoDetailsFragment", "No block ID provided")
            findNavController().navigateUp()
        }
    }

    private fun loadDaoDetails(blockId: String) {
        lifecycleScope.launch {
            try {
                // Get the block from the TrustChain store (using coroutine-safe call)
                val block =
                        withContext(Dispatchers.IO) {
                            val parts = blockId.split(".")
                            if (parts.size != 2) {
                                throw IllegalArgumentException("Invalid block ID format")
                            }
                            val publicKey = parts[0].hexToBytes()
                            val sequenceNumber = parts[1].toUInt()
                            getTrustChainCommunity().database.get(publicKey, sequenceNumber)
                        }

                if (block != null) {
                    daoBlock = block
                    // Parse DAO data once after loading the block
                    daoData = SWJoinBlockTransactionData(daoBlock.transaction).getData()

                    setupDaoDetailsUI()
                    checkMembership()
                    updateUIBasedOnMembership()
                    loadRecentVotingPoll()
                    loadLatestPendingFeatureRequest()
                    loadLatestApprovedUpdate()
                    // request in the new section
                } else {
                    android.util.Log.e("DaoDetailsFragment", "DAO block not found for ID: $blockId")
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                android.util.Log.e("DaoDetailsFragment", "Error loading DAO details: ${e.message}")
                findNavController().navigateUp()
            }
        }
    }

    private fun setupDaoDetailsUI() {
        // Set DAO basic info from the block data
        binding.daoName.text = daoBlock.transaction["name"]?.toString() ?: "Unknown DAO"
        binding.daoCategory.text = daoBlock.transaction["category"]?.toString() ?: "General"
        binding.daoMembersCount.text = daoData.SW_TRUSTCHAIN_PKS.size.toString()
        binding.daoDownloads.text = "0" // TODO: Implement download tracking
        binding.daoDeveloper.text = daoBlock.publicKey.toHex().take(8) + "..."
        binding.daoDescription.text =
                daoBlock.transaction["description"]?.toString() ?: "No description available"

        // Set entrance fee for join button
        val entranceFee = Coin.valueOf(daoData.SW_ENTRANCE_FEE).toFriendlyString()
        binding.btnJoinDao.text = "Join DAO - $entranceFee" // Keeping original text for now
    }

//    private fun setupVotingCard(daoData: SWJoinBlockTD) {
//        // initial state of the voting card views with placeholders or default visibility.
//        // The actual data loading and display will be handled in loadRecentVotingPoll.
//        val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
//        val votingThreshold = daoData.SW_VOTING_THRESHOLD
//
//        // Initialize UI elements to default/hidden state
//        binding.votingCard.visibility = View.GONE
//
//        binding.btnVote.visibility = View.GONE
//        binding.votingStatus.visibility = View.GONE
//
//        binding.updateTitle.text = "Loading..."
//        binding.yesPercentage.text = "0%"
//        binding.noPercentage.text = "0%"
//        binding.pendingPercentage.text = "100%"
//        binding.votesRequiredText.text = "Loading..."
//        binding.totalVotes.text = "Loading..."
//
//        // Reset progress bars
//        updateVotingProgressBars(0, 0, 100)
//    }
//    TODO: make functional
    private fun updateVotingProgressBars(yesPercent: Int, noPercent: Int, pendingPercent: Int) {
        binding.root.post {
            val containerWidth = binding.votingCard.width

            if (containerWidth > 0) {
                val horizontalPaddingAndMargins =
                        resources.getDimensionPixelSize(R.dimen.padding_normal) *
                                2 +
                        resources.getDimensionPixelSize(R.dimen.progress_bar_margin_horizontal) *
                                        2
                val availableWidth = containerWidth - horizontalPaddingAndMargins

                if (availableWidth > 0) {
                    val yesWidth = maxOf(1, (availableWidth * yesPercent / 100))
                    val noWidth = maxOf(1, (availableWidth * noPercent / 100))
                    val pendingWidth = maxOf(1, (availableWidth * pendingPercent / 100))

                    binding.yesProgressBar.layoutParams.width = yesWidth
                    binding.noProgressBar.layoutParams.width = noWidth
                    binding.pendingProgressBar.layoutParams.width = pendingWidth

                    binding.yesProgressBar.requestLayout()
                    binding.noProgressBar.requestLayout()
                    binding.pendingProgressBar.requestLayout()
                } else {
                    android.util.Log.w(
                            "DaoDetailsFragment",
                            "Insufficient width for progress bars."
                    )
                }
            }
        }
    }

    private fun checkMembership() {
        try {
            val daoData = SWJoinBlockTransactionData(daoBlock.transaction).getData()
            val myPublicKey = getP2pStoreCommunity().myPeer.publicKey.keyToBin().toHex()
            isUserMember = daoData.SW_TRUSTCHAIN_PKS.contains(myPublicKey)
        } catch (e: Exception) {
            android.util.Log.e("DaoDetailsFragment", "Error checking membership: ${e.message}")
            isUserMember = false
        }
    }

    private fun updateUIBasedOnMembership() {
        if (isUserMember) {
            binding.btnJoinDao.visibility = View.GONE
            binding.btnInstallUpdate.visibility = View.VISIBLE
            binding.btnFeatureRequest.isEnabled = true
            binding.btnFeatureRequest.alpha = 1.0f
            // Voting card clickability/alpha handled in loadRecentVotingPoll
        } else {
            binding.btnJoinDao.visibility = View.VISIBLE
            binding.btnInstallUpdate.visibility = View.GONE
            binding.btnFeatureRequest.isEnabled = false
            binding.btnFeatureRequest.alpha = 0.5f
            // Voting card clickability/alpha handled in loadRecentVotingPoll
        }
    }
    // This function finds the latest solution block that has met the voting threshold for its feature request.
    private fun loadLatestApprovedUpdate() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L // 1 second delay

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = daoData.SW_UNIQUE_ID

                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }
                    android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Found ${featureRequests.size} feature requests for DAO $daoUniqueId")
                    // Get all solution blocks for this DAO
                    val solutionBlocks = withContext(Dispatchers.IO) {
                        p2playStore.getSolutionBlocksForDaoAndFeature(daoUniqueId)
                    }
                    android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Found ${solutionBlocks.size} solution blocks for DAO $daoUniqueId.")

                    // Get all votes for this DAO
                    val votes = withContext(Dispatchers.IO) {
                        p2playStore.getVotesForSolution(daoUniqueId)
                    }
                    android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Found ${votes.size} votes for DAO $daoUniqueId.")


                    val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                    val votingThreshold = daoData.SW_VOTING_THRESHOLD

                    // Filter for approved solutions
                    val approvedSolutionDataWithBlocks = solutionBlocks.mapNotNull { block ->
                        try {
                            val solutionData = FeatureSolutionTransactionData(block.transaction).getData()
                            block to solutionData
                        } catch (e: Exception) {
                            android.util.Log.e("DaoDetailsFragment", "Failed to parse solution block in approved filter: ${e.message}")
                            null
                        }
                    }.filter { (block, solution) ->
                        // Find the corresponding feature request
                        val correspondingRequest = featureRequests.find { it.featureId == solution.featureId }

                        // Only consider solutions linked to a feature request
                        if (correspondingRequest != null) {
                            // Count votes for this specific solution
                            val votesForSolution = votes.filter { it.solutionId == solution.solutionId }
                            val yesVotes = votesForSolution.count { it.isYes }
                            val votesNeeded = votingThreshold
                            // An update is approved if YES votes meet the threshold
                            yesVotes >= votesNeeded
                        } else {
                            false
                        }
                    }
                    android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate: Found ${approvedSolutionDataWithBlocks.size} approved solutions with blocks for DAO $daoUniqueId.")
                    // Sort by block timestamp descending and take the first one
                    val latestApprovedSolutionWithBlock = approvedSolutionDataWithBlocks
                        .sortedByDescending { it.first.timestamp.time }
                        .firstOrNull()


                    if (latestApprovedSolutionWithBlock != null) {
                        val (block, solution) = latestApprovedSolutionWithBlock
                        android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate: Latest approved solution found: ${solution.solutionId} (Feature ${solution.featureId}), Block Timestamp: ${block.timestamp.time}")
                        // Update UI with latest approved version info
                        binding.daoVersion.text = "v${block.sequenceNumber}" // Use block sequence number as a simple version
                        // Set click listener for update button
                        binding.btnInstallUpdate.setOnClickListener {
                            downloadAndInstallUpdate(solution.apkMagnetLink)
                        }
                        binding.btnInstallUpdate.visibility = View.VISIBLE // Make sure update button is visible if user is member (handled by updateUIBasedOnMembership)
                        // Success, break out of retry loop
                        return@launch

                    } else {
                        android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): No approved solutions found for DAO $daoUniqueId.")
                        binding.daoVersion.text = "No updates"
                        binding.btnInstallUpdate.visibility = View.GONE // Hide update button if no approved updates
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DaoDetailsFragment", "loadLatestApprovedUpdate (Attempt ${retry + 1}): Error loading latest approved update: ${e.message}")
                    binding.daoVersion.text = "Error loading update info"
                    binding.btnInstallUpdate.visibility = View.GONE
                }
                if (retry < maxRetries) {
                    android.util.Log.d("DaoDetailsFragment", "loadLatestApprovedUpdate: Retrying in ${retryDelayMillis}ms...")
                }
            }
        }
    }

    private fun downloadAndInstallUpdate(magnetLink: String) {
        if (magnetLink.isNotEmpty()) {
            android.util.Log.d("DaoDetailsFragment", "Attempting to download/install from magnet link: $magnetLink")
            // TODO: Implement actual download/install logic.
            Toast.makeText(context, "Downloading update from $magnetLink (simulated)", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetLink))
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("DaoDetailsFragment", "Failed to open magnet link: ${e.message}")
                Toast.makeText(context, "Could not open magnet link. Please ensure you have a torrent client installed.", Toast.LENGTH_LONG).show()
            }
        } else {
            android.util.Log.w("DaoDetailsFragment", "No magnet link available for the latest approved update.")
            Toast.makeText(context, "No download link available for this update.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRecentVotingPoll() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = daoData.SW_UNIQUE_ID

                    // Get all feature requests for this DAO
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId)
                    }
                    android.util.Log.d("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): Found ${featureRequests.size} feature requests for DAO $daoUniqueId")


                    val latestVotableSolutionWithBlock = withContext(Dispatchers.IO) {
                        p2playStore.fetchLatestVotableSolutionBlock(daoUniqueId, featureRequests)
                    }

                    if (latestVotableSolutionWithBlock != null) {
                        val (latestVotableSolution, solutionBlock) = latestVotableSolutionWithBlock
                        android.util.Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Latest votable solution found: ${latestVotableSolution.solutionId} for feature ${latestVotableSolution.featureId} in DAO $daoUniqueId")
//                      TODO: might not be necessary but looks cool
                        binding.votingCard.visibility = View.VISIBLE
                        // Hide the "See All" button initially if we only show the latest,

                        val votes = withContext(Dispatchers.IO) {
                            // Fetch votes specifically for this solution within this DAO
                            p2playStore.getVotesForSolution(daoUniqueId, latestVotableSolution.solutionId)
                        }
                        android.util.Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Found ${votes.size} votes for solution ${latestVotableSolution.solutionId} in DAO ${daoUniqueId}")


                        // Find the corresponding feature request again to get its details
                        val correspondingRequest = featureRequests.find { it.featureId == latestVotableSolution.featureId }

                        if (correspondingRequest != null) {
                            val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                            val votingThreshold = daoData.SW_VOTING_THRESHOLD
                            val myPublicKey = p2playStore.myPeer.publicKey.keyToBin().toHex()
                            val hasUserVoted = votes.any { it.voterPublicKey == myPublicKey }
                            val userVote = votes.find { it.voterPublicKey == myPublicKey }?.isYes

                            // Create a VotingPoll object and update the UI
                            val votingPoll = VotingPollHelper.createVotingPoll(
                                correspondingRequest, // Pass the actual request
                                latestVotableSolution,
                                votes,
                                totalMembers,
                                votingThreshold,
                                hasUserVoted = hasUserVoted,
                                userVote = userVote
                            )
                            android.util.Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Created voting poll for UI. Active: ${votingPoll.isActive}, Voted: ${votingPoll.hasUserVoted}, Approved: ${votingPoll.isApproved}")


                            updateVotingCardUI(votingPoll)

                            // Set click listeners based on membership and voting status
                            if (isUserMember) { // Only members can interact with the voting card
                                binding.votingCard.setOnClickListener {
                                    navigateToVotingFragment(daoBlock.blockId, latestVotableSolution.solutionId)
                                }
                                // Show vote button only if active, member, and hasn't voted
                                if (votingPoll.isActive && !hasUserVoted) {
                                    binding.btnVote.visibility = View.VISIBLE
                                    binding.btnVote.setOnClickListener {
                                        navigateToVotingFragment(daoBlock.blockId, latestVotableSolution.solutionId)
                                    }
                                } else {
                                    binding.btnVote.visibility = View.GONE
                                }

                                binding.votingCard.isClickable = true
                                binding.votingCard.alpha = 1.0f

                            } else {
                                // If not a member, make card non-interactive and hide vote button
                                binding.votingCard.setOnClickListener(null)
                                binding.btnVote.setOnClickListener(null)
                                binding.btnVote.visibility = View.GONE
                                binding.votingCard.isClickable = false
                                binding.votingCard.alpha = 0.5f // Gray out
                            }
                            // Always enable "See All" votes if the card is visible
                            binding.btnSeeAllVotes.isEnabled = true
                            binding.btnSeeAllVotes.alpha = 1.0f

                            // Success, break out of retry loop
                            return@launch

                        } else {
                            android.util.Log.e("DaoDetailsFragment", "loadRecentVotingPoll: Found votable solution but corresponding open feature request not found. Solution ID: ${latestVotableSolution.solutionId} in DAO ${daoUniqueId}")
                            binding.votingCard.visibility = View.GONE
                            binding.btnVote.visibility = View.GONE
                            binding.btnSeeAllVotes.isEnabled = false
                            binding.btnSeeAllVotes.alpha = 0.5f
                        }

                    } else {
                        // No open feature request has a submitted solution to vote on
                        android.util.Log.d("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): No latest votable solution found for DAO $daoUniqueId.")
                        binding.votingCard.visibility = View.GONE
                        binding.btnVote.visibility = View.GONE
                        binding.btnSeeAllVotes.isEnabled = false // Disable "See All" if no polls
                        binding.btnSeeAllVotes.alpha = 0.5f

                        // If no votable solution is found, we might need to wait for sync.
                        if (retry < maxRetries) {
                            android.util.Log.d("DaoDetailsFragment", "loadRecentVotingPoll: Retrying in ${retryDelayMillis}ms...")
                        }
                    }

                } catch (e: Exception) {
                    android.util.Log.e("DaoDetailsFragment", "loadRecentVotingPoll (Attempt ${retry + 1}): Error loading recent voting poll: ${e.message}")
                    if (retry < maxRetries) {
                        android.util.Log.e("DaoDetailsFragment", "loadRecentVotingPoll: Retrying in ${retryDelayMillis}ms due to error...")
                    } else {
                        // exhausted retries, show error UI
                        binding.votingCard.visibility = View.GONE
                        binding.btnVote.visibility = View.GONE
                        binding.btnSeeAllVotes.isEnabled = false
                        binding.btnSeeAllVotes.alpha = 0.5f
                    }
                }
            }
        }
    }


    private fun loadLatestPendingFeatureRequest() {
        lifecycleScope.launch {
            val maxRetries = 3
            val retryDelayMillis = 1000L // 1 second delay

            for (retry in 0..maxRetries) {
                try {
                    val daoUniqueId = daoData.SW_UNIQUE_ID

                    val latestPendingRequest = withContext(Dispatchers.IO) {
                        p2playStore.fetchLatestPendingRequestBlock(daoUniqueId)
                    }


                    if (latestPendingRequest != null) {
                        android.util.Log.d("DaoDetailsFragment", "loadLatestPendingFeatureRequest: Latest pending request found: ${latestPendingRequest.featureId} for DAO $daoUniqueId")
                        binding.latestFeatureRequestPreviewCard.visibility = View.VISIBLE
                        binding.tvNoPendingFeatureRequests.visibility = View.GONE

                        binding.tvLatestFeatureTitle.text = latestPendingRequest.title
                        binding.tvLatestFeatureDescription.text = latestPendingRequest.description
                        binding.tvLatestFeatureReward.text = "Reward: ${latestPendingRequest.reward} sats"

                        // Still show 0 solutions count for clarity
                        binding.tvLatestFeatureSolutionCount.text = "0 solution(s)"


                        binding.latestFeatureRequestPreviewCard.setOnClickListener {
                            // Navigate to the submit solution fragment for this request
                            navigateToSubmitSolution(latestPendingRequest)
                        }
                        binding.latestFeatureRequestPreviewCard.isClickable = true
                        binding.latestFeatureRequestPreviewCard.alpha = 1.0f

                        // Success, break out of retry loop
                        return@launch

                    } else {
                        // No pending feature requests without solutions
                        android.util.Log.d("DaoDetailsFragment", "loadLatestPendingFeatureRequest (Attempt ${retry + 1}): No latest pending request found for DAO $daoUniqueId.")
                        binding.latestFeatureRequestPreviewCard.visibility = View.GONE
                        binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
                        binding.latestFeatureRequestPreviewCard.setOnClickListener(null)
                        binding.latestFeatureRequestPreviewCard.isClickable = false
                        binding.latestFeatureRequestPreviewCard.alpha = 0.5f

                    }

                } catch (e: Exception) {
                    android.util.Log.e("DaoDetailsFragment", "loadLatestPendingFeatureRequest (Attempt ${retry + 1}): Error loading latest pending feature request: ${e.message}")
                    if (retry < maxRetries) {
                        android.util.Log.e("DaoDetailsFragment", "loadLatestPendingFeatureRequest: Retrying in ${retryDelayMillis}ms due to error...")
                    } else {
                        // exhausted retries, show error UI
                        binding.latestFeatureRequestPreviewCard.visibility = View.GONE
                        binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
                        binding.latestFeatureRequestPreviewCard.setOnClickListener(null)
                        binding.latestFeatureRequestPreviewCard.isClickable = false
                        binding.latestFeatureRequestPreviewCard.alpha = 0.5f
                    }
                }
            }
        }
    }

    private fun updateVotingCardUI(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        binding.updateTitle.text = poll.title
        binding.votesRequiredText.visibility = View.VISIBLE
        binding.totalVotes.visibility = View.VISIBLE

        binding.yesPercentage.text = "${poll.yesPercentage}%"
        binding.noPercentage.text = "${poll.noPercentage}%"
        binding.pendingPercentage.text = "${poll.pendingPercentage}%"

        binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed"
        binding.totalVotes.text =
                "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted"

        updateVotingProgressBars(poll.yesPercentage, poll.noPercentage, poll.pendingPercentage)

        updateVotingState(poll)
    }

    private fun updateVotingState(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        binding.btnVote.visibility = View.GONE
        binding.votingStatus.visibility = View.GONE

        when {
            !poll.isActive && poll.isApproved -> { // Voting closed and approved
                binding.votingStatus.text = "Approved"
                binding.votingStatus.setTextColor(
                        resources.getColor(android.R.color.holo_green_dark, null)
                )
                binding.votingStatus.visibility = View.VISIBLE
                binding.votingCard.isClickable =
                        false // Make card non-clickable after status is final
                binding.votingCard.alpha = 0.5f // Gray out after status is final
            }
            !poll.isActive -> { // Voting closed and not approved
                binding.votingStatus.text = "Voting Closed"
                binding.votingStatus.setTextColor(
                        resources.getColor(android.R.color.darker_gray, null)
                )
                binding.votingStatus.visibility = View.VISIBLE
                binding.votingCard.isClickable =
                        false // Make card non-clickable after status is final
                binding.votingCard.alpha = 0.5f // Gray out after status is final
            }
            poll.hasUserVoted -> { // Voting active, user has voted
                binding.btnVote.isEnabled = false // Disable vote button if already voted
                binding.votingCard.alpha = 1.0f // Full alpha if active/voted
            }
            isUserMember -> { // Voting active, user is member, user has NOT voted
                binding.btnVote.visibility = View.VISIBLE
                binding.btnVote.isEnabled = true // Enable vote button
                binding.votingCard.alpha = 1.0f // Full alpha if active/can vote
            }
            else -> {
                binding.votingCard.alpha = 0.5f // Gray out if not member
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnJoinDao.setOnClickListener { joinDao() }

        // btnFeatureRequest click listener is correct for navigating to FeatureListFragment
        binding.btnFeatureRequest.setOnClickListener {
            if (isUserMember) {
                val bundle =
                        Bundle().apply {
                            putString("blockId", daoBlock.blockId) // Pass DAO block ID
                            putString("daoUniqueId", daoData.SW_UNIQUE_ID) // Pass DAO unique ID")
                        }
                findNavController()
                        .navigate(
                                R.id.action_daoDetailsFragment_to_featureListFragment,
                                bundle
                        )
            }
        }

        // btnSeeAllVotes click listener (Existing) - Navigate to AllVotingPollsFragment
        binding.btnSeeAllVotes.setOnClickListener {
            if (isUserMember) {
                // Navigate to all voting polls
                val bundle =
                        Bundle().apply {
                            putString("blockId", daoBlock.blockId)
                             putString("daoUniqueId", daoData.SW_UNIQUE_ID)
                        }
                findNavController()
                        .navigate(R.id.action_daoDetailsFragment_to_allVotingPollsFragment, bundle)
            }
        }

        binding.btnSeeAllFeatures.setOnClickListener { // Use the original ID from XML
            // Navigate to the FeatureListFragment
            val bundle =
                    Bundle().apply {
                        putString("blockId", daoBlock.blockId)
                        putString("daoUniqueId", daoData.SW_UNIQUE_ID)
                        // putString("daoId", daoData.SW_UNIQUE_ID)
                    }
            findNavController()
                    .navigate(R.id.action_daoDetailsFragment_to_featureListFragment, bundle)
        }
        // The click listener for latest_feature_request_preview_card is set dynamically in
        // loadLatestPendingFeatureRequest
    }

    // Helper function to navigate to FeatureVotingFragment
    private fun navigateToVotingFragment(daoBlockId: String, solutionId: String) {
        val bundle =
                Bundle().apply {
                    putString("blockId", daoBlockId)
                    putString("solutionId", solutionId)
                    putString("daoUniqueId", daoData.SW_UNIQUE_ID)
                }
        findNavController()
                .navigate(R.id.action_daoDetailsFragment_to_featureVotingFragment, bundle)
    }


    // Helper function to navigate to FeatureSolutionFragment
    private fun navigateToSubmitSolution(featureRequest: FeatureRequestTD) {
        val bundle =
                Bundle().apply {
                    putString("featureId", featureRequest.featureId)
//                    putString("daoId", daoBlock.blockId) // Pass DAO unique ID
                    putString("daoUniqueId", featureRequest.daoId) // Pass DAO block ID
                    putString("featureTitle", featureRequest.title)
                    putString("featureDescription", featureRequest.description)
                }

        findNavController().navigate(R.id.action_daoDetailsFragment_to_featureSolutionFragment, bundle)
    }

    private fun joinDao() {
        try {
            getP2pStoreCommunity().proposeJoinWallet(daoBlock)
            android.util.Log.d("DaoDetailsFragment", "Join proposal sent")
            lifecycleScope.launch {
                checkMembership()
                updateUIBasedOnMembership()
                loadRecentVotingPoll()
                loadLatestPendingFeatureRequest()
                loadLatestApprovedUpdate()
            }
        } catch (e: Exception) {
            android.util.Log.e("DaoDetailsFragment", "Error joining DAO: ${e.message}")
            // Show an error message
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Helper extension function to convert hex string to ByteArray (Keeping existing)
    private fun String.hexToBytes(): ByteArray {
        val len = this.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                    ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16))
                            .toByte()
            i += 2
        }
        return data
    }
}
