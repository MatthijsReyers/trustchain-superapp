package nl.tudelft.trustchain.p2playstore.ui

import UpdateProposalPoll
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import android.widget.FrameLayout
import androidx.navigation.fragment.findNavController

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.util.toHex

import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.PROPOSE_UPDATE_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_REQUEST_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK

import nl.tudelft.trustchain.p2playstore.databinding.FragmentPollDetailsBinding
import nl.tudelft.trustchain.p2playstore.models.DaoJoinPoll
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.models.Poll
import nl.tudelft.trustchain.p2playstore.transactionData.BaseData
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.ProposeUpdateTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteNoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.VoteYesTransactionData

import kotlin.math.max
import kotlin.math.roundToInt

class PollDetailsFragment : BaseFragment() {
    private var _binding: FragmentPollDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var proposalId: String
    private lateinit var app: P2playApp

    private val poll: Poll get() = joinPoll ?: updatePoll!!

    private var joinPoll: DaoJoinPoll? = null
    private var updatePoll: UpdateProposalPoll? = null

    private var isTransferInitiated: Boolean = false
    private var voteBlockListener: BlockListener? = null

    /**
     * Is the user currently voting? We disable the buttons during this time to prevent them from
     * creating multiple vote blocks by spamming clicks while we're still creating their vote block.
     */
    private var isVoting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPollDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            this.proposalId = arguments?.getString("proposalId")!!
            when (val pollType = arguments?.getString("pollType")) {
                "join" -> this.joinPoll = DaoJoinPoll.findByProposalId(proposalId)!!
                "update" -> this.updatePoll = UpdateProposalPoll.findByProposalId(proposalId)!!
                else -> throw Exception("Unknown poll type: $pollType")
            }
            this.app = this.poll.getApp().getLatestVersion()
        }
        catch (err: Throwable) {
            Log.e("P2PlayStore", "Failed to load poll: $err")
            findNavController().navigateUp()
        }
        this.setupClickListeners()
        this.setupVoteBlockListener()

        this.updateVoteButtons()
        this.updateProgressBars()
        this.updatePreviewCard()
        this.updateBottomCard()
    }

    override suspend fun onChainUpdated(block: TrustChainBlock) {
        if (this._binding == null) return // Fragment view destroyed

        try {
            val data: BaseData = when (block.type) {
                JOIN_BLOCK -> JoinDaoTransactionData(block.transaction).getData()
                UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(block.transaction).getData()
                PROPOSE_UPDATE_BLOCK -> ProposeUpdateTransactionData(block.transaction).getData()
                JOIN_REQUEST_BLOCK -> JoinDaoTransactionData(block.transaction).getData() // Can be JoinRequestData too, but this is a common base
                VOTE_YES_BLOCK -> VoteYesTransactionData(block.transaction).getData()
                VOTE_NO_BLOCK -> VoteNoTransactionData(block.transaction).getData()
                else -> return // Ignore other block types
            }

            if (data.DAO_ID != this.poll.daoId) return; // Not relevant for this poll

            if (this.poll == null) {
                Log.w("PollDetailsFragment", "Poll object became null after chain update for ID: $proposalId")
                findNavController().navigateUp()
                return
            }

            // Update the UI on the main thread
            requireActivity().runOnUiThread {
                updatePreviewCard()
                updateBottomCard()
                updateProgressBars()
                updateVoteButtons()

                // Trigger reward transfer if it's an approved feature solution and not yet initiated
                if (poll is UpdateProposalPoll && poll.isApproved && !isTransferInitiated) {
                    Log.d("PollDetailsFragment", "Approved UpdateProposalPoll detected, triggering reward transfer.")
                    isTransferInitiated = true // Set flag to prevent multiple triggers
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            (poll as UpdateProposalPoll).triggerRewardTransfer(requireContext(), requireActivity())
                        } catch (e: Exception) {
                            Log.e("PollDetailsFragment", "Error during reward transfer: ${e.message}", e)
                            requireActivity().runOnUiThread {
                                Toast.makeText(context, "Error initiating reward transfer: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            isTransferInitiated = false // Allow retry if it failed
                        }
                    }
                }
            }
        }
        catch (err: Throwable) {
            Log.e("P2PlayStore", "Error during chain update in PollDetailsFragment: ${err.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("FeatureVotingFragment", "onResume called. Reloading poll data.")
        try {
            updateVoteButtons()
            updateProgressBars()
            updateBottomCard()
        }
        catch (err: Throwable) {
            Log.e("P2PlayStore", "Error updating UI after resume: $err")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clean up block listener
        voteBlockListener?.let { listener ->
            Log.d("PollDetailsFragment", "onDestroyView: Removing block listener.")
            getTrustChainCommunity().removeListener(listener, VOTE_YES_BLOCK)
            getTrustChainCommunity().removeListener(listener, VOTE_NO_BLOCK)
            getTrustChainCommunity().removeListener(listener, UPDATE_ACCEPTED_BLOCK)
            getTrustChainCommunity().removeListener(listener, PROPOSE_UPDATE_BLOCK)
            getTrustChainCommunity().removeListener(listener, JOIN_REQUEST_BLOCK)
        }
        voteBlockListener = null

        _binding = null
    }

    /**
     * Setup block listener for real-time updates
     */
    private fun setupVoteBlockListener() {
        // Remove any existing listener first
        voteBlockListener?.let { listener ->
            Log.d("PollDetailsFragment", "Removing existing block listener")
            getTrustChainCommunity().removeListener(listener, VOTE_YES_BLOCK)
            getTrustChainCommunity().removeListener(listener, VOTE_NO_BLOCK)
            getTrustChainCommunity().removeListener(listener, UPDATE_ACCEPTED_BLOCK)
            getTrustChainCommunity().removeListener(listener, PROPOSE_UPDATE_BLOCK)
            getTrustChainCommunity().removeListener(listener, JOIN_REQUEST_BLOCK)
        }

        Log.d("PollDetailsFragment", "Setting up vote block listener")
        val listener = object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                Log.d("PollDetailsFragment", "Block received: ${block.blockId}, type: ${block.type}")

                val isRelevantVote = try {
                    when (block.type) {
                        VOTE_YES_BLOCK -> VoteYesTransactionData(block.transaction).matchesProposal(poll.daoId, poll.proposalId)
                        VOTE_NO_BLOCK -> VoteNoTransactionData(block.transaction).matchesProposal(poll.daoId, poll.proposalId)
                        UPDATE_ACCEPTED_BLOCK -> {
                            val data = UpdateAcceptedTransactionData(block.transaction).getData()
                            data.DAO_ID == poll.daoId && data.SW_UNIQUE_PROPOSAL_ID == poll.proposalId
                        }
                        PROPOSE_UPDATE_BLOCK -> {
                            val data = ProposeUpdateTransactionData(block.transaction).getData()
                            data.DAO_ID == poll.daoId && data.SW_UNIQUE_PROPOSAL_ID == poll.proposalId
                        }
                        JOIN_REQUEST_BLOCK -> {
                            val data = JoinDaoTransactionData(block.transaction).getData()
                            data.DAO_ID == poll.daoId
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    Log.e("PollDetailsFragment", "Error parsing block in listener: ${e.message}")
                    false
                }

                if (isRelevantVote) {
                    Log.d("PollDetailsFragment", "Relevant block received, updating UI")
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(300) // Small delay for database update
                        updateVoteButtons()
                        updateProgressBars()
                        updateBottomCard()
                    }
                }
            }
        }

        // Register the listener
        getTrustChainCommunity().addListener(VOTE_YES_BLOCK, listener)
        getTrustChainCommunity().addListener(VOTE_NO_BLOCK, listener)
        getTrustChainCommunity().addListener(UPDATE_ACCEPTED_BLOCK, listener)
        getTrustChainCommunity().addListener(PROPOSE_UPDATE_BLOCK, listener)
        getTrustChainCommunity().addListener(JOIN_REQUEST_BLOCK, listener)
        voteBlockListener = listener
        Log.d("PollDetailsFragment", "Vote block listener setup complete")
    }

    /**
     * Called when the user presses a vote "yes"/"no" button.
     */
    private fun onVote(isYes: Boolean) {
        Log.d("PollDetailsFragment", "onVote called with isYes: $isYes")

        // Prevent multiple votes
        if (!this.isVoting) {
            this.isVoting = true
            try {
                lifecycleScope.launch {
                    // Check if user has already voted
                    val votingPoll = withContext(Dispatchers.IO) {
                        p2playStore.getVotingPoll(poll.daoId, poll.proposalId)
                    }

                    if (votingPoll?.hasUserVoted == true) {
                        Log.d("PollDetailsFragment", "User already voted")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "You have already voted on this proposal.", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // Disable buttons immediately
                    withContext(Dispatchers.Main) {
                        binding.btnVoteYes.isEnabled = false
                        binding.btnVoteNo.isEnabled = false
                        binding.btnVoteYes.alpha = 0.5f
                        binding.btnVoteNo.alpha = 0.5f
                        Toast.makeText(context, "Vote submitted. Waiting for confirmation...", Toast.LENGTH_SHORT).show()
                    }

                    // Submit vote using community method
                    withContext(Dispatchers.IO) {
                        p2playStore.voteOnProposal(
                            daoId = poll.daoId,
                            proposalId = poll.proposalId,
                            isYes = isYes,
                            context = requireContext()
                        )
                    }

                    Log.d("PollDetailsFragment", "Vote submitted successfully")
                }
            } catch (err: Throwable) {
                Log.e("P2PlayStore", "Error occurred during voting: $err")
            } finally {
                this.isVoting = false
            }
        }
    }

    /**
     * Updates the card at the top of the page which describes what poll the user is voting on,
     * this can be either a join request or an app update proposal.
     */
    private fun updatePreviewCard() {
        if (this.joinPoll != null) {
            binding.updateProposalCard.visibility = View.GONE
            binding.joinRequestCard.visibility = View.VISIBLE
            binding.joinRequestPeer.text = this.joinPoll!!.requestingUser.substring(0, 8)
            binding.feeAmount.text = "${this.app.getEntranceFee()} sats"
        }
        else {
            binding.updateProposalCard.visibility = View.VISIBLE
            binding.joinRequestCard.visibility = View.GONE
            binding.developerName.text = updatePoll!!.block.publicKey.toHex().substring(0, 8)
            binding.rewardAmount.text = "${updatePoll!!.rewardAmount} sats"
            binding.updateName.text = updatePoll!!.name
            binding.updateDescription.text = updatePoll!!.description
        }
    }

    /**
     * Updates the card on the bottom
     */
    private fun updateBottomCard() {
        val yes = poll.getYesVotes().size
        val required = poll.votesRequired
        if (poll.isApproved) {
            binding.votesRequiredText.text = "Proposal was approved with $yes of " +
                "${required} required votes"
        }
        else if (poll.isDenied) {
            val no = poll.getNoVotes().size
            binding.votesRequiredText.text = "Proposal was denied with $no no votes"
        }
        else {
            binding.votesRequiredText.text = "$yes of ${required} votes needed for approval"
        }
    }

    /**
     * Updates the "yes"/"no"/"pending" progress bars and percentages that indicate how (many) the
     * members of the DAO have voted.
     */
    private fun updateProgressBars() {
        binding.percentageYes.text = "${(poll.yesPercentage * 100).roundToInt()}%"
        this.updateProgressBar(
            binding.yesProgressBar,
            binding.yesProgress,
            poll.yesPercentage
        )
        binding.percentageNo.text = "${(poll.noPercentage * 100).roundToInt()}%"
        this.updateProgressBar(
            binding.noProgressBar,
            binding.noProgress,
            poll.noPercentage
        )
        binding.percentagePending.text = "${(poll.pendingPercentage * 100).roundToInt()}%"
        this.updateProgressBar(
            binding.pendingProgressBar,
            binding.pendingProgress,
            poll.pendingPercentage
        )
    }

    /**
     * Updates/disables the vote "yes"/"no" buttons depending on whether or the user is allowed
     * to/already has voted in this poll.
     */
    private fun updateVoteButtons() {
        // Is the user even allowed to vote in this poll?
        if (!this.poll.isReceivingUser) {
            return this.disableVoteButtons()
        }

        // Has the user not yet voted?
        val myVote = this.poll.getMyVote()
        if (myVote == null) {
            // Is the poll still open?
            if (!this.poll.isPending) {
                return this.disableVoteButtons()
            }

            this.binding.btnVoteNo.alpha = 1.0f
            this.binding.btnVoteNo.isEnabled = true
            this.binding.btnVoteNo.text = "Vote no"

            this.binding.btnVoteYes.alpha = 1.0f
            this.binding.btnVoteYes.isEnabled = true
            this.binding.btnVoteYes.text = "Vote yes"
            return
        }

        this.binding.btnVoteNo.isEnabled = false
        this.binding.btnVoteYes.isEnabled = false

        if (myVote.type == VOTE_YES_BLOCK) {
            this.binding.btnVoteNo.alpha = 0.3f
            this.binding.btnVoteNo.text = "Vote no"
            this.binding.btnVoteYes.alpha = 1.0f
            this.binding.btnVoteYes.text = "Voted yes"
        } else {
            this.binding.btnVoteNo.alpha = 1.0f
            this.binding.btnVoteNo.text = "Voted no"
            this.binding.btnVoteYes.alpha = 0.3f
            this.binding.btnVoteYes.text = "Vote yes"
        }
    }

    private fun disableVoteButtons() {
        this.binding.btnVoteNo.alpha = 0.3f
        this.binding.btnVoteNo.isEnabled = false
        this.binding.btnVoteNo.text = "Vote no"

        this.binding.btnVoteYes.alpha = 0.3f
        this.binding.btnVoteYes.isEnabled = false
        this.binding.btnVoteYes.text = "Vote yes"
    }

    private fun updateProgressBar(bar: FrameLayout, progress: View, value: Float) {
        bar.post {
            // Clamp value at 1, because 0 maps to 100% for some reason...
            progress.layoutParams.width = max(1, (bar.width * value).roundToInt())
            progress.requestLayout()
            bar.requestLayout()
        }
    }

    private fun setupClickListeners() {
        binding.btnVoteYes.setOnClickListener {
            this.onVote(true)
        }
        binding.btnVoteNo.setOnClickListener {
            this.onVote(false)
        }
    }
}
