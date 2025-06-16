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
import nl.tudelft.ipv8.util.*
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureVotingBinding
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData

import nl.tudelft.trustchain.p2playstore.transactionData.VotingPoll
import nl.tudelft.trustchain.p2playstore.transactionData.VotingPollType
import org.bitcoinj.core.Address
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDoaData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedData
import nl.tudelft.trustchain.p2playstore.utils.BlockUtils
import nl.tudelft.trustchain.p2playstore.transactionData.AppMetaData
import nl.tudelft.trustchain.currencyii.util.taproot.CTransaction
import nl.tudelft.trustchain.p2playstore.utils.AppUtils
import nl.tudelft.ipv8.attestation.trustchain.BlockListener

class FeatureVotingFragment : BaseFragment() {
    private var _binding: FragmentFeatureVotingBinding? = null
    private val binding get() = _binding!!

    private lateinit var daoBlockId: String
    private lateinit var proposalId: String
    private lateinit var daoUniqueId: String

    private var votingPoll: VotingPoll? = null
    private var isTransferInitiated: Boolean = false
    private var voteBlockListener: BlockListener? = null

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
        android.util.Log.d("FeatureVotingFragment", "onViewCreated")

        daoBlockId = arguments?.getString("blockId") ?: ""
        proposalId = arguments?.getString("solutionId") ?: ""
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""

        if (daoBlockId.isNotEmpty() && proposalId.isNotEmpty() && daoUniqueId.isNotEmpty()) {
            setupVoteBlockListener()
            loadVotingPoll() // Initial load
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
                // Add a small delay here to allow the TrustChain block to be processed
                android.util.Log.d("FeatureVotingFragment", "loadVotingPoll: Delaying for 500ms...")
                kotlinx.coroutines.delay(500) // Added delay at the start of loadVotingPoll
                android.util.Log.d("FeatureVotingFragment", "loadVotingPoll: Delay finished. Loading poll data...")

                if (daoUniqueId.isEmpty() || proposalId.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "daoUniqueId or proposalId is empty. Cannot load voting data.")
                    setupFallbackUI("Error: Missing DAO or Proposal ID.")
                    return@launch
                }

                android.util.Log.d("FeatureVotingFragment", "loadVotingPoll: Calling getVotingPoll(daoId=$daoUniqueId, proposalId=$proposalId)")
                // Get the voting poll using the community method
                val poll = withContext(Dispatchers.IO) {
                    p2playStore.getVotingPoll(daoUniqueId, proposalId)
                }

                if (poll != null) {
                    android.util.Log.d("FeatureVotingFragment", "loadVotingPoll: Voting poll found. ID: ${poll.id}, YesVotes: ${poll.yesVotes}, NoVotes: ${poll.noVotes}, IsActive: ${poll.isActive}, HasUserVoted: ${poll.hasUserVoted}")
                    votingPoll = poll
                    withContext(Dispatchers.Main) {
                        updateVotingUIWithPoll(poll) // Update UI with confirmed state
                    }
                } else {
                    android.util.Log.e("FeatureVotingFragment", "loadVotingPoll: Voting poll not found for DAO $daoUniqueId and proposal $proposalId. Setting up fallback UI.")
                    setupFallbackUI("Voting poll not found.")
                }

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "loadVotingPoll: Error loading voting poll: ${e.message}", e)
                setupFallbackUI("Error loading voting data: ${e.message}")
            }
            android.util.Log.d("FeatureVotingFragment", "loadVotingPoll finished")
        }
    }

    private fun updateVotingUIWithPoll(poll: VotingPoll) {
        android.util.Log.d("FeatureVotingFragment", "updateVotingUIWithPoll called for poll ID: ${poll.id}")
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

        binding.yesPercentage.text = "${poll.yesPercentage}%"
        binding.noPercentage.text = "${poll.noPercentage}%"
        binding.pendingPercentage.text = "${poll.pendingPercentage}%"

        binding.votesRequiredText.text = "${poll.yesVotes} of ${poll.votesNeeded} votes needed for approval"
        android.util.Log.d("FeatureVotingFragment", "updateVotingUIWithPoll: Percentages (Y/N/P): ${poll.yesPercentage}%/${poll.noPercentage}%/${poll.pendingPercentage}%. Votes (Y/N/Req): ${poll.yesVotes}/${poll.noVotes}/${poll.votesNeeded}")
        AppUtils.updateProgressBars(
            binding.root,
            binding.yesProgressBar,
            binding.noProgressBar,
            binding.pendingProgressBar,
            poll.yesPercentage,
            poll.noPercentage,
            poll.pendingPercentage
        )
        android.util.Log.d("FeatureVotingFragment", "updateVotingUIWithPoll: Progress bars updated.")
        updateVotingState(poll)
        android.util.Log.d("FeatureVotingFragment", "updateVotingUIWithPoll finished")
    }


    private fun setupFallbackUI(message: String = "Voting poll not found or could not be loaded.") {
        android.util.Log.d("FeatureVotingFragment", "setupFallbackUI called with message: $message")
        binding.originalFeatureTitle.text = "Information Unavailable"
        binding.implementationDescription.text = message
        binding.developerName.visibility = View.GONE
        binding.rewardAmount.visibility = View.GONE


        binding.yesPercentage.text = "0%"
        binding.noPercentage.text = "0%"
        binding.pendingPercentage.text = "100%"
        AppUtils.updateProgressBars(
            binding.root,
            binding.yesProgressBar,
            binding.noProgressBar,
            binding.pendingProgressBar,
            0,
            0,
            100
        )
        binding.votesRequiredText.text = "N/A votes needed"
        binding.alreadyVotedText.text = "N/A members voted"

        // Hide voting buttons and claim reward button in fallback
        binding.votingButtonsLayout.visibility = View.GONE
//        binding.btnClaimReward.visibility = View.GONE
        binding.btnDownloadApk.visibility = View.GONE
        android.util.Log.d("FeatureVotingFragment", "setupFallbackUI finished")
    }

    private fun updateVotingState(poll: VotingPoll) {
        android.util.Log.d("FeatureVotingFragment", "updateVotingState called for poll ID: ${poll.id}")
        // Fetch DAO data to check if user is a member and initiator
        lifecycleScope.launch {
            try {
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Fetching latest DAO block for DAO ID: ${poll.daoId}")
                val latestDaoBlock = withContext(Dispatchers.IO) {
                    p2playStore.fetchLatestSharedWalletBlockByDaoId(poll.daoId)
                }
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Latest DAO block fetched: ${latestDaoBlock?.blockId}")


                val latestDaoData: AppMetaData? = if (latestDaoBlock != null) {
                    when(latestDaoBlock.type) {
                        P2pStoreCommunity.JOIN_BLOCK -> JoinDaoTransactionData(latestDaoBlock.transaction).getData()
                        P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(latestDaoBlock.transaction).getData()
                        else -> null
                    }
                } else {
                    null
                }
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Latest DAO data extracted.")


                val myPublicKeyHex = p2playStore.myPeer.publicKey.keyToBin().toHex()
                val isUserMember = if (latestDaoData != null) {
                    // Check trustchain PKs from the latest DAO state
                    when(latestDaoData) {
                        is JoinDoaData -> latestDaoData.SW_TRUSTCHAIN_PKS.contains(myPublicKeyHex)
                        is UpdateAcceptedData -> latestDaoData.SW_TRUSTCHAIN_PKS.contains(myPublicKeyHex)
                        else -> false
                    }
                } else {
                    false
                }
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Is user a DAO member? $isUserMember")

                // The initiator is the creator of the *initial* DAO block (JOIN_BLOCK)
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Fetching initial DAO block for DAO ID: ${poll.daoId}")
                val initialDaoBlock = withContext(Dispatchers.IO) {
                    // Fetch all JOIN blocks for this DAO to find the very first one
                    getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.JOIN_BLOCK)
                        .filter { block ->
                            try { JoinDaoTransactionData(block.transaction).getData().DAO_ID == poll.daoId } catch (e: Exception) { false }
                        }
                        .sortedBy { it.sequenceNumber } // Sort by sequence number to find the genesis
                }.firstOrNull()
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Initial DAO block fetched: ${initialDaoBlock?.blockId}")

                val isDaoInitiator = initialDaoBlock?.publicKey?.toHex() == myPublicKeyHex
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Is user the DAO initiator? $isDaoInitiator")


                // Check if the current user is the developer of this solution
                val developerPublicKey = poll.metadata["developerPublicKey"] as? String
                val isUserDeveloper = if (developerPublicKey != null) {
                    p2playStore.myPeer.publicKey.pub().toString() == developerPublicKey
                } else {
                    false
                }
                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Is user the developer of this solution? $isUserDeveloper")

                binding.votingButtonsLayout.visibility = View.GONE
//                binding.btnClaimReward.visibility = View.GONE
                binding.votesRequiredText.visibility = View.VISIBLE
                binding.alreadyVotedText.visibility = View.VISIBLE
//                binding.btnDownloadApk.visibility = View.GONE


                when {
                    // Voting closed and approved (enough YES votes)
                    poll.isApproved -> { // Use the isApproved property from VotingPoll
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Poll is Approved.")
                        binding.alreadyVotedText.text = "Approved"
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                        binding.votesRequiredText.visibility = View.GONE

                    }
                    // Voting closed and not approved
                    !poll.isActive && poll.yesVotes < poll.votesNeeded -> {
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Voting Closed - Not Approved.")
                        binding.alreadyVotedText.text = "Voting Closed - Not Approved" // Use the total votes TextView
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                        binding.votesRequiredText.visibility = View.GONE // Hide required text when decided
                    }
                    // Check if the user has voted based on the poll data (confirmed on-chain)
                    poll.hasUserVoted -> {
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Voting Active/Closed, User has voted (confirmed).")
                        binding.alreadyVotedText.text = "✓ You voted ${if (poll.userVote == true) "Yes" else "No"}"
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                        binding.alreadyVotedText.visibility = View.VISIBLE
                        binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
                    }
                    isUserMember && poll.isActive -> { // Voting active, user is member, user has NOT voted
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Voting Active, User is member, has NOT voted.")
                        if (!isUserDeveloper) { // Only show vote buttons if not the developer
                            binding.votingButtonsLayout.visibility = View.VISIBLE // Show vote buttons
                        } else {
                            android.util.Log.d("FeatureVotingFragment", "updateVotingState: User is developer, hiding vote buttons.")
                        }
                        binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
                        binding.alreadyVotedText.text = "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted" // Reset total votes text if user hasn't voted yet
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null)) // Reset text color
                        binding.alreadyVotedText.visibility = View.VISIBLE

                    }
                    isUserMember && !poll.isActive && !poll.hasUserVoted -> {
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Voting is closed, user is member, hasn't voted before closure.")
                        /* Voting is closed, user is member, hasn't voted before closure */
                        binding.alreadyVotedText.text = "Voting Closed" // Use the total votes TextView
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                        binding.votesRequiredText.visibility = View.GONE // Hide required text when decided
                        binding.alreadyVotedText.visibility = View.VISIBLE
                    }
                    else -> {
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: User is not a member.")
                        // User is not a member, voting is active, they cannot vote.
                        // Buttons remain hidden by default.
                        binding.votesRequiredText.visibility = View.VISIBLE // Keep required text visible
                        binding.alreadyVotedText.text = "${poll.yesVotes + poll.noVotes} of ${poll.totalMembers} members voted" // Reset total votes text
                        binding.alreadyVotedText.setTextColor(resources.getColor(android.R.color.darker_gray, null)) // Reset text color
                        binding.alreadyVotedText.visibility = View.VISIBLE
                    }
                }

                android.util.Log.d("FeatureVotingFragment", "updateVotingState: Final check for vote button enabled state. Poll isActive=${poll.isActive}, hasUserVoted (Poll)=${poll.hasUserVoted}, isUserMember=${isUserMember}, isUserDeveloper=${isUserDeveloper}")
                // Disable vote buttons if not active OR user has voted (locally or on poll) OR not a member OR is the developer
                if (!poll.isActive || !isUserMember || isUserDeveloper) {
                    binding.btnVoteYes.isEnabled = false
                    binding.btnVoteNo.isEnabled = false
                    binding.btnVoteYes.alpha = 0.5f
                    binding.btnVoteNo.alpha = 0.5f
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: Vote buttons disabled.")
                } else {
                    binding.btnVoteYes.isEnabled = true
                    binding.btnVoteNo.isEnabled = true
                    binding.btnVoteYes.alpha = 1.0f
                    binding.btnVoteNo.alpha = 1.0f
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: Vote buttons enabled.")
                }

                // Adjusted Download APK button visibility: Show if it's a solution poll and the user is a member, and the poll is active or approved
                if (poll.type == VotingPollType.FEATURE_SOLUTION && isUserMember) {
                    if (poll.isActive || poll.isApproved) { // Show if active OR approved
                        binding.btnDownloadApk.visibility = View.VISIBLE
                        binding.btnDownloadApk.isEnabled = true // Always enabled if visible in these states
                        binding.btnDownloadApk.alpha = 1.0f
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Download APK button visible and enabled.")
                    } else {
                        binding.btnDownloadApk.visibility = View.GONE
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Download APK button hidden (poll not active or approved).")
                    }
                } else {
                    binding.btnDownloadApk.visibility = View.GONE
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: Download APK button hidden (not feature solution or not member).")
                }


                // Automatically trigger reward transfer if approved and not already triggered
                if (poll.isApproved && poll.type == VotingPollType.FEATURE_SOLUTION) { // Use isApproved
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: Poll is approved and is a Feature Solution.")
                    // Check if the transfer has already been recorded on chain.
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: Checking for existing UPDATE_ACCEPTED_BLOCK for proposal ${poll.id}")
                    val transferDoneBlockExists = withContext(Dispatchers.IO) {
                        getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK).any {
                            try { UpdateAcceptedTransactionData(it.transaction).getData().SW_UNIQUE_PROPOSAL_ID == poll.id } catch (e: Exception) { false }
                        }
                    }
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: UPDATE_ACCEPTED_BLOCK found? $transferDoneBlockExists. isTransferInitiated flag: $isTransferInitiated")

                    // --- Add check for the state flag ---
                    if (!transferDoneBlockExists && !isTransferInitiated) {
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Poll approved, UPDATE_ACCEPTED_BLOCK not found, and transfer not yet initiated. Triggering reward transfer...")
                        isTransferInitiated = true // Set the flag
                        triggerRewardTransfer(poll)
                    } else if (transferDoneBlockExists) {
                        android.util.Log.d("FeatureVotingFragment", "updateVotingState: Poll approved but UPDATE_ACCEPTED_BLOCK already found. Reward transfer already triggered.")
                        isTransferInitiated = true // Ensure flag is set if block exists
                    } else if (isTransferInitiated) {
                        android.util.Log.d(
                            "FeatureVotingFragment",
                            "updateVotingState: Poll approved but transfer already initiated. Waiting for UPDATE_ACCEPTED_BLOCK."
                        )
                    }
                } else {
                    android.util.Log.d("FeatureVotingFragment", "updateVotingState: Poll not approved OR not a feature solution. Reward transfer logic skipped.")
                }


            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "updateVotingState: Error updating voting state: ${e.message}", e)
                // In case of error, hide interactive elements
                binding.votingButtonsLayout.visibility = View.GONE
//                binding.btnClaimReward.visibility = View.GONE
                binding.btnDownloadApk.visibility = View.GONE
                binding.votesRequiredText.visibility = View.VISIBLE
                binding.alreadyVotedText.visibility = View.GONE
            }
            android.util.Log.d("FeatureVotingFragment", "updateVotingState finished")
        }
    }

    private fun triggerRewardTransfer(poll: VotingPoll) {
        android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer called for poll ID: ${poll.id}")
        lifecycleScope.launch {
            try {
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Coroutine started.")

                val daoUniqueId = poll.daoId
                val proposalId = poll.id // The proposal ID of the solution block
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: DAO ID=$daoUniqueId, Proposal ID=$proposalId")

                // Need to find the solution proposal data first
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Finding solution proposal block...")
                val solutionProposalBlock = withContext(Dispatchers.IO) {
                    p2playStore.findProposalBlock(daoUniqueId, proposalId)
                } ?: run {
                    android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Cannot trigger reward transfer: Solution proposal block not found for DAO $daoUniqueId and proposal $proposalId.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: Could not find solution proposal data.", Toast.LENGTH_SHORT).show() }
                    loadVotingPoll() // Reload to show current state after failed attempt
                    isTransferInitiated = false // Reset flag on failure
                    return@launch
                }
                val solutionProposalData = ProposeUpdateTransactionData(solutionProposalBlock.transaction).getData()
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Solution proposal block found. Proposal Data: $solutionProposalData")


                val rewardAmount = solutionProposalData.SW_TRANSFER_FUNDS_AMOUNT
                val developerBitcoinAddress = solutionProposalData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Reward Amount: $rewardAmount, Developer Address: $developerBitcoinAddress")


                if (rewardAmount <= 0 || developerBitcoinAddress.isNullOrEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Invalid reward amount ($rewardAmount) or developer address ($developerBitcoinAddress) in solution data.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: Invalid reward or developer address info in solution data.", Toast.LENGTH_SHORT).show() }
                    isTransferInitiated = false // Reset flag on failure
                    return@launch
                }

                // Basic validation for Developer Bitcoin address format
                try {
                    // Use the current network params from WalletManagerAndroid
                    val params = WalletManagerAndroid.getInstance().params
                    Address.fromString(params, developerBitcoinAddress)
                    android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Developer Bitcoin address seems valid: $developerBitcoinAddress")
                } catch (e: Exception) {
                    android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Invalid Developer Bitcoin address format: ${e.message}", e)
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Invalid Developer Bitcoin address format in solution data.", Toast.LENGTH_LONG).show() }
                    loadVotingPoll() // Reload to show current state after failed attempt
                    isTransferInitiated = false // Reset flag on failure
                    return@launch
                }

                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Fetching YES and NO votes for proposal $proposalId")
                val yesVotes = withContext(Dispatchers.IO) {
                    p2playStore.fetchProposalResponses(daoUniqueId, proposalId)
                }

                val noVotes = withContext(Dispatchers.IO) {
                    p2playStore.fetchNegativeProposalResponses(daoUniqueId, proposalId)
                }

                val allVotes = yesVotes + noVotes
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Found ${yesVotes.size} YES votes and ${noVotes.size} NO votes.")


                // We need the total number of DAO members and voting threshold from the latest DAO block.
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Fetching latest JOIN block for DAO $daoUniqueId")
                val latestJoinBlock = withContext(Dispatchers.IO) {
                    getTrustChainCommunity().database.getBlocksWithType(P2pStoreCommunity.JOIN_BLOCK)
                        .filter { block ->
                            try { JoinDaoTransactionData(block.transaction).getData().DAO_ID == daoUniqueId } catch (e: Exception) { false }
                        }
                        .maxByOrNull { it.insertTime!! }
                } ?: run {
                    android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Cannot trigger reward transfer: Latest JOIN block not found for DAO $daoUniqueId.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: Could not find latest DAO join state.", Toast.LENGTH_SHORT).show() }
                    loadVotingPoll()
                    isTransferInitiated = false // Reset flag on failure
                    return@launch
                }

                val daoWalletStateForTransfer: JoinDoaData = JoinDaoTransactionData(latestJoinBlock.transaction).getData()
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Latest JOIN block data for transfer found. Total members: ${daoWalletStateForTransfer.SW_TRUSTCHAIN_PKS.size}, Voting Threshold: ${daoWalletStateForTransfer.SW_VOTING_THRESHOLD}")


                val totalDaoMembers = daoWalletStateForTransfer.SW_TRUSTCHAIN_PKS.size
                val votingThreshold = daoWalletStateForTransfer.SW_VOTING_THRESHOLD // Use threshold from latest JOIN block
                val requiredSignatures = BlockUtils.percentageToIntThreshold(totalDaoMembers, votingThreshold)
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Calculated required signatures: $requiredSignatures")


                if (yesVotes.size < requiredSignatures) { // Use the calculated required signatures based on DAO state
                    android.util.Log.w("FeatureVotingFragment", "triggerRewardTransfer: Insufficient YES votes (${yesVotes.size}). Required: $requiredSignatures.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Reward transfer not yet possible. Need $requiredSignatures YES votes, have ${yesVotes.size}.", Toast.LENGTH_LONG).show()
                    }
                    loadVotingPoll() // Reload to show current state
                    isTransferInitiated = false // Reset flag on failure
                    return@launch
                }

                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Enough YES votes (${yesVotes.size}) collected for proposal ${proposalId}. Required: $requiredSignatures. Checking DAO balance...")

                val currentDaoBalance = withContext(Dispatchers.IO) {
                    try {
                        val latestDaoWalletBlock = p2playStore.fetchLatestSharedWalletBlockByDaoId(daoUniqueId)
                        if (latestDaoWalletBlock != null) {
                            val serializedTx = when (latestDaoWalletBlock.type) {
                                P2pStoreCommunity.JOIN_BLOCK -> JoinDaoTransactionData(latestDaoWalletBlock.transaction).getData().SW_TRANSACTION_SERIALIZED
                                P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(latestDaoWalletBlock.transaction).getData().SW_TRANSACTION_SERIALIZED
                                else -> null
                            }
                            if (serializedTx != null) {
                                CTransaction().deserialize(serializedTx.hexToBytes()).vout.find { it.scriptPubKey.size == 35 }?.nValue
                                    ?: 0L
                            } else {
                                0L // Serialized transaction is null
                            }
                        } else {
                            0L // No latest block found
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "FeatureVotingFragment",
                            "triggerRewardTransfer: Error fetching DAO balance for sufficient funds check: ${e.message}", e
                        )
                        0L // Assume 0 if fetching fails, to prevent transfer
                    }
                }
                android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Current DAO balance: $currentDaoBalance satoshis.")

                if (currentDaoBalance < rewardAmount) {
                    android.util.Log.w(
                        "FeatureVotingFragment",
                        "triggerRewardTransfer: Insufficient funds in DAO wallet ($currentDaoBalance) for reward transfer ($rewardAmount). Transfer aborted."
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "DAO wallet has insufficient funds ($currentDaoBalance satoshis) to pay the reward ($rewardAmount satoshis).",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    loadVotingPoll() // Reload to show current state or some other UI update function
                    isTransferInitiated = false // Reset flag on failure
                    return@launch // Exit the current coroutine launch block
                }

                android.util.Log.d(
                    "FeatureVotingFragment",
                    "triggerRewardTransfer: DAO has sufficient funds. Proceeding with DAO fund transfer."
                )

                // Initiate the DAO fund transfer
                try {
                    android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Starting p2playStore.transferFunds...")

                    val overallLatestDaoBlock = withContext(Dispatchers.IO) {
                        p2playStore.fetchLatestSharedWalletBlockByDaoId(daoUniqueId)
                    } ?: run {
                        android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Cannot trigger reward transfer: Overall latest DAO block not found for DAO $daoUniqueId.")
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Error: Could not find overall latest DAO state.", Toast.LENGTH_SHORT).show() }
                        loadVotingPoll()
                        isTransferInitiated = false // Reset flag on failure
                        return@launch
                    }
                    android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer: Overall latest DAO block fetched: ${overallLatestDaoBlock.blockId}. Calling transferFunds...")


                    p2playStore.transferFunds(
                        walletData = daoWalletStateForTransfer,
                        walletBlockData = overallLatestDaoBlock.transaction,
                        blockData = solutionProposalData,
                        voteResponses = allVotes,
                        receiverAddress = developerBitcoinAddress,
                        satoshiAmount = rewardAmount,
                        context = requireContext(),
                        activity = requireActivity()
                    )

                    android.util.Log.i("FeatureVotingFragment", "triggerRewardTransfer: DAO fund transfer for reward initiated successfully.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Reward transfer from DAO wallet initiated.", Toast.LENGTH_LONG).show()
                    }

                    // Reload the poll state after attempting the transfer - this should eventually show "Approved"
                    loadVotingPoll()

                } catch (e: Exception) {
                    android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Error during DAO fund transfer for reward: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error during reward transfer: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    // Reset the flag on error so it can be retried if needed
                    isTransferInitiated = false
                    loadVotingPoll() // Reload to show current state after failed attempt

                }


            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "triggerRewardTransfer: Error triggering reward transfer (outer block): ${e.message}", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error triggering reward transfer.", Toast.LENGTH_SHORT).show() }
            }
            android.util.Log.d("FeatureVotingFragment", "triggerRewardTransfer finished")
        }
    }

    private fun setupClickListeners() {
        android.util.Log.d("FeatureVotingFragment", "setupClickListeners called")
        // Listener for the Download APK button
        binding.btnDownloadApk.setOnClickListener {
            handleDownloadApkClick()
        }

        // Listeners for Vote Yes and Vote No buttons
        binding.btnVoteYes.setOnClickListener {
            submitVote(true)
        }

        binding.btnVoteNo.setOnClickListener {
            submitVote(false)
        }
        android.util.Log.d("FeatureVotingFragment", "setupClickListeners finished")
    }

    private fun handleDownloadApkClick() {
        android.util.Log.d("FeatureVotingFragment", "handleDownloadApkClick called")
        if (votingPoll?.type == VotingPollType.FEATURE_SOLUTION) {
            val magnetLink = votingPoll?.metadata?.get("apkMagnetLink") as? String
            if (!magnetLink.isNullOrEmpty()) {
                android.util.Log.d("FeatureVotingFragment", "handleDownloadApkClick: Downloading APK from: $magnetLink")
                // TODO: Implement actual APK download using the magnet link and TorrentManager
                Toast.makeText(context, "APK download initiated (functionality needs implementation).", Toast.LENGTH_SHORT).show()

            } else {
                android.util.Log.w("FeatureVotingFragment", "handleDownloadApkClick: No magnet link available for this solution.")
                Toast.makeText(context, "No APK magnet link available for this solution.", Toast.LENGTH_SHORT).show()
            }
        } else {
            android.util.Log.w("FeatureVotingFragment", "handleDownloadApkClick: Download APK button clicked for non-feature solution poll.")
        }
        android.util.Log.d("FeatureVotingFragment", "handleDownloadApkClick finished")
    }


    private fun setupVoteBlockListener() {
        // Remove any existing listener first to avoid duplicates
        voteBlockListener?.let { listener ->
            android.util.Log.d("FeatureVotingFragment", "setupVoteBlockListener: Removing existing listener.")
            getTrustChainCommunity().removeListener(listener, P2pStoreCommunity.VOTE_YES_BLOCK)
            getTrustChainCommunity().removeListener(listener, P2pStoreCommunity.VOTE_NO_BLOCK)
            getTrustChainCommunity().removeListener(listener, P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK)
        }

        android.util.Log.d("FeatureVotingFragment", "setupVoteBlockListener called")
        val listener = object : nl.tudelft.ipv8.attestation.trustchain.BlockListener {
            override fun onBlockReceived(block: nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock) {
                android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Received block ${block.blockId}, type: ${block.type}")
                // Check if the received block is a vote (YES or NO) for the current proposal
                val isRelevantVote = try {
                    when (block.type) {
                        P2pStoreCommunity.VOTE_YES_BLOCK -> VoteYesTransactionData(block.transaction).matchesProposal(daoUniqueId, proposalId).also {
                            android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Checking VOTE_YES_BLOCK: DAO ID Match=${VoteYesTransactionData(block.transaction).getData().DAO_ID == daoUniqueId}, Proposal ID Match=${VoteYesTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID == proposalId}, IsRelevant=$it")
                        }
                        P2pStoreCommunity.VOTE_NO_BLOCK -> VoteNoTransactionData(block.transaction).matchesProposal(daoUniqueId, proposalId).also {
                            android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Checking VOTE_NO_BLOCK: DAO ID Match=${VoteNoTransactionData(block.transaction).getData().DAO_ID == daoUniqueId}, Proposal ID Match=${VoteNoTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID == proposalId}, IsRelevant=$it")
                        }
                        // Also listen for UPDATE_ACCEPTED_BLOCK to know when transfer is done
                        P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> {
                            val data = UpdateAcceptedTransactionData(block.transaction).getData()
                            (data.DAO_ID == daoUniqueId && data.SW_UNIQUE_PROPOSAL_ID == proposalId).also {
                                android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Checking UPDATE_ACCEPTED_BLOCK for proposal $proposalId: DAO ID Match=${data.DAO_ID == daoUniqueId}, Proposal ID Match=${data.SW_UNIQUE_PROPOSAL_ID == proposalId}, IsRelevant=$it")
                            }
                        }
                        else -> false.also { android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Received non-relevant block type: ${block.type}") }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FeatureVotingFragment", "Vote Block Listener: Error parsing block in listener: ${e.message}", e)
                    false
                }

                if (isRelevantVote) {
                    android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Relevant vote block received: ${block.blockId}. Reloading poll in UI thread.")
                    viewLifecycleOwner.lifecycleScope.launch {
                        // Increased delay to allow block to be processed by the database
                        android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Delaying for 2000ms before reloading poll...")
                        kotlinx.coroutines.delay(2000) // Increased delay further
                        android.util.Log.d("FeatureVotingFragment", "Vote Block Listener: Delay finished. Calling loadVotingPoll().")
                        loadVotingPoll()
                    }
                }
            }
        }

        // Register the listener for vote blocks
        getTrustChainCommunity().addListener(P2pStoreCommunity.VOTE_YES_BLOCK, listener)
        getTrustChainCommunity().addListener(P2pStoreCommunity.VOTE_NO_BLOCK, listener)
        getTrustChainCommunity().addListener(P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK, listener)
        // Store the new listener instance
        voteBlockListener = listener
        android.util.Log.d("FeatureVotingFragment", "setupVoteBlockListener finished")
    }

    private fun submitVote(isYes: Boolean) {
        android.util.Log.d("FeatureVotingFragment", "submitVote called with isYes: $isYes")

        lifecycleScope.launch {
            try {
                android.util.Log.d("FeatureVotingFragment", "submitVote: Coroutine started.")
                val currentPoll = votingPoll ?: run {
                    android.util.Log.e("FeatureVotingFragment", "submitVote: Cannot submit vote: Voting poll is null.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error: Poll data missing.", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                android.util.Log.d("FeatureVotingFragment", "submitVote: currentPoll is not null (ID: ${currentPoll.id})")

                // Check if user has already voted on this specific poll
                if (currentPoll.hasUserVoted) {
                    android.util.Log.d("FeatureVotingFragment", "submitVote: User already voted on proposal ${currentPoll.id}. Vote not submitted again.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "You have already voted on this proposal.", Toast.LENGTH_SHORT).show() }
                    // Optionally reload to update UI state if it was out of sync
                    loadVotingPoll()
                    return@launch
                }

                // Ensure the current user is a member and can vote
                android.util.Log.d("FeatureVotingFragment", "submitVote: Checking if user is a DAO member...")
                val daoBlock = withContext(Dispatchers.IO) {
                    // Using daoUniqueId to fetch latest block for membership check
                    android.util.Log.d("FeatureVotingFragment", "submitVote: Fetching latest shared wallet block by DAO ID: $daoUniqueId for membership check.")
                    p2playStore.fetchLatestSharedWalletBlockByDaoId(daoUniqueId)
                }
                val isUserMember = if (daoBlock != null) {
                    val myPublicKeyHex = p2playStore.myPeer.publicKey.keyToBin().toHex()
                    android.util.Log.d("FeatureVotingFragment", "submitVote: Latest DAO block found (ID: ${daoBlock.blockId}, Type: ${daoBlock.type}). Checking if user PK ${myPublicKeyHex.take(8)}... is member.")
                    when(daoBlock.type) {
                        P2pStoreCommunity.JOIN_BLOCK -> JoinDaoTransactionData(daoBlock.transaction).getData().SW_TRUSTCHAIN_PKS.contains(myPublicKeyHex)
                        P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(daoBlock.transaction).getData().SW_TRUSTCHAIN_PKS.contains(myPublicKeyHex)
                        else -> false
                    }
                } else {
                    false
                }
                android.util.Log.d("FeatureVotingFragment", "submitVote: Is user a DAO member? $isUserMember")

                if (!isUserMember) {
                    android.util.Log.w("FeatureVotingFragment", "submitVote: Non-member attempting to vote.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "You must be a DAO member to vote.", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                // Check if the user is the developer submitting a solution - developers should not vote on their own solution
                android.util.Log.d("FeatureVotingFragment", "submitVote: Checking if user is the developer...")
                val developerPublicKey = currentPoll.metadata["developerPublicKey"] as? String
                val isUserDeveloper = if (developerPublicKey != null) {
                    p2playStore.myPeer.publicKey.pub().toString() == developerPublicKey
                } else {
                    false
                }
                android.util.Log.d("FeatureVotingFragment", "submitVote: Is user the developer of this solution? $isUserDeveloper")

                if (isUserDeveloper && currentPoll.type == VotingPollType.FEATURE_SOLUTION) {
                    android.util.Log.w("FeatureVotingFragment", "submitVote: Developer attempting to vote on their own solution.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Developers cannot vote on their own solutions.", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                android.util.Log.d("FeatureVotingFragment", "submitVote: Final checks passed. Preparing to call voteOnProposal.")
                android.util.Log.d("FeatureVotingFragment", "submitVote: DAO ID: $daoUniqueId, Poll ID: ${currentPoll.id}, IsYes: $isYes")


                if (daoUniqueId.isEmpty() || currentPoll.id.isEmpty()) {
                    android.util.Log.e("FeatureVotingFragment", "submitVote: Cannot submit vote: daoUniqueId or poll ID is empty.")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Error submitting vote: Missing DAO or poll information.", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                android.util.Log.d("FeatureVotingFragment", "submitVote: Valid DAO and Poll IDs found. Preparing to call voteOnProposal.")


                Toast.makeText(context, "Vote submitted. Waiting for it to appear on chain...", Toast.LENGTH_SHORT).show()

                // Use the community method to create the vote block (runs on IO dispatcher due to withContext)
                withContext(Dispatchers.IO) {
                    p2playStore.voteOnProposal(
                        daoId = daoUniqueId,
                        proposalId = currentPoll.id,
                        isYes = isYes,
                        context = requireContext()
                    )
                }

                android.util.Log.d("FeatureVotingFragment", "submitVote: voteOnProposal called successfully. Waiting for block listener to update UI.")
                currentPoll.hasUserVoted = true

                // The block listener will pick up the new block and trigger loadVotingPoll

            } catch (e: Exception) {
                android.util.Log.e("FeatureVotingFragment", "submitVote: Error submitting vote: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error submitting vote: ${e.message}", Toast.LENGTH_SHORT).show()
                    loadVotingPoll()
                }
            }
            android.util.Log.d("FeatureVotingFragment", "submitVote finished")
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("FeatureVotingFragment", "onResume called. Reloading poll data.")
        // Reload poll data when the fragment is resumed
        loadVotingPoll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        voteBlockListener = null // Clear the reference
        _binding = null
    }

}
