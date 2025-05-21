package nl.tudelft.trustchain.p2playstore


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.ui.bitcoin.SharedWalletListAdapter
import nl.tudelft.trustchain.p2playstore.databinding.FragmentHomeBinding
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.ui.BaseFragment

class HomeFragment : BaseFragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var allDaoAdapter: SharedWalletListAdapter? = null
    private var myDaoAdapter: SharedWalletListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState);
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTopApps.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecommended.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        if (WalletManagerAndroid.isInitialized()) {
            loadDaoData()
        } else {
            android.util.Log.w("P2PlayStore", "WalletManager is not initialized.")
        }

        val wallets = this.p2pStore.discoverSharedWallets();
        println("====================================")
        println("wallets: $wallets.length")
        for (wallet in wallets) {
            println(" - wallet: $wallet ${wallet.blockId}")
        }
        println("====================================")

        if (wallets.isEmpty()) {
            println("No wallets found creating one now..")
            try {
                this.p2pStore.createBitcoinGenesisWallet(
                    540, 1, this.requireContext()
                )
            }
            catch (e: Exception) {
                println("Failed to create wallet, do you have sufficient funds?\n $e")
            }
        }

        binding.seeAllTopApps.setOnClickListener {
            findNavController().navigate(R.id.joinDaoFragment)
        }

        binding.seeAllRecommended.setOnClickListener {
            findNavController().navigate(R.id.joinDaoFragment)
        }
    }

    private fun loadDaoData() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val allDaos = try {
                    getP2pStoreCommunity().discoverSharedWallets()
                } catch (e: Exception) {
                    android.util.Log.e("P2PlayStore", "Error fetching all DAOs: ${e.message}")
                    emptyList()
                }

                val myDaos = try {
                    getP2pStoreCommunity().fetchLatestJoinedSharedWalletBlocks()
                } catch (e: Exception) {
                    android.util.Log.e("P2PlayStore", "Error fetching my DAOs: ${e.message}")
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    // Update UI with fetched data
                    updateAllDaoList(allDaos)
                    updateMyDaoList(myDaos)
                }
            }
        }
    }

    private fun updateAllDaoList(daoList: List<TrustChainBlock>) {
        android.util.Log.d("P2PlayStore", "Updating All DAOs list with ${daoList.size} items.")
    }

    private fun updateMyDaoList(daoList: List<TrustChainBlock>) {
        android.util.Log.d("P2PlayStore", "Updating My DAOs list with ${daoList.size} items.")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
