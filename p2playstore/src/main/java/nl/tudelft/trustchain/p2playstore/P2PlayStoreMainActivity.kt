package nl.tudelft.trustchain.p2playstore

import android.os.Bundle
import android.util.Log
import androidx.core.app.NavUtils
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.frostwire.jlibtorrent.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.common.BaseActivity
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid

//import nl.tudelft.trustchain.currencyii.ui.bitcoin.DAOLoginChoiceFragment

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

        this.torrentManager = TorrentManager(this)

        // Always show the arrow button in the action bar so you can navigate back to the super app
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        this.torrentManager.start();

        this.initalizeTorrents();
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

    private fun initalizeTorrents() {
        val wallets = this.p2playStore.discoverSharedWallets()

        println("apps: ${wallets.size}")
        println("====================================")
        for (wallet in wallets) {
            val magnetLink: String? = wallet.transaction["magnetLink"] as? String;
            println("block: $wallet ${wallet.blockId}")
            println(" - name: ${wallet.transaction["name"]}")
            println(" - description: ${wallet.transaction["description"]}")
            println(" - magnetLink: $magnetLink")

            if (magnetLink != null && this.torrentManager.magnetLinkIsKnown(magnetLink)) {
                println(" - magnetLink is known")
            } else {
                println(" - magnetLink is not known")
            }
        }

//        this.torrentManager.getMagnetLink(
//            "magnet:?xt=urn:btih:1fecb556cfdd403728f841a67e8541cc1329b615&dn=5z4oVp1.jpeg&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=http%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.skyts.net%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.ololosh.space%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.bittor.pw%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.free-tracker.ga%3A6969%2Fannounce&tr=udp%3A%2F%2Fns-1.x-fins.com%3A6969%2Fannounce&tr=udp%3A%2F%2Fleet-tracker.moe%3A1337%2Fannounce&tr=udp%3A%2F%2Fisk.richardsw.club%3A6969%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce&tr=http%3A%2F%2Fwww.torrentsnipe.info%3A2701%2Fannounce&tr=http%3A%2F%2Fwww.genesis-sp.org%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker810.xyz%3A11450%2Fannounce&tr=http%3A%2F%2Ftracker.xiaoduola.xyz%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.vanitycore.co%3A6969%2Fannounce&tr=http%3A%2F%2Ftracker.sbsub.com%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker.ipv6tracker.org%3A80%2Fannounce&tr=http%3A%2F%2Ftracker.dmcomic.org%3A2710%2Fannounce&tr=http%3A%2F%2Ftracker.corpscorp.online%3A80%2Fannounce&tr=http%3A%2F%2Ftracker.bz%3A80%2Fannounce",
//            "testDownload"
//        );
    }

//    fun addTopLevelDestinationId(id: Int) {
//        topLevelDestinationIds = topLevelDestinationIds + id
//    }
//
//    fun removeTopLevelDestinationId(id: Int) {
//        topLevelDestinationIds = topLevelDestinationIds - id
//    }
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//    }

}
