package nl.tudelft.trustchain.p2playstore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.FragmentHomeBinding

class HomeFragment : BaseFragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var allDaoAdapter: DaoAdapter? = null
    private var myDaoAdapter: DaoAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupClickListeners()

        if (WalletManagerAndroid.isInitialized()) {
            loadDaoData()
        } else {
            android.util.Log.w("P2PlayStore", "WalletManager is not initialized.")
        }

        createWalletIfNeeded()
    }

    private fun setupRecyclerViews() {
        binding.rvTopApps.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
        binding.rvRecommended.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
    }

    private fun setupClickListeners() {
        binding.seeAllTopApps.setOnClickListener {
            findNavController().navigate(R.id.joinDaoFragment)
        }

        binding.seeAllRecommended.setOnClickListener {
            findNavController().navigate(R.id.joinDaoFragment)
        }
    }

    private fun createWalletIfNeeded() {
        val wallets = this.p2playStore.discoverSharedWallets()
        android.util.Log.d("P2PlayStore", "Found ${wallets.size} wallets")

        if (wallets.isEmpty()) {
            android.util.Log.d("P2PlayStore", "No wallets found, creating one...")
            try {
                this.p2playStore.createBitcoinGenesisWallet(540, 1, this.requireContext())
            } catch (e: Exception) {
                android.util.Log.e("P2PlayStore", "Failed to create wallet: ${e.message}")
            }
        }
    }

    private fun loadDaoData() {
        if (!isAdded) return

        lifecycleScope.launch {
            try {
                val allDaos = withContext(Dispatchers.IO) {
                    getP2pStoreCommunity().discoverSharedWallets()
                }

                val myDaos = withContext(Dispatchers.IO) {
                    getP2pStoreCommunity().fetchLatestJoinedSharedWalletBlocks()
                }

                if (isAdded) {
                    updateAllDaoList(allDaos)
                    updateMyDaoList(myDaos)
                }
            } catch (e: Exception) {
                android.util.Log.e("P2PlayStore", "Error loading DAO data: ${e.message}")

                if (isAdded) {
                    createDummyData()
                }
            }
        }
    }

    private fun createDummyData() {
        val dummyDaos = createDummyDaoBlocks()
        updateAllDaoList(dummyDaos)
        updateMyDaoList(dummyDaos.take(2))
    }

    private fun createDummyDaoBlocks(): List<TrustChainBlock> {
        return emptyList()
    }

    private fun updateAllDaoList(daoList: List<TrustChainBlock>) {
        if (!isAdded) return

        android.util.Log.d("P2PlayStore", "Updating All DAOs list with ${daoList.size} items.")
        allDaoAdapter = DaoAdapter(
            daoList,
            object : DaoAdapter.OnItemClickListener {
                override fun onItemClick(daoBlock: TrustChainBlock) {
                    android.util.Log.d("P2PlayStore", "Navigating to joinDaoFragment from All DAOs")
                    try {
                        findNavController().navigate(R.id.joinDaoFragment)
                    } catch (e: Exception) {
                        android.util.Log.e("P2PlayStore", "Navigation error: ${e.message}")
                    }
                }
            }
        )
        binding.rvTopApps.adapter = allDaoAdapter
    }

    private fun updateMyDaoList(daoList: List<TrustChainBlock>) {
        if (!isAdded) return

        android.util.Log.d("P2PlayStore", "Updating My DAOs list with ${daoList.size} items.")
        myDaoAdapter = DaoAdapter(
            daoList,
            object : DaoAdapter.OnItemClickListener {
                override fun onItemClick(daoBlock: TrustChainBlock) {
                    android.util.Log.d("P2PlayStore", "Navigating to joinDaoFragment from My DAOs")
                    try {
                        findNavController().navigate(R.id.joinDaoFragment)
                    } catch (e: Exception) {
                        android.util.Log.e("P2PlayStore", "Navigation error: ${e.message}")
                    }
                }
            }
        )
        binding.rvRecommended.adapter = myDaoAdapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
