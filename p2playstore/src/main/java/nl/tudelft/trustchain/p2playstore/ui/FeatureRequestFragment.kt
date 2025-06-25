package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureRequestBinding
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.util.taproot.CTransaction
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity
import nl.tudelft.trustchain.p2playstore.models.FeatureRequest
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData

class FeatureRequestFragment : BaseFragment() {
    private var _binding: FragmentFeatureRequestBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: P2playApp

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatureRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val daoId = arguments?.getString("daoId") as String
        this.app = P2playApp.findByDoaId(daoId)!!

        // Don't allow people who are not DAO members to access this page
        if (!this.app.isDaoMember()) {
            findNavController().navigateUp()
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSubmitRequest.setOnClickListener {
            submitFeatureRequest()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun submitFeatureRequest() {
        val title = binding.etFeatureTitle.text.toString().trim()
        val description = binding.etFeatureDescription.text.toString().trim()
        val reward = binding.etReward.text.toString().toLongOrNull() ?: 0L

        if (title.isEmpty()) {
            printToast("Please enter a feature title")
            return
        }

        if (description.isEmpty()) {
            printToast("Please enter a feature description")
            return
        }

        if (reward <= 0) {
            printToast("Please enter a valid reward amount")
            return
        }

        val daoWalletBalance = this.app.getWalletBalance()
        if (daoWalletBalance < reward) {
            return printToast(
                "Fee is higher than the funds in shared wallet," +
                    " your request might be too expensive ($reward satoshis).",
            )
        }

        try {
            FeatureRequest.createFeatureRequest(
                daoId = this.app.daoId,
                title = title,
                description = description,
                reward = reward,
            )
            printToast(
                "Feature request submitted successfully! DAO members can now propose solutions."
            )
            findNavController().navigateUp()

        } catch (e: Exception) {
            android.util.Log.e(
                "P2PlayStore",
                "Error submitting feature request: ${e.message}"
            )
            printToast("Error submitting feature request")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
