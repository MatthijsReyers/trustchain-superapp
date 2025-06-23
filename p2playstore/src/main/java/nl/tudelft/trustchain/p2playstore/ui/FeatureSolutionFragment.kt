package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.p2playstore.databinding.FragmentFeatureSolutionBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bitcoinj.core.Address
import org.bitcoinj.core.NetworkParameters
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid // Import WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.models.FeatureRequest

class FeatureSolutionFragment : BaseFragment() {
    private var _binding: FragmentFeatureSolutionBinding? = null
    private val binding get() = _binding!!

    private lateinit var featureRequest: FeatureRequest

    private lateinit var featureId: String
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
        try {
            val featureRequestId = arguments?.getString("featureRequestId") as String
            this.featureRequest = FeatureRequest.findById(featureRequestId)!!
            updateFeatureRequestPreview()
            setupClickListeners()
        }
        catch (err: Throwable) {
            android.util.Log.e("P2PlayStore", "Failed to load feature request: $err")
            findNavController().navigateUp()
        }
    }

    private fun updateFeatureRequestPreview() {
        binding.title.text = this.featureRequest.title
        binding.description.text = this.featureRequest.description

        val manager = WalletManagerAndroid.getInstance()
        binding.etDeveloperBitcoinAddress.hint = "Enter your Bitcoin address for reward"
        binding.etDeveloperBitcoinAddress.text = Editable.Factory.getInstance().newEditable(
            manager.protocolAddress().toString()
        )
    }

    private fun setupClickListeners() {
        binding.btnSubmitSolution.setOnClickListener {
            lifecycleScope.launch {
                submitSolution()
            }
        }

        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    suspend private fun submitSolution() {
        Log.d("P2PlayStore", "Submitting solution")

        val title = binding.etSolutionTitle.text.toString().trim()
        val description = binding.etSolutionDescription.text.toString().trim()
        val magnetLink = binding.etMagnetLink.text.toString().trim()
        val developerBitcoinAddress = binding.etDeveloperBitcoinAddress.text.toString().trim()

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

        if (developerBitcoinAddress.isEmpty()) {
            Toast.makeText(context, "Please enter your Bitcoin address to receive the reward.", Toast.LENGTH_LONG).show()
            android.util.Log.e("FeatureSolutionFragment", "Developer Bitcoin address is missing or a placeholder!")
            return
        }

        // Basic validation for Bitcoin address format
        try {
            val params: NetworkParameters = WalletManagerAndroid.getInstance().params // Use the current network params
            Address.fromString(params, developerBitcoinAddress)
        } catch (e: Exception) {
            Toast.makeText(context, "Invalid Bitcoin address format.", Toast.LENGTH_LONG).show()
            android.util.Log.e("FeatureSolutionFragment", "Invalid Bitcoin address format: ${e.message}")
            return
        }

        try {
            this.featureRequest.submitSolution(
                title = title,
                description = description,
                magnetLink = magnetLink,
                developerBitcoinAddress = developerBitcoinAddress,
            )

            Toast.makeText(
                context,
                "Solution submitted successfully!",
                Toast.LENGTH_LONG
            ).show()
            findNavController().navigateUp()

        } catch (e: Exception) {
            android.util.Log.e("P2PlayStore", "Error submitting solution: ${e.message}")
            Toast.makeText(context, "Error submitting solution", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
