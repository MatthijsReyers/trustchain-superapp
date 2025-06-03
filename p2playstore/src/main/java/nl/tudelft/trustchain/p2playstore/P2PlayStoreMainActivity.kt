package nl.tudelft.trustchain.p2playstore

import android.os.Bundle
import android.util.Log
import androidx.core.app.NavUtils
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.common.BaseActivity
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid

class P2PlayStoreMainActivity() : BaseActivity() {

    public lateinit var torrentManager: TorrentManager;

    override val navigationGraph = R.navigation.nav_graph_p2pstore

    private val p2pTopLevelDestinationIds = setOf(
        R.id.p2pLoginFragment,
        R.id.p2pblockchainDownloadFragment,
        R.id.homeFragment
    )

    /**
     * Configuration for the ActionBar, primarily defining top-level destinations.
     */
    override val appBarConfiguration: AppBarConfiguration by lazy {
        AppBarConfiguration(emptySet())
    }

    /**
     * For some asinine reason we need to overwrite this and add a custom way to return to the main
     * activity of the super app even though our setup is the same as all the other apps with a
     * back button and they do not have to do this..
     */
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.navHostFragment)
        val currentDest = navController.currentDestination?.id
        if (currentDest in p2pTopLevelDestinationIds) {
            NavUtils.navigateUpFromSameTask(this)
            return false;
        }
        return navController.navigateUp(appBarConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        performInitialNavigation()

        // Always show the arrow button in the action bar so you can navigate back to the super app
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        this.torrentManager = TorrentManager(this.applicationContext.cacheDir)
        this.torrentManager.start();
        this.initializeTorrents();
    }

    private fun performInitialNavigation() {
        val navController = findNavController(nl.tudelft.trustchain.common.R.id.navHostFragment)
        val currentDestinationId = navController.currentDestination?.id
        val targetDestinationId = when {
            !WalletManagerAndroid.isInitialized() -> {
                Log.d("P2PNav", "Condition: Wallet not initialized. Target: p2pLoginFragment")
                R.id.p2pLoginFragment
            }
            WalletManagerAndroid.getInstance().progress < 100 -> {
                Log.d("P2PNav", "Condition: Wallet progress < 100 (${WalletManagerAndroid.getInstance().progress}%). Target: p2pblockchainDownloadFragment")
                R.id.p2pblockchainDownloadFragment
            }
            else -> {
                Log.d("P2PNav", "Condition: Wallet initialized and progress >= 100. Target: homeFragment")
                R.id.homeFragment
            }
        }

        Log.d("P2PNav", "Final Target Destination ID: $targetDestinationId")

        if (currentDestinationId != targetDestinationId) {
            Log.d("P2PNav", "Navigating from $currentDestinationId to $targetDestinationId")
            val navOptions = NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, true, true) // Clears back stack to the graph's defined start
                .build()
            try {
                navController.navigate(targetDestinationId, null, navOptions)
                Log.i("P2PNav", "Successfully navigated to $targetDestinationId")
            } catch (e: IllegalArgumentException) {
                Log.e("P2PNav", "Navigation failed (IllegalArgumentException): ${e.message}. Graph may not be ready or destination invalid.")
            } catch (e: IllegalStateException) {
                Log.e("P2PNav", "Navigation failed (IllegalStateException): ${e.message}. NavController might not be ready.")
            }
        } else {
            Log.d("P2PNav", "Already at the target destination ($currentDestinationId). No navigation needed.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val navController = findNavController(nl.tudelft.trustchain.common.R.id.navHostFragment)
        val currentDestinationId = navController.currentDestination?.id

        if (currentDestinationId != null && p2pTopLevelDestinationIds.contains(currentDestinationId)) {
            NavUtils.navigateUpFromSameTask(this)
        } else {
            super.onBackPressed()
        }
    }

    protected fun getIpv8(): IPv8 {
        return IPv8Android.getInstance()
    }

    protected fun getP2pStoreCommunity(): P2pStoreCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("P2pStoreCommunity is not configured")
    }

    protected val p2playStore: P2pStoreCommunity by lazy {
        getP2pStoreCommunity()
    }

    protected fun getTrustChainCommunity(): TrustChainCommunity {
        return getIpv8().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    protected val trustChain: TrustChainCommunity by lazy {
        getTrustChainCommunity()
    }

    private fun initializeTorrents() {
        // Downloading torrents is a blocking operation so we cannot do this on the UI thread or the
        // whole app will block.
        val scope = CoroutineScope(Dispatchers.IO);
        scope.launch {
            val wallets = p2playStore.fetchLatestJoinedSharedWalletBlocks()
            println("apps: ${wallets.size}")
            println("====================================")
            for (wallet in wallets) {
                val name = wallet.transaction["name"]
                val magnetLink: String? = wallet.transaction["magnetLink"] as? String;

                println("block: $wallet ${wallet.blockId}")
                println(" - name: $name")
                println(" - description: ${wallet.transaction["description"]}")
                println(" - magnetLink: $magnetLink")

                try {
                    torrentManager.downloadApp(wallet);
                }
                catch (err: Throwable) {
                    Log.e("P2PlayStore", "App download failed: $err")
                }
            }
        }
    }
}
