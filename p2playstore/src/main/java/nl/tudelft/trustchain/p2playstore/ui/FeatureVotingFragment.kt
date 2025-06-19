package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
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
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_NO_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.VOTE_YES_BLOCK
import nl.tudelft.trustchain.p2playstore.models.DaoJoinPoll
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.models.Poll
import nl.tudelft.trustchain.p2playstore.transactionData.BaseData

class FeatureVotingFragment : BaseFragment() {
    private var _binding: FragmentFeatureVotingBinding? = null
    private val binding get() = _binding!!

    private lateinit var proposalId: String
    private lateinit var poll: Poll
    private lateinit var app: P2playApp

    /**
     * Is the user currently voting? We disable the buttons during this time to prevent them from
     * creating multiple vote blocks by spamming clicks while
     */
    private var isVoting = false

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
        try {
            this.proposalId = arguments?.getString("proposalId")!!
            when (val pollType = arguments?.getString("pollType")) {
                "join" -> this.poll = DaoJoinPoll.findByProposalId(proposalId)!!
                "update" -> this.poll = DaoJoinPoll.findByProposalId(proposalId)!!
                else -> throw Exception("Unknown poll type: $pollType")
            }
            this.app = this.poll.getApp()
        }
        catch (err: Throwable) {
            android.util.Log.e("P2PlayStore", "Failed to load poll: $err")
            findNavController().navigateUp()
        }
        this.setupClickListeners()
    }

    override suspend fun onChainUpdated(block: TrustChainBlock) {
        try {
            val data: BaseData = JoinDaoTransactionData(block.transaction).getData()
            if (data.DAO_ID != this.poll.daoId) return;

            when (block.type) {
                VOTE_NO_BLOCK, VOTE_YES_BLOCK -> {
                    updateVoteButtons();
                }
            }
        }
        catch (err: Throwable) {
            android.util.Log.e("P2PlayStore", "Error during chain update: $err")
        }
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("FeatureVotingFragment", "onResume called. Reloading poll data.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Called when the user presses a vote "yes"/"no" button.
     */
    private fun onVote(isYes: Boolean) {
        // Prevent the user from creating multiple vote blocks by voting "twice" while the chain is
        // still being updated.
        if (!this.isVoting) {
            this.isVoting = true;
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    poll.submitVote(isYes, requireContext())
                }
            }
            catch (err: Throwable) {
                Log.e("P2PlayStore", "Error occured during voting: $err")
            }
            finally {
                this.isVoting = false
            }
        }
    }

    private fun updateVoteButtons() {
        // Is the user even allowed to vote?
        if (!this.app.isDaoMember()) {
            this.binding.btnVoteNo.alpha = 0.3f
            this.binding.btnVoteNo.isEnabled = false
            this.binding.btnVoteNo.text = "Vote no"

            this.binding.btnVoteYes.alpha = 0.3f
            this.binding.btnVoteYes.isEnabled = false
            this.binding.btnVoteYes.text = "Vote yes"
            return
        }

        // Has the user not yet voted?
        val myVote = this.poll.getMyVote()
        if (myVote == null) {
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

    private fun setupClickListeners() {
        binding.btnVoteYes.setOnClickListener {
            this.onVote(true)
        }
        binding.btnVoteNo.setOnClickListener {
            this.onVote(false)
        }
    }
}
