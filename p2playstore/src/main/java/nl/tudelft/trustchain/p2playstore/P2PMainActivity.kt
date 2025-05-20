package nl.tudelft.trustchain.p2playstore

import android.os.Bundle
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import nl.tudelft.trustchain.common.BaseActivity


import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
//import nl.tudelft.trustchain.currencyii.ui.bitcoin.DAOLoginChoiceFragment
import nl.tudelft.trustchain.currencyii.ui.bitcoin.BlockchainDownloadFragment
import nl.tudelft.trustchain.p2playstore.ui.bitcoin.P2PLoginFragment
import nl.tudelft.trustchain.p2playstore.ui.bitcoin.P2PBlockchainDownloadFragment

class P2PMainActivity() : BaseActivity() {

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
        AppBarConfiguration(p2pTopLevelDestinationIds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        performInitialNavigation()
    }


    private fun performInitialNavigation() {
        val navController = findNavController(nl.tudelft.trustchain.common.R.id.navHostFragment)
        Log.d("P2PNav", "Current Destination ID before initial nav: ${navController.currentDestination?.id}")
        Log.d("P2PNav", "WalletManager Initialized: ${WalletManagerAndroid.isInitialized()}")

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
                .setPopUpTo(navController.graph.startDestinationId, true) // Clears back stack to the graph's defined start
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
            Log.i("P2PStore", "Back press on a top-level P2P destination. Consumed.")
        } else {
            super.onBackPressed()
        }
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
