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
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureVotingBinding
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VotingPoll
import nl.tudelft.trustchain.p2playstore.transactionData.VotingPollType

class FeatureVotingFragment : BaseFragment() {
    private var _binding: FragmentFeatureVotingBinding? = null
    private val binding get() = _binding!!

    private lateinit var daoBlockId: String
    private lateinit var proposalId: String
    private lateinit var daoUniqueId: String

    private var votingPoll: VotingPoll? = null

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

        daoBlockId = arguments?.getString("blockId") ?: ""
        proposalId = arguments?.getString("solutionId") ?: ""
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""

        if (daoBlockId.isNotEmpty() && proposalId.isNotEmpty() && daoUniqueId.isNotEmpty()) {
            loadVotingPoll()
            setupClickListeners()
        } else {
            android.util.Log.e("FeatureVotingFragment", "Missing DAO block ID, Proposal ID, or DAO Unique ID")
            Toast.makeText(context, "Error: Missing voting information.", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun loadVotingPoll() {
        lifecycleScope.launch {
            try {
                if (daoUniqueId.isEmpty() || proposalId.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "daoUniqueId or proposalId is empty. Cannot load voting data.")
                    setupFallbackUI("Error: Missing DAO or Proposal ID.")
                    return@launch
                }

                // Get the voting poll using the community method
                val poll = withContext(Dispatchers.IO) {
                    p2playStore.getVotingPoll(daoUniqueId, proposalId)
                }

                if (poll != null) {
                    votingPoll = poll
                    updateVotingUIWithPoll(poll)
                } else {
                    android.util.Log.e("FeatureVotingFragment", "Voting poll not found for DAO $daoUniqueId and proposal $proposalId")
                    setupFallbackUI("Voting poll not found.")
                }

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error loading voting poll: ${e.message}")
                setupFallbackUI("Error loading voting data: ${e.message}")
            }
        }
    }

    private fun updateVotingUIWithPoll(poll: VotingPoll) {
        // Use data from the unified VotingPoll object
        binding.originalFeatureTitle.text = poll.title
        binding.implementationDescription.text = poll.description

        // Display reward only for feature solutions
        if (poll.type == VotingPollType.FEATURE_SOLUTION) {
            val reward = poll.metadata["featureReward"] as? Long ?: 0L
            binding.rewardAmount.text = "$reward sats"
            binding.rewardAmount.visibility = View.VISIBLE
        } else {
            binding.rewardAmount.visibility = View.GONE
        }

        // Display developer only for feature solutions
        if (poll.type == VotingPollType.FEATURE_SOLUTION) {
            val developerPk = poll.metadata["developerPublicKey"] as? String ?: "Unknown"
            binding.developerName.text = developerPk.take(8) + "..."
            binding.developerName.visibility = View.VISIBLE
        } else {
            binding.developerName.visibility = View.GONE
        }

        binding.percentageYes.text = "${poll.yesPercentage}%"
        binding.percentageNo.text = "${poll.noPercentage}%"
        binding.percentagePending.text = "${poll.pendingPercentage}%"

        binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed for approval"

        updateProgressBars(poll.yesPercentage, poll.noPercentage, poll.pendingPercentage)
        updateVotingState(poll)
    }


    private fun setupFallbackUI(message: String = "Voting poll not found or could not be loaded.") {
        binding.originalFeatureTitle.text = "Information Unavailable"
        binding.implementationDescription.text = message
        binding.developerName.visibility = View.GONE
        binding.rewardAmount.visibility = View.GONE


        binding.percentageYes.text = "0%"
        binding.percentageNo.text = "0%"
        binding.percentagePending.text = "100%"

        updateProgressBars(0, 0, 100)
        binding.votesRequiredText.text = "N/A votes needed"
        binding.alreadyVotedText.text = "N/A members voted"

        // Hide voting buttons and claim reward button in fallback
        binding.votingButtonsLayout.visibility = View.GONE
        binding.btnClaimReward.visibility = View.GONE
        binding.btnDownloadApk.visibility = View.GONE
    }

    private fun updateVotingState(poll: VotingPoll) {
        // Fetch DAO data to check if user is a member
        lifecycleScope.launch {
            try {
                val daoBlock = withContext(Dispatchers.IO) {
                    p2playStore.getDaoBlock(daoBlockId)
                }
                val isUserMember = if (daoBlock != null) {
                    SWJoinBlockTransactionData(daoBlock.transaction).getData().SW_TRUSTCHAIN_PKS.contains(p2playStore.myPeer.publicKey.keyToBin().toHex())
                } else {
                    false
                }

                // Access myPeer from the p2playStore instance
                val isDaoInitiator = daoBlock?.publicKey?.toHex() == p2playStore.myPeer.publicKey.keyToBin().toHex() // Assuming DAO initiator is the block creator


                binding.votingButtonsLayout.visibility = View.GONE
                binding.btnClaimReward.visibility = View.GONE
                binding.votesRequiredText.visibility = View.VISIBLE
                binding.alreadyVotedText.visibility = View.VISIBLE
                binding.btnDownloadApk.visibility = View.GONE


                when {
                    // Voting closed and approved (enough YES votes)
                    !poll.isActive && poll.yesVotes >= poll.votesNeeded -> {
                        binding.alreadyVotedText.text = "Approved" // Use the total votes TextView to show final status
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                        binding.votesRequiredText.visibility = View.GONE // Hide required text when decided

                        // If approved and this is a feature solution, show download APK button
                        if (poll.type == VotingPollType.FEATURE_SOLUTION) {
                            binding.btnDownloadApk.visibility = View.VISIBLE
                            binding.btnDownloadApk.isEnabled = true
                            binding.btnDownloadApk.alpha = 1.0f
                        }

                        // If approved, this is a feature solution, and I am the developer, show claim button
                        if (poll.type == VotingPollType.FEATURE_SOLUTION && poll.metadata["developerPublicKey"] == p2playStore.myPeer.publicKey.pub().toString()) {
                            binding.btnClaimReward.visibility = View.VISIBLE

                            // The DAO initiator is responsible for triggering the transfer
                            if (isDaoInitiator) {
                                binding.btnClaimReward.text = "Transfer Reward"
                                binding.btnClaimReward.setOnClickListener {
                                    triggerRewardTransfer(poll)
                                }
                                binding.btnClaimReward.isEnabled = true
                                binding.btnClaimReward.alpha = 1.0f
                            } else {
                                // Developer is a member but not initiator, they can see it's approved
                                binding.btnClaimReward.text = "Approved - Reward Pending"
                                binding.btnClaimReward.isEnabled = false
                                binding.btnClaimReward.alpha = 0.5f
                            }
                        }
                    }
                    // Voting closed and not approved
                    !poll.isActive && poll.yesVotes < poll.votesNeeded -> {
                        binding.alreadyVotedText.text = "Voting Closed - Not Approved" // Use the total votes TextView
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

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error updating voting state: ${e.message}")
                // In case of error, hide interactive elements
                binding.votingButtonsLayout.visibility = View.GONE
                binding.btnClaimReward.visibility = View.GONE
                binding.btnDownloadApk.visibility = View.GONE
                binding.votesRequiredText.visibility = View.VISIBLE
                binding.alreadyVotedText.visibility = View.VISIBLE
                binding.alreadyVotedText.text = "Error loading state."
                binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            }
        }
    }

    private fun triggerRewardTransfer(poll: VotingPoll) {
        lifecycleScope.launch {
            try {
                val daoUniqueId = poll.daoId
                val proposalId = poll.id // The proposal ID of the solution block
                val rewardAmount = poll.metadata["featureReward"] as? Long ?: 0L
                val developerPublicKey = poll.metadata["developerPublicKey"] as? String ?: ""

                if (rewardAmount <= 0 || developerPublicKey.isEmpty()) {
                    Toast.makeText(context, "Error: Invalid reward or developer info.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // The transferFunds method requires the developer's Bitcoin address.
                // Currently, we only have the TrustChain public key.
                // TODO: Implement a mechanism to get the developer's Bitcoin address from their TrustChain PK
                // For now, using a placeholder, this will cause the transfer to fail.
                val developerBitcoinAddress = "mty7WcvBbEYXKuwW86KJwatpMXcm7NMitX"

                if (developerBitcoinAddress == "mty7WcvBbEYXKuwW86KJwatpMXcm7NMitX") {
                    Toast.makeText(context, "Error: Developer's Bitcoin address not available.", Toast.LENGTH_LONG).show()
                    android.util.Log.e("FeatureVotingFragment", "Developer Bitcoin address is a placeholder!")
                    // Reload to show current state after failed attempt
                    loadVotingPoll()
                    return@launch
                }

                // First, find the latest DAO block (JOIN_BLOCK or UPDATE_ACCEPTED_BLOCK) for this DAO
                val latestDaoWalletBlock = withContext(Dispatchers.IO) {
                    p2playStore.fetchLatestSharedWalletBlockByDaoId(daoUniqueId)
                } ?: run {
                    android.util.Log.e("FeatureVotingFragment", "Cannot trigger reward transfer: Latest DAO wallet block not found for DAO $daoUniqueId.")
                    Toast.makeText(context, "Error: Could not find latest DAO wallet state.", Toast.LENGTH_SHORT).show()
                    // Reload to show current state after failed attempt
                    loadVotingPoll()
                    return@launch
                }

                // Now, call proposeTransferFunds to initiate the signature collection for the reward transfer
                withContext(Dispatchers.IO) {
                    try {
                        // This will create PROPOSE_UPDATE_BLOCKs for other members to sign
                        p2playStore.proposeTransferFunds(
                            latestDaoWalletBlock,
                            developerBitcoinAddress, // Target address for reward
                            rewardAmount
                        )
                        android.util.Log.i("FeatureVotingFragment", "Reward transfer proposal initiated for DAO $daoUniqueId, amount $rewardAmount to $developerBitcoinAddress.")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Reward transfer proposal initiated. Members need to vote YES.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FeatureVotingFragment", "Error initiating reward transfer proposal: ${e.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error initiating reward transfer proposal: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Reload the poll state after initiating the transfer proposal
                loadVotingPoll()


            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error triggering reward transfer: ${e.message}")
                Toast.makeText(context, "Error triggering reward transfer.", Toast.LENGTH_SHORT).show()
                // Reload to show current state after failed attempt
                loadVotingPoll()
            }
        }
    }

    private fun updateProgressBars(yesPercent: Int, noPercent: Int, pendingPercent: Int) {
        binding.root.post {
            val containerWidth = binding.root.width
            if (containerWidth > 0) {
                val availableWidth = containerWidth - (resources.getDimensionPixelSize(nl.tudelft.trustchain.p2playstore.R.dimen.progress_bar_margin_horizontal) * 2)

                if (availableWidth > 0) {
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
            if (votingPoll?.type == VotingPollType.FEATURE_SOLUTION) {
                val magnetLink = votingPoll?.metadata?.get("apkMagnetLink") as? String
                if (!magnetLink.isNullOrEmpty()) {
                    android.util.Log.d("FeatureVotingFragment", "Downloading APK from: $magnetLink")
                    // TODO: Implement actual APK download using the magnet link and TorrentManager
                    // This would likely involve calling a download method on the TorrentManager instance
                    // available from the Activity or a shared ViewModel.
                    // Example (requires TorrentManager instance):
                    // (activity as? P2PlayStoreMainActivity)?.torrentManager?.downloadApp(app_object_representing_this_solution)
                    // You would need to pass or create a P2playApp object from the solution data if TorrentManager expects it.
                    Toast.makeText(context, "APK download initiated (functionality needs implementation).", Toast.LENGTH_SHORT).show()

                } else {
                    android.util.Log.w("FeatureVotingFragment", "No magnet link available for this solution.")
                    Toast.makeText(context, "No APK magnet link available for this solution.", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.w("FeatureVotingFragment", "Download APK button clicked for non-feature solution poll.")
            }
        }

        binding.btnVoteYes.setOnClickListener {
            submitVote(true)
        }

        binding.btnVoteNo.setOnClickListener {
            submitVote(false)
        }
    }

    private fun submitVote(isYes: Boolean) {
        lifecycleScope.launch {
            try {
                val currentPoll = votingPoll ?: run {
                    android.util.Log.e("FeatureVotingFragment", "Cannot submit vote: Voting poll is null.")
                    Toast.makeText(context, "Error: Poll data missing.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (daoUniqueId.isEmpty() || currentPoll.id.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "Cannot submit vote: daoUniqueId or poll ID is empty.")
                    Toast.makeText(context, "Error submitting vote: Missing DAO or poll information.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Use the community method to create the vote block
                p2playStore.voteOnProposal(
                    daoId = daoUniqueId,
                    proposalId = currentPoll.id,
                    isYes = isYes,
                    context = requireContext()
                )

                android.util.Log.d("FeatureVotingFragment", "Vote submitted: ${if (isYes) "Yes" else "No"} for Proposal ${currentPoll.id} in DAO ${daoUniqueId}")

                Toast.makeText(context, "Vote submitted successfully!", Toast.LENGTH_SHORT).show()
                // Reload the poll state after voting
                loadVotingPoll()

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "Error submitting vote: ${e.message}")
                Toast.makeText(context, "Error submitting vote: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload poll data when the fragment is resumed
        loadVotingPoll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
