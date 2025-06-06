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
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils.parseMagnet

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

        if (WalletManagerAndroid.isInitialized()) {
            loadDaoData()
        } else {
            Log.w("P2PlayStore", "WalletManager is not initialized.")
        }
    }

    private fun setupRecyclerViews() {
        binding.rvTopApps.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
        binding.rvRecommended.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
    }

    private fun setupClickListeners() {
        binding.rvTopApps.setOnClickListener {
            findNavController().navigate(R.id.joinDaoFragment)
        }

        binding.rvRecommended.setOnClickListener {
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
        val etMagnetLink = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMagnetLink) ?: return
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
            val magnetLink = etMagnetLink.text.toString().trim()
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

            if (magnetLink.isEmpty()) {
                etMagnetLink.error = "Magnet Link is required"
                return@setOnClickListener
            }
            try {
                parseMagnet(magnetLink)
            } catch (e: Exception) {
                etMagnetLink.error = "Magnet link could not be parsed: ${e.message}"
                return@setOnClickListener
            }
            if (magnetLink.take(7) != "magnet:") {
                etMagnetLink.error = "Invalid magnet link provided: $magnetLink"
                return@setOnClickListener
            }

            // Create DAO
            createDao(name, description, entranceFee, votingThreshold, selectedIconIndex, category, magnetLink)
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
        category: String,
        magnetLink: String
    ) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {

                    p2playStore.createBitcoinGenesisWallet(
                        entranceFee,
                        iconIndex,
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
                Log.e("P2PlayStore", "Error creating DAO: ${e.message}")
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
                Log.e("P2PlayStore", "Error loading DAO data: ${e.message}")
            }
        }
    }

    private fun updateAllDaoList(daoList: List<TrustChainBlock>) {
        if (!isAdded) return

        binding.allApps.text = "All apps (${daoList.size})"

        Log.d("P2PlayStore", "Updating All DAOs list with ${daoList.size} items.")
        allDaoAdapter = DaoAdapter(
            daoList,
            object : DaoAdapter.OnItemClickListener {
                override fun onItemClick(daoBlock: TrustChainBlock) {
                    Log.d("P2PlayStore", "Navigating to joinDaoFragment from All DAOs")
                    try {
                        val bundle = Bundle()
                        bundle.apply {
                            putByteArray("publicKey", daoBlock.publicKey);
                            putInt("sequenceNumber", daoBlock.sequenceNumber.toInt());
                        }
                        findNavController().navigate(R.id.appDetails, bundle)
                    } catch (e: Exception) {
                        Log.e("P2PlayStore", "Navigation error: ${e.message}")
                    }
                }
            }
        )
        binding.rvTopApps.adapter = allDaoAdapter
    }

    private fun updateMyDaoList(daoList: List<TrustChainBlock>) {
        if (!isAdded) return

        binding.installedApps.text = "Installed apps (${daoList.size})"

        Log.d("P2PlayStore", "Updating My DAOs list with ${daoList.size} items.")
        myDaoAdapter = DaoAdapter(
            daoList,
            object : DaoAdapter.OnItemClickListener {
                override fun onItemClick(daoBlock: TrustChainBlock) {
                    Log.d("P2PlayStore", "Navigating to joinDaoFragment from My DAOs")
                    try {
                        val bundle = Bundle()
                        bundle.apply {
                            putByteArray("publicKey", daoBlock.publicKey);
                            putInt("sequenceNumber", daoBlock.sequenceNumber.toInt());
                        }
                        findNavController().navigate(R.id.appDetails, bundle)
                    } catch (e: Exception) {
                        Log.e("P2PlayStore", "Navigation error: ${e.message}")
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
