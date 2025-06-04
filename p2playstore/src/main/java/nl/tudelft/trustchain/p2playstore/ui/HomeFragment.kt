package nl.tudelft.trustchain.p2playstore.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.FragmentHomeBinding

class HomeFragment : BaseFragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var allDaoAdapter: DaoAdapter? = null
    private var myDaoAdapter: DaoAdapter? = null

    /**
     * This background job tries to find new apps on the trustchain at a set time interval.
     */
    private lateinit var discoverNewAppsJob: Job

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        this.discoverNewAppsJob = lifecycleScope.launch {
            while (true) {
                discoverNewApps()
                delay(60_000 * 5)
            }
        }

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

        binding.btnCreateDao.setOnClickListener {
            showCreateDaoDialog()
        }
    }

    private fun showCreateDaoDialog() {
        val context = requireContext()
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.fragment_create_app_dialog, null)

        // Find views in the dialog layout
        val etDaoName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDaoName) ?: return
        val etDaoDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDaoDescription) ?: return
        val etDaoCategory = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDaoCategory) ?: return
        val etEntranceFee = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEntranceFee) ?: return
        val etVotingThreshold = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVotingThreshold) ?: return
        val ivDaoIcon = dialogView.findViewById<android.widget.ImageView>(R.id.ivDaoIcon)
        val btnSelectIcon = dialogView.findViewById<android.widget.Button>(R.id.btnSelectIcon)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnCreateDao = dialogView.findViewById<android.widget.Button>(R.id.btnCreateDao)

        // Available icons (same as in DaoAdapter)
        val availableIcons = listOf(
            R.drawable.ic_bitcoin,
            R.drawable.ic_account_balance_wallet_black_24dp,
            R.drawable.ic_group_work_black_24dp,
            R.drawable.ic_device_hub_black_24dp
        )
        var selectedIconIndex = 0

        // Set default values
        etDaoCategory.setText("Democracy")
        etEntranceFee.setText("100")
        etVotingThreshold.setText("60")

        // Icon selection functionality
        btnSelectIcon?.setOnClickListener {
            val iconNames = arrayOf("Bitcoin", "Wallet", "Group", "Network")

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Select DAO Icon")
                .setSingleChoiceItems(iconNames, selectedIconIndex) { dialog, which ->
                    selectedIconIndex = which
                    ivDaoIcon?.setImageResource(availableIcons[selectedIconIndex])
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        builder.setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()

        // Handle button clicks
        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnCreateDao?.setOnClickListener {
            val name = etDaoName.text.toString().trim()
            val description = etDaoDescription.text.toString().trim()
            val category = etDaoCategory.text.toString().trim()
            val entranceFeeStr = etEntranceFee.text.toString().trim()
            val votingThresholdStr = etVotingThreshold.text.toString().trim()

            // Validation
            if (name.isEmpty()) {
                etDaoName.error = "DAO name is required"
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                etDaoDescription.error = "DAO description is required"
                return@setOnClickListener
            }

            if (category.isEmpty()) {
                etDaoCategory.error = "DAO category is required"
                return@setOnClickListener
            }

            val entranceFee = try {
                entranceFeeStr.toLong()
            } catch (e: NumberFormatException) {
                etEntranceFee.error = "Invalid entrance fee"
                return@setOnClickListener
            }

            val votingThreshold = try {
                val threshold = votingThresholdStr.toInt()
                if (threshold < 1 || threshold > 100) {
                    etVotingThreshold.error = "Voting threshold must be between 1-100%"
                    return@setOnClickListener
                }
                threshold
            } catch (e: NumberFormatException) {
                etVotingThreshold.error = "Invalid voting threshold"
                return@setOnClickListener
            }

            // Create DAO
            createDao(name, description, entranceFee, votingThreshold, selectedIconIndex, category)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createDao(
        name: String,
        description: String,
        entranceFee: Long,
        votingThreshold: Int,
        iconIndex: Int,
        category: String
    ) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Generate a random magnet link for the DAO with icon information
                    val magnetLink = "magnet:?xt=urn:btih:${java.util.UUID.randomUUID().toString().replace("-", "")}&dn=${name.replace(" ", "-")}.apk&icon=$iconIndex"

                    p2playStore.createBitcoinGenesisWallet(
                        entranceFee,
                        name,
                        description,
                        magnetLink,
                        category,
                        votingThreshold,
                        requireContext()
                    )
                }

                android.widget.Toast.makeText(
                    requireContext(),
                    "DAO '$name' created successfully!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                // Reload the DAO data
                loadDaoData()
            } catch (e: Exception) {
                android.util.Log.e("P2PlayStore", "Error creating DAO: ${e.message}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Failed to create DAO: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun createWalletIfNeeded() {
        val wallets = this.p2playStore.fetchLatestJoinedSharedWalletBlocks()
        android.util.Log.d("P2PlayStore", "Found ${wallets.size} wallets")

        if (wallets.isEmpty()) {
            android.util.Log.d("P2PlayStore", "No wallets found, creating one...")
            try {
                this.p2playStore.createBitcoinGenesisWallet(
                    540,
                    "Demo app",
                    "A simple app to demonstrate the store works",
                    "magnet:?xt=urn:btih:9f65cf30a02d654151bd26108a6fe91f7c000409&dn=test-app.apk&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=http%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.skyts.net%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.ololosh.space%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.bittor.pw%3A1337%2Fannounce&tr=http%3A%2F%2Ftracker.bittor.pw%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.free-tracker.ga%3A6969%2Fannounce&tr=udp%3A%2F%2Fns-1.x-fins.com%3A6969%2Fannounce&tr=udp%3A%2F%2Fleet-tracker.moe%3A1337%2Fannounce&tr=udp%3A%2F%2Fisk.richardsw.club%3A6969%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce&tr=http%3A%2F%2Fwww.torrentsnipe.info%3A2701%2Fannounce&tr=http%3A%2F%2Fwww.genesis-sp.org%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker810.xyz%3A11450%2Fannounce&tr=http%3A%2F%2Ftracker.xiaoduola.xyz%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.vanitycore.co%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.sbsub.com%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker.ipv6tracker.org%3A80%2Fannounce&tr=http%3A%2F%2Ftracker.dmcomic.org%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker.corpscorp.online%3A80%2Fannounce&tr=http%3A%2F%2Ftracker.bz%3A80%2Fannounce&tr=http%3A%2F%2Ftracker.bt-hash.com%3A80%2Fannounce&tr=http%3A%2F%2Ft.jaekr.sh%3A6969%2Fannounce&tr=http%3A%2F%2Fshubt.net%3A2710%2Fannounce&tr=http%3A%2F%2Fservandroidkino.ru%3A80%2Fannounce&tr=http%3A%2F%2Fseeders-paradise.org%3A80%2Fannounce&tr=http%3A%2F%2Fretracker.spark-rostov.ru%3A80%2Fannounce&tr=http%3A%2F%2Fopen.trackerlist.xyz%3A80%2Fannounce&tr=http%3A%2F%2Fhighteahop.top%3A6960%2Fannounce&tr=http%3A%2F%2Ffinbytes.org%3A80%2Fannounce.php&tr=http%3A%2F%2Fbuny.uk%3A6969%2Fannounce&tr=http%3A%2F%2Fbt1.xxxxbt.cc%3A6969%2Fannounce",
                    "",
                    1,
                    this.requireContext()
                )
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
            }
        }
    }

    private fun updateAllDaoList(daoList: List<TrustChainBlock>) {
        if (!isAdded) return

        binding.allApps.text = "All apps (${daoList.size})"

        android.util.Log.d("P2PlayStore", "Updating All DAOs list with ${daoList.size} items.")
        allDaoAdapter = DaoAdapter(
            daoList,
            object : DaoAdapter.OnItemClickListener {
                override fun onItemClick(daoBlock: TrustChainBlock) {
                    android.util.Log.d("P2PlayStore", "Navigating to joinDaoFragment from All DAOs")
                    try {
                        val bundle = Bundle()
                        bundle.apply {
                            putByteArray("publicKey", daoBlock.publicKey);
                            putInt("sequenceNumber", daoBlock.sequenceNumber.toInt());
                        }
                        findNavController().navigate(R.id.appDetails, bundle)
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

        binding.installedApps.text = "Installed apps (${daoList.size})"

        android.util.Log.d("P2PlayStore", "Updating My DAOs list with ${daoList.size} items.")
        myDaoAdapter = DaoAdapter(
            daoList,
            object : DaoAdapter.OnItemClickListener {
                override fun onItemClick(daoBlock: TrustChainBlock) {
                    android.util.Log.d("P2PlayStore", "Navigating to joinDaoFragment from My DAOs")
                    try {
                        val bundle = Bundle()
                        bundle.apply {
                            putByteArray("publicKey", daoBlock.publicKey);
                            putInt("sequenceNumber", daoBlock.sequenceNumber.toInt());
                        }
                        findNavController().navigate(R.id.appDetails, bundle)
                    } catch (e: Exception) {
                        android.util.Log.e("P2PlayStore", "Navigation error: ${e.message}")
                    }
                }
            }
        )
        binding.rvRecommended.adapter = myDaoAdapter
    }

    /**
     * This method looks for undiscovered apps on the trustchain based on the currently connected
     * peers and their blocks.
     */
    private suspend fun discoverNewApps() {
        val trustChain = getTrustChainCommunity();
        Log.d("P2PlayStore", "I am peer ${trustChain.myPeer.publicKey}")
        val peers = trustChain.getPeers()
        Log.d("P2PlayStore", "Found ${peers.size} peers")
        for (peer in peers) {
            try {
                Log.d(
                    "P2PlayStore",
                    "Crawling peer ${peer.publicKey}"
                )
                trustChain.crawlChain(peer)
                val crawlResult = trustChain.database.getMutualBlocks(
                    peer.publicKey.keyToBin(), 1000
                )
                Log.d(
                    "P2PlayStore",
                    "Found ${crawlResult.size} new blocks"
                )
            }
            catch (err: Throwable) {
                Log.e(
                    "P2PlayStore",
                    "Crawling failed for: ${peer.publicKey}. $err."
                )
            }
            this.loadDaoData();
        }
    }

    private fun loadDynamicCode(fileName: String) {
        try {
            val intent = Intent(requireContext(), ExecutionActivity::class.java)
            intent.putExtra(
                "fileName",
                "${requireContext().cacheDir}/${fileName.split("/").last()}"
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
