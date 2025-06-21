package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureListBinding
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData

class FeatureListFragment : BaseFragment() {
    private var _binding: FragmentFeatureListBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var app: P2playApp
    private lateinit var adapter: FeatureRequestPreviewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatureListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            val daoId = arguments?.getString("daoId") as String
            this.app = P2playApp.findByDoaId(daoId)!!

            setupRecyclerView()
            setupClickListeners()

            updateFeatureRequests()
        }
        catch (e: Throwable) {
            Log.e("P2PlayStore", "Error loading app details: ${e.message}")
            findNavController().navigateUp()
        }
    }

    override suspend fun onChainUpdated(block: TrustChainBlock) {
        try {
            // Update the feature request list if anything has changed about this app.
            val data = JoinDaoTransactionData(block.transaction).getData()
            if (data.DAO_ID == this.app.daoId) {
                updateFeatureRequests()
            }
        }
        catch (e: Throwable) {
            Log.e("P2PlayStore", "Error updating UI after new block: ${e.message}")
            findNavController().navigateUp()
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            updateFeatureRequests()
        }
        catch (e: Throwable) {
            Log.e("P2PlayStore", "Error updating UI after resume: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateFeatureRequests() {
        val requests = this.app.getFeatureRequests()
        if (requests.isEmpty()) {
            this.adapter.updateRequests(requests)
            binding.tvNoFeatures.visibility = View.VISIBLE
            binding.recyclerViewFeatures.visibility = View.GONE
        }
        else {
            binding.tvNoFeatures.visibility = View.GONE
            this.adapter.updateRequests(requests)
        }
    }

    private fun setupRecyclerView() {
        this.adapter = FeatureRequestPreviewsAdapter()
        binding.recyclerViewFeatures.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewFeatures.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnRequestFeature.setOnClickListener {
            findNavController()
                .navigate(
                    R.id.action_featureListFragment_to_featureRequestFragment,
                    Bundle().apply { putString("daoId", app.daoId) }
                )
        }
    }
}
