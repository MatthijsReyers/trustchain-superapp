package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.databinding.FragmentAllVotingPollsBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.models.Poll

class AllVotingPollsFragment : BaseFragment() {
    private var _binding: FragmentAllVotingPollsBinding? = null
    private val binding get() = _binding!!

    private lateinit var daoId: String

    private lateinit var app: P2playApp

    private lateinit var polls: ArrayList<Poll>

    private val pollsAdapter = PollPreviewsAdapter()

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
        try {
            this.daoId = arguments?.getString("daoId")!!
            this.app = P2playApp.findByDoaId(daoId)!!
            this.loadPolls()
        }
        catch (err: Throwable) {
            Log.e("P2PlayStore", "Failed to load polls: $err")
            findNavController().navigateUp()
        }
        this.setupRecyclerView()
    }

    override suspend fun onChainUpdated(block: TrustChainBlock) {
        try {
            this.app = this.app.getLatestVersion()
            this.loadPolls()
        }
        catch (err: Throwable) {
            Log.e("P2PlayStore", "Failed to load polls: $err")
         }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Refreshes the list of polls
     */
    private fun loadPolls() {
        this.polls = ArrayList()
        this.polls.addAll(this.app.getAllUpdatePolls())
        this.polls.addAll(this.app.getAllDaoJoinPolls())
        this.polls.sortBy { poll: Poll -> poll.block.insertTime }
        this.polls.reverse()
        this.pollsAdapter.updatePolls(this.polls)
        Log.d("P2PlayStore", "Updated polls list: ${this.polls.size} polls loaded")
    }

    private fun setupRecyclerView() {
        binding.recyclerViewPolls.apply {
            adapter = pollsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }
}

