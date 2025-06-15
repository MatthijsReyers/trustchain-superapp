package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
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
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid // Import WalletManagerAndroid

class FeatureSolutionFragment : BaseFragment() {
    private var _binding: FragmentFeatureSolutionBinding? = null
    private val binding get() = _binding!!

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

        featureId = arguments?.getString("featureId") ?: ""
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

         binding.etDeveloperBitcoinAddress.hint = "Enter your Bitcoin address for reward"
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
        val title = binding.etSolutionTitle.text.toString().trim()
        val description = binding.etSolutionDescription.text.toString().trim()
        val magnetLink = binding.etMagnetLink.text.toString().trim()

//        val developerBitcoinAddress = "mty7WcvBbEYXKuwW86KJwatpMXcm7NMitX"
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
            // Use the community method to create the block
            p2playStore.createFeatureSolution(
                daoId = daoUniqueId,
                featureRequestId = featureId,
                solutionTitle = title,
                solutionDescription = description,
                apkMagnetLink = magnetLink,
                developerBitcoinAddress = developerBitcoinAddress,
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
