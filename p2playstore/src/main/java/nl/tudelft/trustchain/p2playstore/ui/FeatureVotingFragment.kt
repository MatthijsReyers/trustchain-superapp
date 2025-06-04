package nl.tudelft.trustchain.p2playstore.ui

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
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureVotingBinding
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureRequestTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureSolutionTD
import nl.tudelft.trustchain.p2playstore.blockdata.FeatureVoteTD
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPollHelper
import nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity

class FeatureVotingFragment : BaseFragment() {
    private var _binding: FragmentFeatureVotingBinding? = null
    private val binding get() = _binding!!

    private lateinit var daoBlock: TrustChainBlock
    private lateinit var daoData: SWJoinBlockTD
    private var featureSolution: FeatureSolutionTD? = null // For standard feature solutions
    private var joinRequestFeature: FeatureRequestTD? = null // For join request features
    private var votes: List<FeatureVoteTD> = emptyList()
    private var hasUserVoted = false
    private lateinit var daoUniqueId: String
    private var isJoinRequest = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatureVotingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val blockId = arguments?.getString("blockId")
        val solutionId = arguments?.getString("solutionId") // Used for standard features
        val featureId = arguments?.getString("featureId") // Used for join requests
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""
        isJoinRequest = arguments?.getBoolean("isJoinRequest") ?: false // Get the boolean flag


        if (blockId != null && daoUniqueId.isNotEmpty()) {
            if (isJoinRequest && featureId != null) {
                android.util.Log.d("FeatureVotingFragment", "Loading for Join Request: Feature ID $featureId, DAO ID $daoUniqueId BlockID $blockId")
                loadDaoBlockAndJoinRequest(blockId, featureId)
                setupClickListeners()
            } else if (!isJoinRequest && solutionId != null) {
                android.util.Log.d("FeatureVotingFragment", "Loading for Standard Feature Solution: Solution ID $solutionId, DAO ID $daoUniqueId")
                loadDaoBlockAndSolution(blockId, solutionId)
                setupClickListeners()
            } else {
                android.util.Log.e("FeatureVotingFragment", "Missing required arguments for voting. isJoinRequest: $isJoinRequest, featureId: $featureId, solutionId: $solutionId")
                Toast.makeText(context, "Error: Missing voting information.", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        } else {
            android.util.Log.e("FeatureVotingFragment", "Missing DAO block ID or DAO Unique ID")
            Toast.makeText(context, "Error: Missing voting information.", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }


    // New function to load data for a Join Request feature
    private fun loadDaoBlockAndJoinRequest(blockId: String, featureId: String) {
        lifecycleScope.launch {
            try {
                if (daoUniqueId.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "daoUniqueId is empty. Cannot load voting data for join request.")
                    setupFallbackUI() // Or a different fallback UI for join requests
                    return@launch
                }

                val daoBlock = withContext(Dispatchers.IO) {
                    p2playStore.getDaoBlock(blockId)
                }

                if (daoBlock != null) {
                    this@FeatureVotingFragment.daoBlock = daoBlock
                    daoData = SWJoinBlockTransactionData(daoBlock.transaction).getData()

                    // Load the specific join request feature
                    val featureRequests = withContext(Dispatchers.IO) {
                        p2playStore.getFeatureRequestsForDao(daoUniqueId).filter {
                            it.featureId == featureId && it.requestType == P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE
                        }
                    }
                    joinRequestFeature = featureRequests.firstOrNull()

                    if (joinRequestFeature != null) {
                        // Load votes for this join request feature
                        votes = withContext(Dispatchers.IO) {
                            p2playStore.getVotesForSolution(daoUniqueId, featureId) // Use featureId as 'solutionId' for votes on join requests
                        }
                        android.util.Log.d("FeatureVotingFragment", "loadDaoBlockAndJoinRequest: Loaded ${votes.size} votes for join request feature ${featureId} in DAO ${daoUniqueId}")
                        setupVotingUIForJoinRequest(joinRequestFeature!!)

                    } else {
                        android.util.Log.e("FeatureVotingFragment", "Join request feature not found for ID: $featureId in DAO $daoUniqueId")
                        setupFallbackUI() // Or a different fallback UI
                    }

                } else {
                    android.util.Log.e("FeatureVotingFragment", "DAO block not found for ID: $blockId")
                    findNavController().navigateUp()
                }

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error loading DAO block or join request: ${e.message}")
                setupFallbackUI() // Or a different fallback UI
            }
        }
    }


    // Existing function to load data for a standard feature solution
    private fun loadDaoBlockAndSolution(blockId: String, solutionId: String) {
        lifecycleScope.launch {
            try {
                if (daoUniqueId.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "daoUniqueId is empty. Cannot load voting data.")
                    setupFallbackUI()
                    return@launch
                }

                val daoBlock = withContext(Dispatchers.IO) {
                    p2playStore.getDaoBlock(blockId)
                }

                if (daoBlock != null) {
                    this@FeatureVotingFragment.daoBlock = daoBlock
                    daoData = SWJoinBlockTransactionData(daoBlock.transaction).getData()

                    // Load the specific solution for this DAO
                    val solutions = withContext(Dispatchers.IO) {
                        // Fetch all solutions for this DAO and filter by solutionId
                        p2playStore.getSolutionsForFeature(daoUniqueId).filter { it.solutionId == solutionId }
                    }
                    featureSolution = solutions.firstOrNull()

                    if (featureSolution != null) {
                        // Load the corresponding feature request for context and reward info for this DAO
                        val featureRequests = withContext(Dispatchers.IO) {
                            // Fetch all feature requests for the DAO and filter by featureId (and ensure it's a standard feature)
                            p2playStore.getFeatureRequestsForDao(daoUniqueId).filter {
                                it.featureId == featureSolution!!.featureId && it.requestType != P2pStoreCommunity.JOIN_REQUEST_FEATURE_TYPE
                            }
                        }
                        val correspondingRequest = featureRequests.firstOrNull()


                        if (correspondingRequest != null) {
                            // Load votes for this solution within this DAO
                            votes = withContext(Dispatchers.IO) {
                                // Fetch votes specifically for this solution within this DAO
                                p2playStore.getVotesForSolution(daoUniqueId, solutionId)
                            }
                            android.util.Log.d("FeatureVotingFragment", "loadDaoBlockAndSolution: Loaded ${votes.size} votes for solution ${solutionId} in DAO ${daoUniqueId}")
                            setupVotingUIForSolution(correspondingRequest, featureSolution!!) // Use a specific UI setup for solutions
                        } else {
                            android.util.Log.e("FeatureVotingFragment", "Corresponding standard feature request not found for solution ID: $solutionId in DAO $daoUniqueId")
                            setupFallbackUI()
                        }
                    } else {
                        android.util.Log.e("FeatureVotingFragment", "Feature solution not found for ID: $solutionId in DAO $daoUniqueId")
                        setupFallbackUI()
                    }

                } else {
                    android.util.Log.e("FeatureVotingFragment", "DAO block not found for ID: $blockId")
                    findNavController().navigateUp()
                }

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error loading DAO block, solution, or request: ${e.message}")
                setupFallbackUI()
            }
        }
    }

    // New function to set up UI for Join Request voting
    private fun setupVotingUIForJoinRequest(joinRequest: FeatureRequestTD) {
        try {
            // Set UI elements for Join Request
            binding.originalFeatureTitle.text = "Join Request" // Title for join request
            binding.implementationDescription.text = "Details: ${joinRequest.description}" // Use description from the feature request
            binding.developerName.text = "Requester: ${joinRequest.requesterPublicKey.take(8)}..." // Show requester
            binding.rewardAmount.text = "Entrance Fee: ${joinRequest.reward} sats" // Show entrance fee as reward

            // Hide download APK button for join requests
            binding.btnDownloadApk.visibility = View.GONE

            val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
            val votingThreshold = daoData.SW_VOTING_THRESHOLD // Use DAO's general voting threshold for join requests
            val myPublicKey = p2playStore.myPeer.publicKey.keyToBin().toHex()

            // Check if user has voted on this specific join request feature
            // For join requests, the vote's solutionId is set to the featureId
            hasUserVoted = votes.any { it.voterPublicKey == myPublicKey && it.solutionId == joinRequest.featureId }
            val userVote = votes.find { it.voterPublicKey == myPublicKey && it.solutionId == joinRequest.featureId }?.isYes

            // Manually create the VotingPoll object for the join request
            val yesVotes = votes.count { it.isYes }
            val noVotes = votes.count { !it.isYes }
            val isActive = joinRequest.status == "OPEN" // Assume active if status is OPEN

            val votingPoll = VotingPoll(
                id = joinRequest.featureId, // Use featureId as the poll ID for join requests
                title = "Vote on Join Request",
                question = "Should ${joinRequest.requesterPublicKey.take(8)}... be allowed to join the DAO?",
                yesVotes = yesVotes,
                noVotes = noVotes,
                totalMembers = totalMembers,
                votingThreshold = votingThreshold,
                isActive = isActive,
                hasUserVoted = hasUserVoted,
                userVote = userVote
            )

            updateVotingUIWithPoll(votingPoll)

        } catch (e: Exception) {
            android.util.Log.e("FeatureVotingFragment", "Error setting up voting UI for join request: ${e.message}")
            setupFallbackUI()
        }
    }




    // Existing function to set up UI for standard Feature voting
    private fun setupVotingUIForSolution(featureRequest: FeatureRequestTD, solution: FeatureSolutionTD) {
        try {
            // Use feature request and solution data
            binding.originalFeatureTitle.text = featureRequest.title
            binding.implementationDescription.text = solution.description
            binding.developerName.text = solution.developerPublicKey.take(8) + "..."

            // Display the actual reward from the feature request
            binding.rewardAmount.text = "${featureRequest.reward} sats"

            // Show download APK button for solutions if magnet link exists
            binding.btnDownloadApk.visibility = if (solution.apkMagnetLink.isNotEmpty()) View.VISIBLE else View.GONE

            val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
            val votingThreshold = daoData.SW_VOTING_THRESHOLD
            val myPublicKey = p2playStore.myPeer.publicKey.keyToBin().toHex()

            // Check if user has voted on this specific solution
            hasUserVoted = votes.any { it.voterPublicKey == myPublicKey && it.solutionId == solution.solutionId }
            val userVote = votes.find { it.voterPublicKey == myPublicKey && it.solutionId == solution.solutionId }?.isYes


            // Create a VotingPoll object
            val votingPoll = VotingPollHelper.createVotingPoll(
                featureRequest, // Pass the actual request
                solution,
                votes,
                totalMembers,
                votingThreshold,
                hasUserVoted = hasUserVoted,
                userVote = userVote
            )

            updateVotingUIWithPoll(votingPoll)

        } catch (e: Exception) {
            android.util.Log.e("FeatureVotingFragment", "Error setting up voting UI: ${e.message}")
            setupFallbackUI()
        }
    }

    private fun updateVotingUIWithPoll(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        binding.percentageYes.text = "${poll.yesPercentage}%"
        binding.percentageNo.text = "${poll.noPercentage}%"
        binding.percentagePending.text = "${poll.pendingPercentage}%"

        binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed for approval"

        updateProgressBars(poll.yesPercentage, poll.noPercentage, poll.pendingPercentage)
        updateVotingState(poll)
    }


    private fun setupFallbackUI() {
        // Keep the existing fallback UI
        binding.originalFeatureTitle.text = "Dark Mode Support"
        binding.implementationDescription.text = "Added dark theme toggle in settings with automatic switching based on system preference."
        binding.developerName.text = "abc123..."
        binding.rewardAmount.text = "10000 sats"

        binding.percentageYes.text = "0%"
        binding.percentageNo.text = "0%"
        binding.percentagePending.text = "100%"

        updateProgressBars(0, 0, 100)
        binding.votesRequiredText.text = "0 of 1 votes needed for approval"
        // Hide voting buttons in fallback
        binding.votingButtonsLayout.visibility = View.GONE
        binding.alreadyVotedText.visibility = View.GONE
    }
//    TODO: still in draft mode
    // Modify updateVotingState to handle Join Request specific logic (e.g., no Claim Reward)
    private fun updateVotingState(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        val isUserMember = daoData.SW_TRUSTCHAIN_PKS.contains(p2playStore.myPeer.publicKey.keyToBin().toHex())
        // DAO initiator status might be relevant if only initiator can finalize join after vote
        // val isDaoInitiator = daoBlock.publicKey.toHex() == p2playStore.myPeer.publicKey.keyToBin().toHex()

        binding.votingButtonsLayout.visibility = View.GONE // Hide vote buttons by default
        binding.btnClaimReward.visibility = View.GONE // Hide claim reward button by default
        binding.votesRequiredText.visibility = View.VISIBLE // Show requirement text
        binding.alreadyVotedText.visibility = View.VISIBLE // Show total votes cast text


        when {
            // Voting closed and approved (enough YES votes)
            !poll.isActive && poll.yesVotes >= poll.votesNeeded -> {
                binding.alreadyVotedText.text = "Approved" // Use the total votes TextView to show final status
                binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                binding.votesRequiredText.visibility = View.GONE // Hide required text when decided

                // For Join Requests, approval means the user can now be added to the DAO.
                // This is where the actual Bitcoin multisig update needs to be triggered.
                // For now, let's just indicate approval. The actual join needs separate logic.
                if (isJoinRequest) {
                    // If current user is the requester of the join request
                    if (joinRequestFeature?.requesterPublicKey == p2playStore.myPeer.publicKey.pub().toString()) {
                        // Display a message to the requester that they are approved
                        binding.btnClaimReward.visibility = View.VISIBLE // Reusing the button for message/action
                        binding.btnClaimReward.text = "Join Approved! Finalize Join."
                        binding.btnClaimReward.isEnabled = isUserMember // Only members can finalize? Or only initiator?
                        binding.btnClaimReward.alpha = if (isUserMember) 1.0f else 0.5f
                        binding.btnClaimReward.setOnClickListener {
                            // TODO: Implement logic to trigger the actual Bitcoin multisig join here
                            android.util.Log.d("FeatureVotingFragment", "Finalize Join button clicked for approved join request.")
                            Toast.makeText(context, "Finalizing join (TODO: Bitcoin multisig update needed)", Toast.LENGTH_LONG).show()
                        }
                    } else if (isUserMember) {
                        // If current user is an existing DAO member and it's approved
                        binding.btnClaimReward.visibility = View.VISIBLE
                        binding.btnClaimReward.text = "Join Request Approved" // Indicate approval to members
                        binding.btnClaimReward.isEnabled = false
                        binding.btnClaimReward.alpha = 0.5f
                    }

                } else {
                    // Existing logic for standard feature solutions: Show Claim Reward button if user is developer and initiator
                    if (featureSolution?.developerPublicKey == p2playStore.myPeer.publicKey.pub().toString() /* && isDaoInitiator */) { // Re-evaluate initiator check
                        binding.btnClaimReward.visibility = View.VISIBLE
                        binding.btnClaimReward.text = "Trigger Reward Transfer" // Or "Claim Reward"
                        binding.btnClaimReward.isEnabled = isUserMember // Only members can trigger? Or only initiator?
                        binding.btnClaimReward.alpha = if (isUserMember) 1.0f else 0.5f
                        binding.btnClaimReward.setOnClickListener {
                            triggerRewardTransfer(poll)
                        }
                    }
                }
            }
            // Voting closed and not approved
            !poll.isActive && poll.yesVotes < poll.votesNeeded -> {
                binding.alreadyVotedText.text = if (isJoinRequest) "Join Request Denied" else "Voting Closed - Not Approved"
                binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                binding.votesRequiredText.visibility = View.GONE // Hide required text when decided
            }
            poll.hasUserVoted -> { // Voting active, user has voted
                binding.alreadyVotedText.text = "✓ You voted ${if (poll.userVote == true) "Yes" else "No"}" // Use the total votes TextView
                binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
            }
            isUserMember -> { // Voting active, user is member, user has NOT voted
                binding.votingButtonsLayout.visibility = View.VISIBLE // Show vote buttons
                binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
                binding.alreadyVotedText.text = "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted" // Reset total votes text if user hasn't voted yet
                binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null)) // Reset text color

            }
            else -> {
                // User is not a member, voting is active, they cannot vote.
                // Buttons remain hidden by default.
                binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
                binding.alreadyVotedText.text = "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted" // Reset total votes text
                binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null)) // Reset text color

            }
        }

        // Disable vote buttons if not active or user has voted or not a member
        if (!poll.isActive || poll.hasUserVoted || !isUserMember) {
            binding.btnVoteYes.isEnabled = false
            binding.btnVoteNo.isEnabled = false
            binding.btnVoteYes.alpha = 0.5f
            binding.btnVoteNo.alpha = 0.5f
        } else {
            binding.btnVoteYes.isEnabled = true
            binding.btnVoteNo.isEnabled = true
            binding.btnVoteYes.alpha = 1.0f
            binding.btnVoteNo.alpha = 1.0f
        }
    }



    // Add the triggerRewardTransfer function
//    TODO: still in draft mode
    private fun triggerRewardTransfer(poll: nl.tudelft.trustchain.p2playstore.blockdata.VotingPoll) {
        lifecycleScope.launch {
            try {
                val daoUniqueId = daoData.SW_UNIQUE_ID
                val solution = featureSolution ?: run {
                    android.util.Log.e("FeatureVotingFragment", "Cannot trigger reward transfer: Feature solution is null.")
                    Toast.makeText(context, "Error: Solution data missing.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Fetch the latest DAO block state (should be a SWJoinBlockTD or SWTransferDoneBlockTD)
                val latestDaoWalletBlock = withContext(Dispatchers.IO) {
                    p2playStore.fetchLatestSharedWalletBlock(daoBlock.calculateHash()) // Fetch the latest block for this DAO's wallet
                } ?: run {
                    android.util.Log.e("FeatureVotingFragment", "Cannot trigger reward transfer: Latest DAO wallet block not found.")
                    Toast.makeText(context, "Error: Could not find latest DAO wallet state.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Fetch the votes for this specific solution again to ensure they are up-to-date
                val currentVotesForSolution = withContext(Dispatchers.IO) {
                    p2playStore.getVotesForSolution(solution.solutionId)
                }

                // Re-check if approval threshold is still met with fresh data
                val yesVotesCount = currentVotesForSolution.count { it.isYes }
                val totalMembers = daoData.SW_TRUSTCHAIN_PKS.size
                val votesNeeded = daoData.SW_VOTING_THRESHOLD

                if (yesVotesCount < votesNeeded) {
                    Toast.makeText(context, "Reward cannot be transferred yet. Approval threshold not met.", Toast.LENGTH_SHORT).show()
                    // Refresh UI in case votes changed while user was on screen
                    loadDaoBlockAndSolution(daoBlock.blockId, solution.solutionId)
                    return@launch
                }


                // Fetch the original feature request to get the reward amount
                val featureRequests = withContext(Dispatchers.IO) {
                    p2playStore.getFeatureRequestsForDao(daoUniqueId)
                }
                val correspondingRequest = featureRequests.find { it.featureId == solution.featureId } ?: run {
                    android.util.Log.e("FeatureVotingFragment", "Cannot trigger reward transfer: Original feature request not found.")
                    Toast.makeText(context, "Error: Original feature request data missing.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Get the developer's public key (Bitcoin address needed for transfer)
                val developerPublicKey = solution.developerPublicKey // This is the TrustChain PK, need Bitcoin PK or Address


                // TODO: IMPORTANT - The `transferFunds` method in `currencyii` requires a Bitcoin address,
                // not a TrustChain public key.  TrustChain PK
                val developerBitcoinAddress = "REPLACE_WITH_DEVELOPER_BITCOIN_ADDRESS"

                if (developerBitcoinAddress == "REPLACE_WITH_DEVELOPER_BITCOIN_ADDRESS") {
                    Toast.makeText(context, "Error: Developer's Bitcoin address not available.", Toast.LENGTH_LONG).show()
                    android.util.Log.e("FeatureVotingFragment", "Developer Bitcoin address is a placeholder!")
                    return@launch
                }


                val latestDAOBlockForTransferProposal = withContext(Dispatchers.IO) {
                    // Assuming fetchLatestSharedWalletBlock gets the latest state regardless of type
                    p2playStore.fetchLatestSharedWalletBlock(daoData.SW_UNIQUE_ID.hexToBytes()) // Fetch by unique ID might be better
                } ?: run {
                    android.util.Log.e("FeatureVotingFragment", "Cannot trigger reward transfer proposal: Latest DAO block state not found.")
                    Toast.makeText(context, "Error: Could not find latest DAO state for transfer.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Create the transfer proposal
                val transferProposalAskBlockData = withContext(Dispatchers.IO) {
                    try {
                        p2playStore.proposeTransferFunds(
                            latestDAOBlockForTransferProposal,
                            developerBitcoinAddress,
                            correspondingRequest.reward
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("FeatureVotingFragment", "Error creating transfer proposal: ${e.message}")
                        Toast.makeText(context, "Error creating reward transfer proposal.", Toast.LENGTH_SHORT).show()
                        null
                    }
                } ?: return@launch


                android.util.Log.i("FeatureVotingFragment", "FEATURE APPROVED! Initiator clicked to trigger reward transfer.")
                android.util.Log.i("FeatureVotingFragment", "DAO: ${daoUniqueId}, Solution: ${solution.solutionId}, Reward: ${correspondingRequest.reward} sats to Developer: ${solution.developerPublicKey}")

                Toast.makeText(context, "Reward transfer triggered (requires DAO transfer process completion).", Toast.LENGTH_LONG).show()

                loadDaoBlockAndSolution(daoBlock.blockId, solution.solutionId)


            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error triggering reward transfer: ${e.message}")
                Toast.makeText(context, "Error triggering reward transfer.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProgressBars(yesPercent: Int, noPercent: Int, pendingPercent: Int) {
        binding.root.post {
            val containerWidth = binding.root.width
            if (containerWidth > 0) {
                val availableWidth = containerWidth - (resources.getDimensionPixelSize(nl.tudelft.trustchain.p2playstore.R.dimen.progress_bar_margin_horizontal) * 2) // Account for horizontal margins

                if (availableWidth > 0) {
                    // Ensure minimum width to be visible
                    val yesWidth = maxOf(1, (availableWidth * yesPercent / 100))
                    val noWidth = maxOf(1, (availableWidth * noPercent / 100))
                    val pendingWidth = maxOf(1, (availableWidth * pendingPercent / 100))


                    binding.yesProgress.layoutParams.width = yesWidth
                    binding.noProgress.layoutParams.width = noWidth
                    binding.pendingProgress.layoutParams.width = pendingWidth

                    binding.yesProgress.requestLayout()
                    binding.noProgress.requestLayout()
                    binding.pendingProgress.requestLayout()
                }
            }
        }
    }


    private fun setupClickListeners() {
        binding.btnDownloadApk.setOnClickListener {
            downloadApk()
        }

        binding.btnVoteYes.setOnClickListener {
            submitVote(true)
        }

        binding.btnVoteNo.setOnClickListener {
            submitVote(false)
        }
    }

    private fun downloadApk() {
        try {
            val magnetLink = featureSolution?.apkMagnetLink
            if (!magnetLink.isNullOrEmpty()) {
                android.util.Log.d("FeatureVotingFragment", "Downloading APK from: $magnetLink")
                // TODO: Implement actual APK download using the magnet link
            } else {
                android.util.Log.w("FeatureVotingFragment", "No magnet link available for this solution.")
            }
        } catch (e: Exception) {
            android.util.Log.e("FeatureVotingFragment", "Error attempting to download APK: ${e.message}")
        }
    }

    // Update submitVote to use the correct featureId/solutionId and potentially trigger join
    private fun submitVote(isYes: Boolean) {
        lifecycleScope.launch { // Use lifecycleScope for coroutine
            try {
                // Declare as var so they can be assigned later
                var targetFeatureId: String? = null
                var targetSolutionId: String = "" // Initialize with empty string

                if (isJoinRequest) {
                    val joinRequest = joinRequestFeature ?: run {
                        android.util.Log.e("FeatureVotingFragment", "Cannot submit vote: Join request feature is null.")
                        Toast.makeText(context, "Error: Join request data missing.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    targetFeatureId = joinRequest.featureId
                    targetSolutionId = joinRequest.featureId // For join requests, use featureId as solutionId in the vote block
                } else {
                    val solution = featureSolution ?: run {
                        android.util.Log.e("FeatureVotingFragment", "Cannot submit vote: Feature solution is null.")
                        Toast.makeText(context, "Error: Solution data missing.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    targetFeatureId = solution.featureId
                    targetSolutionId = solution.solutionId // This is a non-nullable String from FeatureSolutionTD
                }

                // Add a check to ensure targetFeatureId is not null before proceeding
                if (targetFeatureId == null) {
                    android.util.Log.e("FeatureVotingFragment", "Cannot submit vote: targetFeatureId is null.")
                    Toast.makeText(context, "Error submitting vote: Invalid feature information.", Toast.LENGTH_SHORT).show()
                    return@launch
                }


                if (daoUniqueId.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "Cannot submit vote: daoUniqueId is empty.")
                    Toast.makeText(context, "Error submitting vote: Missing DAO information.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Use the community method to create the vote block
                p2playStore.createFeatureVote(
                    daoId = daoUniqueId, // Use the retrieved DAO unique ID
                    featureId = targetFeatureId, // Pass non-nullable targetFeatureId
                    solutionId = targetSolutionId, // Use targetSolutionId (which is featureId for join requests)
                    isYes = isYes
                )

                android.util.Log.d("FeatureVotingFragment", "Vote submitted: ${if (isYes) "Yes" else "No"} for ${if(isJoinRequest) "Join Request Feature" else "Solution"} $targetFeatureId${if(!isJoinRequest) " (Solution $targetSolutionId)" else ""} in DAO ${daoUniqueId}")

                if (context != null) {
                    Toast.makeText(context, "Vote submitted successfully!", Toast.LENGTH_SHORT).show()
                }
                // Reload data based on whether it's a join request or standard feature
                if (isJoinRequest) {
                    // When reloading for a join request, use the featureId
                    loadDaoBlockAndJoinRequest(daoBlock.blockId, targetFeatureId)
                } else {
                    // When reloading for a standard feature solution, use the solutionId
                    loadDaoBlockAndSolution(daoBlock.blockId, targetSolutionId) // solutionId is guaranteed not null here by the 'else' block
                }


            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error submitting vote: ${e.message}")
                // Show an error message
                if (context != null) {
                    Toast.makeText(context, "Error submitting vote.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
