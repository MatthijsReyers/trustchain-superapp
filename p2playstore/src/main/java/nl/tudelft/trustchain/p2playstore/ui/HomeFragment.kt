package nl.tudelft.trustchain.p2playstore.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock

import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.p2playstore.ExecutionActivity
import nl.tudelft.trustchain.p2playstore.P2PlayStoreMainActivity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.UPDATE_ACCEPTED_BLOCK
import nl.tudelft.trustchain.p2playstore.R
import nl.tudelft.trustchain.p2playstore.databinding.FragmentHomeBinding
import nl.tudelft.trustchain.p2playstore.utils.AppUtils
import nl.tudelft.trustchain.p2playstore.utils.AppUtils.printToast
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils.parseMagnet
import java.io.File

class HomeFragment : BaseFragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var allDaoAdapter: AppPreviewsAdapter? = null
    private var myDaoAdapter: AppPreviewsAdapter? = null

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()

        if (WalletManagerAndroid.isInitialized()) {
            updateAppsLists()
        } else {
            Log.w("P2PlayStore", "WalletManager is not initialized.")
        }

        binding.btnCreateDao.setOnClickListener {
            showCreateDaoDialog()
        }

        val testMagnet = "magnet:?xt=urn:btih:9f65cf30a02d654151bd26108a6fe91f7c000409&dn=test-app.apk&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=udp%3A%2F%2Fopen.free-tracker.ga%3A6969%2Fannounce&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dump.cl%3A6969%2Fannounce&tr=udp%3A%2F%2Fopentracker.io%3A6969%2Fannounce&tr=udp%3A%2F%2Fns-1.x-fins.com%3A6969%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce&tr=http%3A%2F%2Fwww.torrentsnipe.info%3A2701%2Fannounce&tr=http%3A%2F%2Ftracker810.xyz%3A11450%2Fannounce&tr=http%3A%2F%2Ftracker.xiaoduola.xyz%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.vanitycore.co%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.skyts.net%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.sbsub.com%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker.dmcomic.org%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker.bz%3A80%2Fannounce&tr=http%3A%2F%2Ftracker.bt-hash.com%3A80%2Fannounce&tr=http%3A%2F%2Ft.jaekr.sh%3A6969%2Fannounce"

        val magnetLink = MagnetUtils.parseMagnet(testMagnet)

        (activity as P2PlayStoreMainActivity).torrentManager.downloadMagnetLink(magnetLink)


        // Temporary for testing apk files....
        binding.testBtn.setOnClickListener {
            val applicationContext = requireContext()

            val apkPath = "${applicationContext.cacheDir}/p2p-apps/${magnetLink.infoHash}" +
                "/${magnetLink.displayName}"
            val apkFile = File(apkPath)

            if (!apkFile.exists() || !apkFile.isFile) {
                Log.e("P2P", "File not found or invalid: $apkFile")
                printToast(applicationContext, "No APK found connected to this DAO.")
                return@setOnClickListener
            }

            val intent = Intent(applicationContext, ExecutionActivity::class.java).apply {
                putExtra("fileName", apkPath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                applicationContext.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("P2P", "No activity found to handle intent for APK: $apkPath", e)
                printToast(applicationContext, "Unable to open APK – app component not found.")
            } catch (e: SecurityException) {
                Log.e("P2P", "Security exception when launching APK: $apkPath", e)
                printToast(applicationContext, "Permission denied to launch APK.")
            } catch (e: Exception) {
                Log.e("P2P", "Unexpected error launching APK: $apkPath", e)
                printToast(applicationContext, "Something went wrong when opening the APK.")
            }
        }

        this.discoverNewAppsJob = lifecycleScope.launch {
            while (true) {
                discoverNewApps()
                delay(60_000 * 5)
            }
        }

//        val wallet = WalletManagerAndroid.getInstance()
//        val amountBTC: Double = 0.05
//        val destination = "mgVz64BgaRMpKosY1fj1QCzETo2qMoLNpE"
//        val address = Address.fromString(wallet.params, destination)
//        val amountToSend = Coin.parseCoin(amountBTC.toString())
//        val sendRequest = SendRequest.to(address, amountToSend)
//        val sendResult = wallet.kit.wallet().sendCoins(sendRequest)
    }

    override suspend fun onChainUpdated(block: TrustChainBlock) {
        when (block.type) {
            // Did a new app (verion) just release?
            JOIN_BLOCK, UPDATE_ACCEPTED_BLOCK -> {
                this.updateAppsLists()
            }
        }
    }

    private fun setupRecyclerViews() {
        binding.rvTopApps.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
        binding.rvRecommended.layoutManager = GridLayoutManager(context, 2, GridLayoutManager.HORIZONTAL, false)
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
                .setTitle("Select App Icon")
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
                etDaoName.error = "App name is required"
                return@setOnClickListener
            }

            if (description.isEmpty()) {
                etDaoDescription.error = "App description is required"
                return@setOnClickListener
            }

            if (category.isEmpty()) {
                etDaoCategory.error = "App category is required"
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
                updateAppsLists()
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

    /**
     * Updates the UI to display all the apps the application knows about.
     */
    private fun updateAppsLists() {
        lifecycleScope.launch {
            try {
                val allApps = withContext(Dispatchers.IO) {
                    getP2pStoreCommunity().discoverAllApps()
                }

                binding.allApps.text = "All apps (${allApps.size})"
                allDaoAdapter = AppPreviewsAdapter(allApps)
                binding.rvTopApps.adapter = allDaoAdapter

                val myApps = allApps.filter { app -> app.isDaoMember() }

                binding.installedApps.text = "Installed apps (${myApps.size})"
                myDaoAdapter = AppPreviewsAdapter(myApps)
                binding.rvRecommended.adapter = myDaoAdapter
            }
            catch (e: Exception) {
                Log.e("P2PlayStore", "Error loading DAO data: ${e.message}")
            }
        }
    }

    /**
     * This method looks for undiscovered apps on the trustchain based on the currently connected
     * peers and their blocks.
     */
    private suspend fun discoverNewApps() {
        val trustChain = getTrustChainCommunity();
        val peers = trustChain.getPeers()
        Log.d("P2PlayStore", "Discovering new apps, found ${peers.size} peers")
        for (peer in peers) {
            try {
                trustChain.crawlChain(peer)
                val crawlResult = trustChain.database.getMutualBlocks(
                    peer.publicKey.keyToBin(), 1000
                )
            }
            catch (err: Throwable) {
                Log.e(
                    "P2PlayStore",
                    "Crawling peer failed for: ${peer.publicKey}. $err."
                )
            }
            this.updateAppsLists();
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
