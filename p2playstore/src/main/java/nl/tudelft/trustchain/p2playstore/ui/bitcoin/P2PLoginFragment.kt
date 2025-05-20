package nl.tudelft.trustchain.p2playstore.ui.bitcoin
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.currencyii.coin.*
import nl.tudelft.trustchain.p2playstore.databinding.FragmentP2pLoginBinding
import nl.tudelft.trustchain.p2playstore.ui.BaseFragment
import nl.tudelft.trustchain.p2playstore.ui.bitcoin.P2PLoginFragmentDirections
import java.io.File


class P2PLoginFragment : BaseFragment(R.layout.fragment_p2p_login) {
    @Suppress("ktlint:standard:property-naming") // False positive
    private var _binding: FragmentP2pLoginBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
//        hideNavBar()
        _binding = FragmentP2pLoginBinding.inflate(inflater, container, false)
        return binding.root
    }
//    @Deprecated("Deprecated in Java")
//    override fun onActivityCreated(savedInstanceState: Bundle?) {
//        @Suppress("DEPRECATION")
//        super.onActivityCreated(savedInstanceState)
//
//        binding.loadProductionWallet.setOnClickListener {
//            loadWallet(BitcoinNetworkOptions.PRODUCTION)
//        }
//
//        binding.loadRegtestWallet.setOnClickListener {
//            loadWallet(BitcoinNetworkOptions.REG_TEST)
//        }
//
//        binding.loadTestnetWallet.setOnClickListener {
//            loadWallet(BitcoinNetworkOptions.TEST_NET)
//        }
//    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loadProductionWallet.setOnClickListener {
            loadWallet(BitcoinNetworkOptions.PRODUCTION)
        }
        binding.loadTestnetWallet.setOnClickListener {
            loadWallet(BitcoinNetworkOptions.TEST_NET)
        }
        binding.loadRegtestWallet.setOnClickListener {
            loadWallet(BitcoinNetworkOptions.REG_TEST)
        }
    }


    private fun loadWallet(params: BitcoinNetworkOptions) {
        // Close the current wallet manager if there is one running, blocks thread until it is closed
        if (WalletManagerAndroid.isInitialized()) {
            WalletManagerAndroid.close()
        }

        val hideWallets =
            arrayListOf(
                BitcoinNetworkOptions.TEST_NET,
                BitcoinNetworkOptions.PRODUCTION,
                BitcoinNetworkOptions.REG_TEST
            )
        hideWallets.remove(params)

        // Make sure to hide any other wallets that exists, when creating a new wallet
        hideWalletFiles(hideWallets)

        // Initialize wallet manager
        val config = WalletManagerConfiguration(params)
        WalletManagerAndroid
            .Factory(this.requireContext().applicationContext)
            .setConfiguration(config)
            .init()

        findNavController().navigate(
            P2PLoginFragmentDirections
                .actionP2pLoginFragmentToP2pblockchainDownloadFragment(parent = R.id.p2pLoginFragment)
        )
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = P2PLoginFragment()
    }

    /**
     * This function "hides" stored wallets of a certain network type by renaming them.
     */
    private fun hideWalletFiles(walletToHide: ArrayList<BitcoinNetworkOptions>) {
        val vWalletFileMainNet =
            File(
                this.requireContext().applicationContext.filesDir,
                "$MAIN_NET_WALLET_NAME.wallet"
            )
        val vChainFileMainNet =
            File(
                this.requireContext().applicationContext.filesDir,
                "$MAIN_NET_WALLET_NAME.spvchain"
            )
        val vWalletFileTestNet =
            File(
                this.requireContext().applicationContext.filesDir,
                "$TEST_NET_WALLET_NAME.wallet"
            )
        val vChainFileTestNet =
            File(
                this.requireContext().applicationContext.filesDir,
                "$TEST_NET_WALLET_NAME.spvchain"
            )
        val vWalletFileRegTest =
            File(
                this.requireContext().applicationContext.filesDir,
                "$REG_TEST_WALLET_NAME.wallet"
            )
        val vChainFileRegTest =
            File(
                this.requireContext().applicationContext.filesDir,
                "$REG_TEST_WALLET_NAME.spvchain"
            )

        val fileSuffix = System.currentTimeMillis()

        if (walletToHide.contains(BitcoinNetworkOptions.PRODUCTION)) {
            if (vWalletFileMainNet.exists()) {
                vWalletFileMainNet.renameTo(
                    File(
                        this.requireContext().applicationContext.filesDir,
                        "${MAIN_NET_WALLET_NAME}_backup_main_net_wallet_$fileSuffix.wallet"
                    )
                )
            }
            if (vChainFileMainNet.exists()) {
                vChainFileMainNet.renameTo(
                    File(
                        this.requireContext().applicationContext.filesDir,
                        "${MAIN_NET_WALLET_NAME}_backup_main_net_spvchain_$fileSuffix.spvchain"
                    )
                )
            }
            Log.w("Coin", "Renamed MainNet file")
        }
        if (walletToHide.contains(BitcoinNetworkOptions.REG_TEST)) {
            if (vWalletFileRegTest.exists()) {
                vWalletFileMainNet.renameTo(
                    File(
                        this.requireContext().applicationContext.filesDir,
                        "${REG_TEST_WALLET_NAME}_backup_reg_test_wallet_$fileSuffix.wallet"
                    )
                )
            }
            if (vChainFileRegTest.exists()) {
                vChainFileMainNet.renameTo(
                    File(
                        this.requireContext().applicationContext.filesDir,
                        "${REG_TEST_WALLET_NAME}_backup_reg_test_spvchain_$fileSuffix.spvchain"
                    )
                )
            }
            Log.w("Coin", "Renamed RegTest file")
        }
        if (walletToHide.contains(BitcoinNetworkOptions.TEST_NET)) {
            if (vWalletFileTestNet.exists()) {
                vWalletFileTestNet.renameTo(
                    File(
                        this.requireContext().applicationContext.filesDir,
                        "${TEST_NET_WALLET_NAME}_backup_test_net_wallet_$fileSuffix.wallet"
                    )
                )
            }
            if (vChainFileTestNet.exists()) {
                vChainFileTestNet.renameTo(
                    File(
                        this.requireContext().applicationContext.filesDir,
                        "${TEST_NET_WALLET_NAME}_backup_test_net_spvchain_$fileSuffix.spvchain"
                    )
                )
            }
            Log.w("Coin", "Renamed TestNet file")
        }
    }
}
