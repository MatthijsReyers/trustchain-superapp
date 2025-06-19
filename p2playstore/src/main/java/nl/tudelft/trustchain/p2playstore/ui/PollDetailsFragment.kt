package nl.tudelft.trustchain.p2playstore.ui

import UpdateProposalPoll
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.databinding.FragmentPollDetailsBinding
import nl.tudelft.trustchain.p2playstore.models.DaoJoinPoll
import nl.tudelft.trustchain.p2playstore.models.Poll

class PollDetailsFragment : BaseFragment() {
    private var _binding: FragmentPollDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var proposalId: String;

    private var isJoinProposal: Boolean = false

    private val poll: Poll get() = (daoJoinPoll ?: updateProposalPoll)!!

    private val daoId: String get() = poll.daoId

    private var daoJoinPoll: DaoJoinPoll? = null
    private var updateProposalPoll: UpdateProposalPoll? = null

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        // The previous fragment (home) tells us which block/app/version to show
        val args = this.requireArguments();
        this.proposalId = args.getString("proposalId")!!
        this.isJoinProposal = args.getBoolean("isJoinProposal")

        if (isJoinProposal) {
            this.daoJoinPoll = DaoJoinPoll.findByProposalId(proposalId)
        } else {
            this.updateProposalPoll = UpdateProposalPoll.findByProposalId(proposalId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPollDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        this.setupClickListeners()

        lifecycleScope.launch {
            updateVoteButtons();
            updateFeatureRequestPreview()
        }
    }

    /**
     * Called whenever new blocks with the DAO ID for this app are detected, practically this means
     * we want to update the whole UI since votes/version updates might have changed.
     */
    override suspend fun onChainUpdated(block: TrustChainBlock) {
        Log.d("P2pStore", "Chain update ${block.type}")

        when (block.type) {
            // Was a new version of the app released?
            P2pStoreCommunity.JOIN_BLOCK, P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> {
                // TODO: Update UI?
            }
            // ALl the other possible blocks are essentially just updates for various polls,
            else -> {
                // TODO: Update UI
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateVoteButtons() {
        when (this.poll.getMyVote()?.type) {
            VOTE_YES_BLOCK -> {
                this.binding.btnVoteYes.text = "Voted yes"
                this.binding.btnVoteNo.text = "Vote no"
                this.binding.btnVoteYes.isEnabled = true
                this.binding.btnVoteNo.isEnabled = false
            }
            VOTE_NO_BLOCK -> {
                this.binding.btnVoteYes.text = "Vote yes"
                this.binding.btnVoteNo.text = "Voted no"
                this.binding.btnVoteYes.isEnabled = false
                this.binding.btnVoteNo.isEnabled = true
            }
            else -> {
                this.binding.btnVoteYes.text = "Vote yes"
                this.binding.btnVoteNo.text = "Vote no"
                this.binding.btnVoteYes.isEnabled = true
                this.binding.btnVoteNo.isEnabled = true
            }
        }
    }

    private fun updateFeatureRequestPreview() {
        if (this.isJoinProposal) {
            this.binding.featureRequestPreview.visibility = View.GONE
            return
        }
        this.binding.featureRequestPreview.visibility = View.VISIBLE

    }

    /**
     * This function attaches the required event handlers to all the buttons on the page
     */
    private fun setupClickListeners() {
        this.binding.btnVoteYes.setOnClickListener {
            lifecycleScope.launch {
                poll.submitVote(true, requireContext())
            }
        }
        this.binding.btnVoteNo.setOnClickListener {
            lifecycleScope.launch {
                poll.submitVote(false, requireContext())
            }
        }
    }
}
