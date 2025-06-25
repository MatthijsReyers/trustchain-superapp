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
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import nl.tudelft.trustchain.p2playstore.transactionData.UpdateAcceptedTransactionData

class FeatureRequestFragment : BaseFragment() {
    private var _binding: FragmentFeatureRequestBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: P2playApp
    private lateinit var daoBlockId: String
    private lateinit var daoUniqueId: String

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

        this.daoBlockId = this.app.block.blockId
        this.daoUniqueId = daoId

        if (daoUniqueId.isEmpty()) {
            android.util.Log.e("FeatureRequestFragment", "daoUniqueId is empty. Cannot submit feature request.")
            binding.btnSubmitRequest.isEnabled = false
            binding.btnSubmitRequest.alpha = 0.5f
            Toast.makeText(context, "Error: Could not load DAO information.", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "Please enter a feature title", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(context, "Please enter a feature description", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (reward <= 0) {
            Toast.makeText(context, "Please enter a valid reward amount", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val daowalletBalance =
            try {
                val latestDaoWalletBlock =
                    p2playStore.fetchLatestSharedWalletBlockByDaoId(daoUniqueId)
                if (latestDaoWalletBlock != null) {
                    val serializedTx = when (latestDaoWalletBlock.type) {
                        P2pStoreCommunity.JOIN_BLOCK -> JoinDaoTransactionData(
                            latestDaoWalletBlock.transaction
                        ).getData().SW_TRANSACTION_SERIALIZED

                        P2pStoreCommunity.UPDATE_ACCEPTED_BLOCK -> UpdateAcceptedTransactionData(
                            latestDaoWalletBlock.transaction
                        ).getData().SW_TRANSACTION_SERIALIZED

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
                    "Error fetching DAO balance for sufficient funds check: ${e.message}"
                )
                0L // Assume 0 if fetching fails, to prevent transfer
        }
        if (daowalletBalance < reward) {
            Toast.makeText(
                context,
                "Fee is higher than the funds in shared wallet your request might be to expensive ($reward satoshis).",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.w(
                "FeatureRequestFragment",
                "Insufficient personal funds ($daowalletBalance) for requested reward ($reward). Request aborted."
            )
            return
        }
        try {
            p2playStore.createFeatureRequest(
                daoId = daoUniqueId,
                title = title,
                description = description,
                reward = reward,
            )

            Toast.makeText(context, "Feature request submitted successfully! DAO members can now propose solutions.", Toast.LENGTH_SHORT).show()
            android.util.Log.d("FeatureRequestFragment", "Feature request submitted: $title for DAO ${daoUniqueId}Id by ${p2playStore.myPeer.publicKey.keyToBin().toHex()} with reward $reward to be sent to developer who submit succesful solution")
            findNavController().navigateUp()

        } catch (e: Exception) {
            android.util.Log.e("FeatureRequestFragment", "Error submitting feature request: ${e.message}")
            Toast.makeText(context, "Error submitting feature request", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
