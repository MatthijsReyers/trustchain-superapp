//package nl.tudelft.trustchain.p2playstore.ui
//
//import android.content.Intent
//import android.net.Uri
//import android.os.Build
//import android.os.Bundle
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.annotation.RequiresApi
//import androidx.fragment.app.viewModels
//import androidx.lifecycle.Observer
//import androidx.navigation.fragment.findNavController
//import nl.tudelft.trustchain.p2playstore.R
//import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTD
//import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTD
//import nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll
//import nl.tudelft.trustchain.p2playstore.databinding.FragmentAppDetailsBinding
//import nl.tudelft.trustchain.p2playstore.utils.AppUtils // Import the new utility
//import nl.tudelft.ipv8.util.toHex
//import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTD // Import necessary data classes
//
//class AppDetails : BaseFragment() {
//    private var _binding: FragmentAppDetailsBinding? = null
//    private val binding get() = _binding!!
//
//    // Use the viewModels delegate to get a ViewModel instance
//    // Provide a Factory if your ViewModel requires dependencies (like Application)
//    private val viewModel: AppDetailsViewModel by viewModels {
//        AppDetailsViewModel.Factory(requireActivity().application)
//    }
//
//    // State is now held in the ViewModel, accessed via viewModel.uiState.value?.data
//    // Remove local state variables like daoBlock, daoData, isUserMember, featureSolution etc.
//    // private lateinit var daoBlock: TrustChainBlock // Removed
//    // private lateinit var daoData: SWJoinBlockTD // Removed
//    // private var isUserMember = false // Removed
//    // private var featureSolution: FeatureSolutionTD? = null // Removed - Access via ViewModel state
//
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentAppDetailsBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    @RequiresApi(Build.VERSION_CODES.S)
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        setupClickListeners()
//        observeViewModel()
//
//        // Get the block ID from arguments and trigger loading in the ViewModel
//        val blockId = arguments?.getString("blockId")
//        if (blockId != null) {
//            viewModel.loadAppDetails(blockId)
//        } else {
//            Log.e("AppDetailsFragment", "No block ID provided")
//            Toast.makeText(context, "Error: Missing DAO information.", Toast.LENGTH_SHORT).show()
//            findNavController().navigateUp()
//        }
//    }
//
//    // Observe the ViewModel's UI state
//    private fun observeViewModel() {
//        viewModel.uiState.observe(viewLifecycleOwner, Observer { state ->
//            when (state) {
//                is AppDetailsUiState.Loading -> {
//                    // Show loading indicator, hide content and error
//                    binding.progressBar.visibility = View.VISIBLE // Assuming you add a ProgressBar in XML
//                    binding.contentLayout.visibility = View.GONE // Hide content while loading
//                    binding.errorText.visibility = View.GONE
//                }
//                is AppDetailsUiState.Success -> {
//                    // Hide loading, show content, hide error
//                    binding.progressBar.visibility = View.GONE
//                    binding.contentLayout.visibility = View.VISIBLE
//                    binding.errorText.visibility = View.GONE
//                    // Update all UI elements with the data from the state
//                    updateUI(state.data)
//                }
//                is AppDetailsUiState.Error -> {
//                    // Hide loading, hide content, show error message
//                    binding.progressBar.visibility = View.GONE
//                    binding.contentLayout.visibility = View.GONE
//                    binding.errorText.visibility = View.VISIBLE
//                    binding.errorText.text = state.message
//                    // Optionally show a retry button or navigate up
//                    // binding.btnRetry.visibility = View.VISIBLE // Assuming a retry button exists
//                    // binding.btnRetry.setOnClickListener { viewModel.loadAppDetails(viewModel.currentDaoBlockId) } // Assuming currentDaoBlockId is accessible
//                }
//                is AppDetailsUiState.JoiningDao -> {
//                    // Show joining progress/message, disable join button
//                    Toast.makeText(context, "Joining DAO...", Toast.LENGTH_SHORT).show()
//                    binding.btnJoinDao.isEnabled = false // Disable join button while joining
//                }
//                is AppDetailsUiState.DaoJoined -> {
//                    // Show success message, state will transition back to Success(data) after reload
//                    Toast.makeText(context, "Successfully joined ${state.daoName}!", Toast.LENGTH_SHORT).show()
//                    // UI will be updated when loadAppDetails completes (triggered after join success in ViewModel)
//                }
//                is AppDetailsUiState.JoiningDaoError -> {
//                    // Show error message, re-enable join button
//                    Toast.makeText(context, "Failed to join DAO: ${state.message}", Toast.LENGTH_LONG).show()
//                    binding.btnJoinDao.isEnabled = true // Re-enable join button on error
//                }
//                is AppDetailsUiState.SubmittingVote -> {
//                    // Show voting progress/message, disable vote buttons and voting card
//                    Toast.makeText(context, "Submitting vote...", Toast.LENGTH_SHORT).show()
//                    binding.btnVote.isEnabled = false // Disable vote button within the card
//                    binding.votingCard.isClickable = false // Make card non-clickable while submitting
//                }
//                is AppDetailsUiState.VoteSubmitted -> {
//                    // Show success message, state will transition back to Success(data) after reload
//                    Toast.makeText(context, "Vote submitted successfully!", Toast.LENGTH_SHORT).show()
//                    // UI will be updated when loadAppDetails completes (triggered after vote success in ViewModel)
//                }
//                is AppDetailsUiState.VoteSubmissionError -> {
//                    // Show error message, re-enable vote buttons and voting card
//                    Toast.makeText(context, "Error submitting vote: ${state.message}", Toast.LENGTH_LONG).show()
//                    // Re-enable buttons and card interaction based on current data
//                    val currentDataState = viewModel.uiState.value as? AppDetailsUiState.Success
//                    if (currentDataState != null) {
//                        updateVotingState(currentDataState.data.recentVotingPoll, currentDataState.data.isUserMember, currentDataState.data.myPublicKeyHex)
//                        binding.votingCard.isClickable = currentDataState.data.isUserMember // Re-enable card click if member
//                    }
//                }
//                is AppDetailsUiState.TriggeringRewardTransfer -> {
//                    // Show message indicating transfer initiation, disable claim button
//                    Toast.makeText(context, "Initiating reward transfer...", Toast.LENGTH_SHORT).show()
//                    binding.btnClaimReward.isEnabled = false // Disable claim button
//                }
//                is AppDetailsUiState.RewardTransferTriggered -> {
//                    // Show success message (transfer proposed, not necessarily completed), state will transition back to Success(data) after reload
//                    Toast.makeText(context, "Reward transfer proposal created!", Toast.LENGTH_LONG).show()
//                    // UI will be updated when loadAppDetails completes
//                }
//                is AppDetailsUiState.RewardTransferError -> {
//                    // Show error message, re-enable claim button
//                    Toast.makeText(context, "Error triggering reward transfer: ${state.message}", Toast.LENGTH_LONG).show()
//                    binding.btnClaimReward.isEnabled = true // Re-enable claim button on error
//                }
//            }
//        })
//    }
//
//    // Function to update the UI based on the data from the ViewModel
//    private fun updateUI(data: AppDetailsData) {
//        val daoBlock = data.daoBlock
//        val daoData = data.daoData
//        val isUserMember = data.isUserMember
//        val myPublicKeyHex = data.myPublicKeyHex
//
//        // Set DAO basic info
//        binding.daoName.text = daoBlock.transaction["name"]?.toString() ?: "Unknown DAO"
//        binding.daoCategory.text = daoBlock.transaction["category"]?.toString() ?: "General"
//        binding.daoMembersCount.text = daoData.SW_TRUSTCHAIN_PKS.size.toString()
//        binding.daoDownloads.text = "0" // TODO: Implement download tracking
//        binding.daoDeveloper.text = daoBlock.publicKey.toHex().take(8) + "..."
//        binding.daoDescription.text =
//            daoBlock.transaction["description"]?.toString() ?: "No description available"
//
//        // Set entrance fee for join button
//        binding.btnJoinDao.text = data.joinFeeText
//
//        // Set icon using utility
//        binding.daoIcon.setImageResource(AppUtils.iconFromIconId(daoBlock.transaction["iconIndex"]))
//
//        // Update UI elements based on membership status (driven by ViewModel data)
//        updateUIBasedOnMembership(isUserMember)
//
//        // Update UI for Latest Approved Update
//        updateLatestApprovedUpdateUI(data.latestApprovedUpdateBlock, data.latestApprovedUpdateSolution, isUserMember)
//
//        // Update UI for Recent Voting Poll
//        updateRecentVotingPollUI(data.recentVotingPoll, isUserMember, myPublicKeyHex)
//
//        // Update UI for Latest Pending Feature Request
//        updateLatestPendingFeatureRequestUI(data.latestPendingFeatureRequest)
//
//        // TODO: Update Screenshots UI (Requires loading screenshots)
//        // binding.screenshotsContainer...
//    }
//
//    private fun updateUIBasedOnMembership(isUserMember: Boolean) {
//        if (isUserMember) {
//            binding.btnJoinDao.visibility = View.GONE
//            // btnInstallUpdate visibility is also controlled by updateLatestApprovedUpdateUI
//            binding.btnFeatureRequest.isEnabled = true
//            binding.btnFeatureRequest.alpha = 1.0f
//            // Voting card clickability/alpha handled in updateVotingState
//        } else {
//            binding.btnJoinDao.visibility = View.VISIBLE
//            binding.btnInstallUpdate.visibility = View.GONE
//            binding.btnFeatureRequest.isEnabled = false
//            binding.btnFeatureRequest.alpha = 0.5f
//            // Voting card clickability/alpha handled in updateVotingState
//        }
//    }
//
//    private fun updateLatestApprovedUpdateUI(block: TrustChainBlock?, solution: FeatureSolutionTD?, isUserMember: Boolean) {
//        if (block != null && solution != null) {
//            binding.daoVersion.text = "v${block.sequenceNumber}" // Use block sequence number as a simple version
//            // Set click listener for update button - only visible if user is member
//            if (isUserMember) {
//                binding.btnInstallUpdate.visibility = View.VISIBLE
//                binding.btnInstallUpdate.setOnClickListener {
//                    downloadAndInstallUpdate(solution.apkMagnetLink)
//                }
//            } else {
//                binding.btnInstallUpdate.visibility = View.GONE
//            }
//
//        } else {
//            binding.daoVersion.text = "No updates"
//            binding.btnInstallUpdate.visibility = View.GONE // Hide update button if no approved updates
//        }
//    }
//
//    private fun updateRecentVotingPollUI(poll: VotingPoll?, isUserMember: Boolean, myPublicKeyHex: String) {
//        if (poll != null) {
//            binding.votingCard.visibility = View.VISIBLE
//
//            binding.updateTitle.text = poll.title
//            // Question text is static in XML, but could be set here if needed:
//            // binding.votingQuestion.text = poll.question
//
//            binding.yesPercentage.text = "${poll.yesPercentage}%"
//            binding.noPercentage.text = "${poll.noPercentage}%"
//            binding.pendingPercentage.text = "${poll.pendingPercentage}%"
//
//            binding.votesRequiredText.text = getString(R.string.votes_required_text, poll.yesVotes, poll.votesNeeded)
//            binding.totalVotes.text = getString(R.string.total_votes_text, poll.yesVotes + poll.noVotes, poll.totalMembers)
//
//            // Update progress bars using the utility
//            AppUtils.updateProgressBars(
//                binding.votingCard, // Use votingCard as container view for width calculation
//                binding.yesProgressBar,
//                binding.noProgressBar,
//                binding.pendingProgressBar,
//                poll.yesPercentage,
//                poll.noPercentage,
//                poll.pendingPercentage
//            )
//
//
//            // Update vote state (buttons, status text visibility)
//            updateVotingState(poll, isUserMember, myPublicKeyHex)
//
//            // Set click listener for the voting card - Navigate to FeatureVotingFragment
//            binding.votingCard.setOnClickListener {
//                // Only navigate if the poll is active and user is a member
//                if (poll.isActive && isUserMember) {
//                    // Need DAO block ID and solution ID to navigate
//                    val currentData = viewModel.uiState.value?.data
//                    if (currentData != null) {
//                        navigateToVotingFragment(currentData.daoBlock.blockId, poll.id) // Use poll.id as solutionId
//                    }
//                }
//            }
//
//            // See All votes button - Navigate to AllVotingPollsFragment
//            binding.btnSeeAllVotes.isEnabled = isUserMember // Only members can see all votes
//            binding.btnSeeAllVotes.alpha = if(isUserMember) 1.0f else 0.5f
//            binding.btnSeeAllVotes.setOnClickListener {
//                val currentData = viewModel.uiState.value?.data
//                if (currentData != null && currentData.isUserMember) {
//                    val bundle =
//                        Bundle().apply {
//                            putString("blockId", currentData.daoBlock.blockId)
//                            putString("daoUniqueId", currentData.daoData.SW_UNIQUE_ID)
//                        }
//                    findNavController().navigate(R.id.action_appDetailsFragment_to_allVotingPollsFragment, bundle)
//                } else {
//                    Toast.makeText(context, "You must be a member to see all votes.", Toast.LENGTH_SHORT).show()
//                }
//            }
//
//
//        } else {
//            binding.votingCard.visibility = View.GONE
//            binding.btnSeeAllVotes.isEnabled = false // Disable "See All" if no polls
//            binding.btnSeeAllVotes.alpha = 0.5f
//            binding.btnSeeAllVotes.setOnClickListener(null) // Remove listener
//        }
//    }
//
//    private fun updateLatestPendingFeatureRequestUI(request: FeatureRequestTD?) {
//        if (request != null) {
//            binding.latestFeatureRequestPreviewCard.visibility = View.VISIBLE
//            binding.tvNoPendingFeatureRequests.visibility = View.GONE
//
//            binding.tvLatestFeatureTitle.text = request.title
//            binding.tvLatestFeatureDescription.text = request.description
//            binding.tvLatestFeatureReward.text = "Reward: ${request.reward} sats"
//            binding.tvLatestFeatureSolutionCount.text = "0 solution(s)" // Always 0 for pending request preview
//
//            // Set click listener to navigate to submit solution
//            binding.latestFeatureRequestPreviewCard.setOnClickListener {
//                navigateToSubmitSolution(request)
//            }
//            binding.latestFeatureRequestPreviewCard.isClickable = true
//            binding.latestFeatureRequestPreviewCard.alpha = 1.0f
//
//        } else {
//            binding.latestFeatureRequestPreviewCard.visibility = View.GONE
//            binding.tvNoPendingFeatureRequests.visibility = View.VISIBLE
//            binding.latestFeatureRequestPreviewCard.setOnClickListener(null) // Remove listener
//            binding.latestFeatureRequestPreviewCard.isClickable = false
//            binding.latestFeatureRequestPreviewCard.alpha = 0.5f
//        }
//    }
//
//
//    // Refactored updateVotingState to be called with data from ViewModel
//    private fun updateVotingState(poll: VotingPoll?, isUserMember: Boolean, myPublicKeyHex: String) {
//        if (poll == null) {
//            binding.btnVote.visibility = View.GONE
//            binding.votingStatus.visibility = View.GONE
//            binding.btnClaimReward.visibility = View.GONE // Hide claim button if no poll
//            return
//        }
//
//        binding.btnVote.visibility = View.GONE // Hide vote button by default
//        binding.votingStatus.visibility = View.GONE // Hide status text by default
//        binding.btnClaimReward.visibility = View.GONE // Hide claim reward button by default
//
//
//        // Check if the current peer is the initiator of the DAO
//        val isDaoInitiator = viewModel.uiState.value?.data?.daoBlock?.publicKey?.toHex() == myPublicKeyHex
//
//
//        when {
//            // Voting closed and approved (enough YES votes)
//            !poll.isActive && poll.isApproved -> {
//                binding.votingStatus.text = "Approved"
//                binding.votingStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
//                binding.votingStatus.visibility = View.VISIBLE
//                binding.votingCard.isClickable = false // Make card non-clickable after status is final
//                binding.votingCard.alpha = 0.5f // Gray out after status is final
//                binding.votesRequiredText.visibility = View.GONE // Hide required text when decided
//                binding.totalVotes.visibility = View.GONE // Hide total votes when decided
//
//
//                // If approved and I am the developer who submitted this solution, show claim button
//                // Need the current solution block to check developer Public Key
//                val currentSolution = viewModel.uiState.value?.data?.latestApprovedUpdateSolution // Assuming this is the solution for the approved update
//                if (currentSolution != null && currentSolution.developerPublicKey == myPublicKeyHex) {
//                    binding.btnClaimReward.visibility = View.VISIBLE
//
//                    if (isDaoInitiator) {
//                        binding.btnClaimReward.text = "Transfer Reward"
//                        binding.btnClaimReward.isEnabled = true
//                        binding.btnClaimReward.alpha = 1.0f
//                        // Click listener for claim reward is set in setupClickListeners,
//                        // but enabled/disabled here
//                    } else {
//                        // Developer is a member but not initiator, they can see it's approved
//                        binding.btnClaimReward.text = "Approved - Reward Pending"
//                        binding.btnClaimReward.isEnabled = false
//                        binding.btnClaimReward.alpha = 0.5f
//                    }
//                }
//            }
//            // Voting closed and not approved
//            !poll.isActive && !poll.isApproved -> { // Use !poll.isApproved based on createVotingPoll logic
//                binding.votingStatus.text = "Voting Closed - Not Approved"
//                binding.votingStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
//                binding.votingStatus.visibility = View.VISIBLE
//                binding.votingCard.isClickable = false // Make card non-clickable after status is final
//                binding.votingCard.alpha = 0.5f // Gray out after status is final
//                binding.votesRequiredText.visibility = View.GONE // Hide required text when decided
//                binding.totalVotes.visibility = View.GONE // Hide total votes when decided
//
//            }
//            poll.hasUserVoted -> { // Voting active, user has voted
//                binding.votingStatus.text = "✓ You voted ${if (poll.userVote == true) "Yes" else "No"}" // Display user's vote
//                binding.votingStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
//                binding.votingStatus.visibility = View.VISIBLE
//                binding.votingCard.isClickable = isUserMember // Still clickable if member, even if voted (to see all votes for example)
//                binding.votingCard.alpha = 1.0f
//                binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
//                binding.totalVotes.visibility = View.VISIBLE // Keep total votes visible
//            }
//            isUserMember -> { // Voting active, user is member, user has NOT voted
//                binding.btnVote.visibility = View.VISIBLE // Show vote button
//                binding.btnVote.isEnabled = true // Enable vote button
//                binding.votingCard.alpha = 1.0f
//                binding.votingCard.isClickable = true // Clickable to go to voting fragment
//                binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
//                binding.totalVotes.visibility = View.VISIBLE // Keep total votes visible
//
//            }
//            else -> { // Voting active, user is NOT a member
//                binding.btnVote.visibility = View.GONE // Cannot vote
//                binding.votingCard.alpha = 0.5f // Gray out if not member
//                binding.votingCard.isClickable = false // Not clickable if not member
//                binding.votingStatus.visibility = View.GONE // No status for non-members
//                binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
//                binding.totalVotes.visibility = View.VISIBLE // Keep total votes visible
//            }
//        }
//
//        // Disable vote buttons within the card if not active or user has voted or not a member
//        // Note: The main btnVote visibility/enabled state is handled in the updateRecentVotingPollUI or the observer.
//        // This might be redundant depending on how you want the UI to behave. Keeping for clarity.
//        // if (!poll.isActive || poll.hasUserVoted || !isUserMember) {
//        //    // These buttons are only shown if isUserMember and !hasUserVoted, so this might be redundant.
//        // } else {
//        //    // These buttons are only shown if isUserMember and !hasUserVoted
//        // }
//    }
//
//
//    private fun setupClickListeners() {
//        // Join DAO button - call ViewModel function
//        binding.btnJoinDao.setOnClickListener {
//            val daoBlock = viewModel.uiState.value?.data?.daoBlock
//            if (daoBlock != null) {
//                viewModel.joinDao(daoBlock)
//            } else {
//                Toast.makeText(context, "Error: DAO data not loaded.", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // Request Feature button - navigate (Fragment's responsibility)
//        // Listener is already correctly set up in your original code, just ensure it's enabled/disabled based on membership via updateUIBasedOnMembership
//        binding.btnFeatureRequest.setOnClickListener {
//            val currentData = viewModel.uiState.value?.data
//            if (currentData != null && currentData.isUserMember) {
//                val bundle =
//                    Bundle().apply {
//                        putString("blockId", currentData.daoBlock.blockId)
//                        putString("daoUniqueId", currentData.daoData.SW_UNIQUE_ID)
//                    }
//                findNavController().navigate(R.id.action_appDetailsFragment_to_featureListFragment, bundle)
//            } else {
//                Toast.makeText(context, "You must be a member to request a feature.", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // See All Votes button - navigate (Fragment's responsibility)
//        // Listener is set up dynamically in updateRecentVotingPollUI based on poll availability and membership
//
//        // See All Features button - navigate (Fragment's responsibility)
//        binding.btnSeeAllFeatures.setOnClickListener {
//            val currentData = viewModel.uiState.value?.data
//            if (currentData != null) {
//                val bundle =
//                    Bundle().apply {
//                        putString("blockId", currentData.daoBlock.blockId)
//                        putString("daoUniqueId", currentData.daoData.SW_UNIQUE_ID)
//                    }
//                findNavController().navigate(R.id.action_appDetailsFragment_to_featureListFragment, bundle)
//            } else {
//                Toast.makeText(context, "Error loading DAO data.", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        // Vote button (within voting card) - call ViewModel function (listener set in updateRecentVotingPollUI)
//        // The Vote button's click listener is now set dynamically in updateRecentVotingPollUI
//        // based on whether the button is visible and enabled.
//
//        // Claim Reward button - call ViewModel function
//        // Listener is set here, but button visibility and enabled state are controlled in updateVotingState
//        binding.btnClaimReward.setOnClickListener {
//            val currentData = viewModel.uiState.value?.data
//            val recentPoll = currentData?.recentVotingPoll // Use the recent poll to get its state (approved/active)
//            val approvedSolution = currentData?.latestApprovedUpdateSolution // Use the *approved* solution for details (developer, etc.)
//
//            // Ensure we have the necessary data and the poll is approved before calling ViewModel
//            if (currentData != null && recentPoll != null && approvedSolution != null && !recentPoll.isActive && recentPoll.isApproved) {
//                viewModel.triggerRewardTransfer(recentPoll, currentData.daoBlock, approvedSolution)
//            } else {
//                Log.w("AppDetailsFragment", "Claim reward clicked but conditions not met. Data: $currentData, Poll Approved: ${recentPoll?.isApproved}, Poll Active: ${recentPoll?.isActive}")
//                Toast.makeText(context, "Reward cannot be claimed at this time.", Toast.LENGTH_SHORT).show()
//                // Optionally reload data to refresh state
//                currentData?.daoBlock?.blockId?.let { viewModel.loadAppDetails(it) }
//            }
//        }
//
//        // The click listener for latest_feature_request_preview_card is set dynamically in
//        // updateLatestPendingFeatureRequestUI based on ViewModel data
//    }
//
//    private fun downloadAndInstallUpdate(magnetLink: String?) {
//        if (!magnetLink.isNullOrEmpty()) {
//            Log.d("AppDetailsFragment", "Attempting to download/install from magnet link: $magnetLink")
//            // TODO: Implement actual download/install logic using your TorrentManager
//            Toast.makeText(context, "Initiating download from $magnetLink (simulated)", Toast.LENGTH_LONG).show()
//            try {
//                // Example: Open magnet link with external app (like a torrent client)
//                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetLink))
//                startActivity(intent)
//            } catch (e: Exception) {
//                Log.e("AppDetailsFragment", "Failed to open magnet link: ${e.message}")
//                Toast.makeText(context, "Could not open magnet link. Please ensure you have a torrent client installed.", Toast.LENGTH_LONG).show()
//            }
//        } else {
//            Log.w("AppDetailsFragment", "No magnet link available for the latest approved update.")
//            Toast.makeText(context, "No download link available for this update.", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//
//    // Helper function to navigate to FeatureVotingFragment (Fragment's responsibility)
//    private fun navigateToVotingFragment(daoBlockId: String, solutionId: String) {
//        val currentData = viewModel.uiState.value?.data
//        if (currentData != null) {
//            val bundle =
//                Bundle().apply {
//                    putString("blockId", daoBlockId)
//                    putString("solutionId", solutionId)
//                    // Need DAO Unique ID here, get from ViewModel's current data
//                    putString("daoUniqueId", currentData.daoData.SW_UNIQUE_ID)
//                }
//            findNavController().navigate(R.id.action_appDetailsFragment_to_featureVotingFragment, bundle)
//        } else {
//            Log.e("AppDetailsFragment", "Cannot navigate to voting: DAO data missing from ViewModel.")
//            Toast.makeText(context, "Error navigating to voting.", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    // Helper function to navigate to FeatureSolutionFragment (Fragment's responsibility)
//    private fun navigateToSubmitSolution(featureRequest: FeatureRequestTD) {
//        val currentData = viewModel.uiState.value?.data
//        if (currentData != null) {
//            val bundle =
//                Bundle().apply {
//                    putString("featureId", featureRequest.featureId)
//                    putString("daoUniqueId", featureRequest.daoId) // Use daoId from FeatureRequestTD
//                    putString("featureTitle", featureRequest.title)
//                    putString("featureDescription", featureRequest.description)
//                }
//            findNavController().navigate(R.id.action_appDetailsFragment_to_featureSolutionFragment, bundle)
//        } else {
//            Toast.makeText(context, "Error loading DAO data for submission.", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}
//
