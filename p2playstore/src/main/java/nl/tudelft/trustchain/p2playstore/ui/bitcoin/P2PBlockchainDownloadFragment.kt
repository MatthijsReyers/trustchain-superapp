package nl.tudelft.trustchain.p2playstore.ui.bitcoin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.currencyii.coin.*
import nl.tudelft.trustchain.p2playstore.databinding.FragmentP2pBlockchainDownloadBinding
import nl.tudelft.trustchain.p2playstore.ui.BaseFragment
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import kotlin.concurrent.thread

/**
 * A simple [Fragment] subclass.
 * Use the [P2PBlockchainDownloadFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class P2PBlockchainDownloadFragment : BaseFragment(R.layout.fragment_p2p_blockchain_download) {
    @Suppress("ktlint:standard:property-naming") // False positive
    private var _binding: FragmentP2pBlockchainDownloadBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentP2pBlockchainDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bitcoinProgressContinue.setOnClickListener {
            if (WalletManagerAndroid.isInitialized() &&
                WalletManagerAndroid.getInstance().progress >= 100
            ) {
                findNavController().navigate(
                    P2PBlockchainDownloadFragmentDirections
                        .actionP2pblockchainDownloadFragmentToHomeFragment()
                )
            }
        }

        if (WalletManagerAndroid.isInitialized() &&
            WalletManagerAndroid.getInstance().progress >= 100
        ) {
            binding.bitcoinDownloadPercentage.text = "Fully Synced!"
            binding.bitcoinProgressContinue.text = "Continue"
            findNavController().navigate(
                P2PBlockchainDownloadFragmentDirections
                    .actionP2pblockchainDownloadFragmentToHomeFragment()
            )
            return
        }

        if (WalletManagerAndroid.isInitialized()) {
            updateProgressUI(WalletManagerAndroid.getInstance().progress)

            val networkName = when (WalletManagerAndroid.getInstance().params) {
                RegTestParams.get() -> "RegTest"
                TestNet3Params.get() -> "TestNet"
                MainNetParams.get() -> "MainNet"
                else -> ""
            }
            binding.downloadingChainTv.text =
                "Please wait while the chain from $networkName is downloading."

            thread {
                while (WalletManagerAndroid.isInitialized() &&
                    WalletManagerAndroid.getInstance().progress < 100) {
                    Thread.sleep(500)
                    val p = WalletManagerAndroid.getInstance().progress
                    requireActivity().runOnUiThread {
                        updateProgressUI(p)
                    }
                }
                requireActivity().runOnUiThread {
                    updateProgressUI(100)
                    binding.bitcoinProgressContinue.text = "Continue"
                }
            }
        }
    }

    private fun updateProgressUI(progress: Int) {
        binding.bitcoinDownloadPercentage.text = "$progress%"
        binding.bitcoinDownloadProgress.progress = progress
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment BlockchainDownloading.
         */
        @JvmStatic
        fun newInstance() = P2PBlockchainDownloadFragment()
    }
}
