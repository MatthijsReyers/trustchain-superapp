package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureRequestBinding

class FeatureRequestFragment : BaseFragment() {
    private var _binding: FragmentFeatureRequestBinding? = null
    private val binding get() = _binding!!

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

        daoBlockId = arguments?.getString("blockId") ?: ""
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""

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
            Toast.makeText(context, "Please enter a feature description", Toast.LENGTH_SHORT).show()
            return
        }

        if (reward <= 0) {
            Toast.makeText(context, "Please enter a valid reward amount", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            p2playStore.createFeatureRequest(
                daoId = daoUniqueId,
                title = title,
                description = description,
                reward = reward
            )

            Toast.makeText(context, "Feature request submitted successfully!", Toast.LENGTH_SHORT).show()
            android.util.Log.d("FeatureRequestFragment", "Feature request submitted: $title for DAO ${daoUniqueId}Id")
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
