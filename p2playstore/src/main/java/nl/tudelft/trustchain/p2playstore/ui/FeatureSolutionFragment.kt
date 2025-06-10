package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureSolutionBinding

class FeatureSolutionFragment : BaseFragment() {
    private var _binding: FragmentFeatureSolutionBinding? = null
    private val binding get() = _binding!!

    private lateinit var featureId: String
    private lateinit var daoId: String
    private lateinit var daoUniqueId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeatureSolutionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        featureId = arguments?.getString("featureId") ?: ""
//        daoId = arguments?.getString("daoId") ?: ""
        daoUniqueId = arguments?.getString("daoUniqueId") ?: ""


        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Load feature request details to show context
        val featureTitle = arguments?.getString("featureTitle") ?: "Unknown Feature"
        val featureDescription = arguments?.getString("featureDescription") ?: ""

        binding.tvFeatureTitle.text = featureTitle
        binding.tvFeatureDescription.text = featureDescription
    }

    private fun setupClickListeners() {
        binding.btnSubmitSolution.setOnClickListener {
            submitSolution()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun submitSolution() {
        val title = binding.etSolutionTitle.text.toString().trim()
        val description = binding.etSolutionDescription.text.toString().trim()
        val magnetLink = binding.etMagnetLink.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(context, "Please enter a solution title", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(context, "Please enter a solution description", Toast.LENGTH_SHORT).show()
            return
        }

        if (magnetLink.isEmpty()) {
            Toast.makeText(context, "Please enter a magnet link for your APK", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Use the community method to create the block
            p2playStore.createFeatureSolution(
                daoId = daoUniqueId,
                featureId = featureId,
                title = title,
                description = description,
                apkMagnetLink = magnetLink
            )

            Toast.makeText(context, "Solution submitted successfully! DAO members can now vote.", Toast.LENGTH_LONG).show()
            android.util.Log.d("FeatureSolutionFragment", "Solution submitted: $title for Feature $featureId in DAO ${daoUniqueId}Id")
            findNavController().navigateUp()

        } catch (e: Exception) {
            android.util.Log.e("FeatureSolutionFragment", "Error submitting solution: ${e.message}")
            Toast.makeText(context, "Error submitting solution", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
