package nl.tudelft.trustchain.p2playstore

import android.Manifest
import android.app.Activity
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.android.keyvault.AndroidCryptoProvider
import nl.tudelft.ipv8.attestation.schema.SchemaManager
import nl.tudelft.ipv8.attestation.trustchain.BlockSigner
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.TransactionValidator
import nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult
import nl.tudelft.ipv8.attestation.wallet.cryptography.bonehexact.BonehPrivateKey
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.bitcoin.WalletService
import nl.tudelft.trustchain.common.eurotoken.GatewayStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import org.junit.Rule
import org.junit.runner.RunWith
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch

@RunWith(AndroidJUnit4::class)
abstract class TrustChainTest() {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_CONNECT)

    private val app: Application = InstrumentationRegistry
        .getInstrumentation()
        .targetContext
        .applicationContext as Application

    fun init() {
        val activityScenario = launch(P2PlayStoreMainActivity::class.java)
        activityScenario.use { scenario ->
            scenario.onActivity { activity ->
                grantBluetoothConnectPermissionIfNeeded(activity)
            }
        }

        val config = IPv8Configuration(
            overlays = listOf(createTrustChainCommunity()),
            walkerInterval = 5.0
        )
        IPv8Android.Factory(app)
            .setConfiguration(config)
            .setPrivateKey(getPrivateKey())
            .init()
        initWallet()
        initTrustChain()
    }

    private fun createTrustChainCommunity(): OverlayConfiguration<TrustChainCommunity> {
        val blockTypesBcDisabled: Set<String> = setOf("eurotoken_join", "eurotoken_trade")
        val settings = TrustChainSettings(blockTypesBcDisabled)
        val driver = AndroidSqliteDriver(Database.Schema, app, "trustchain.db")
        val store = TrustChainSQLiteStore(Database(driver))
        val randomWalk = RandomWalk.Factory()
        return OverlayConfiguration(
            TrustChainCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

    fun grantBluetoothConnectPermissionIfNeeded(activity: Activity) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1234
            )
        }
    }

    private fun initWallet() {
        GlobalScope.launch {
            // Generate keys in a coroutine as this significantly impacts first launch.
            val ipv8 = IPv8Android.getInstance()
            ipv8.myPeer.identityPrivateKeySmall = getIdAlgorithmKey(PREF_ID_METADATA_KEY)
            ipv8.myPeer.identityPrivateKeyBig = getIdAlgorithmKey(PREF_ID_METADATA_BIG_KEY)
            ipv8.myPeer.identityPrivateKeyHuge = getIdAlgorithmKey(PREF_ID_METADATA_HUGE_KEY)
        }
    }

    private fun initTrustChain() {
        val ipv8 = IPv8Android.getInstance()
        val trustchain = ipv8.getOverlay<TrustChainCommunity>()!!
        val tr = TransactionRepository(trustchain, GatewayStore.getInstance(app))
        tr.initTrustChainCommunity()
        WalletService.createGlobalWallet(app.cacheDir ?: throw Error("CacheDir not found"))

        trustchain.registerTransactionValidator(
            BLOCK_TYPE,
            object : TransactionValidator {
                override fun validate(
                    block: TrustChainBlock,
                    database: TrustChainStore
                ): ValidationResult {
                    if (block.transaction["message"] != null || block.isAgreement) {
                        return ValidationResult.Valid
                    } else {
                        return ValidationResult.Invalid(listOf("Proposal must have a message"))
                    }
                }
            }
        )

        trustchain.registerBlockSigner(
            BLOCK_TYPE,
            object : BlockSigner {
                override fun onSignatureRequest(block: TrustChainBlock) {
                    trustchain.createAgreementBlock(block, mapOf<Any?, Any?>())
                }
            }
        )
    }


    private fun getIdAlgorithmKey(idFormat: String): BonehPrivateKey {
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val privateKey = prefs.getString(idFormat, null)

        val schemaManager = SchemaManager()
        schemaManager.registerDefaultSchemas()

        return if (privateKey == null) {
            // Generate a new key on the first launch
            val newKey = schemaManager.getAlgorithmInstance(idFormat).generateSecretKey()
            prefs.edit()
                .putString(idFormat, newKey.serialize().toHex())
                .apply()
            newKey
        } else {
            BonehPrivateKey.deserialize(privateKey.hexToBytes())!!
        }
    }

    private fun getPrivateKey(): PrivateKey {
        // Load a key from the shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val privateKey = prefs.getString(PREF_PRIVATE_KEY, null)
        return if (privateKey == null) {
            // Generate a new key on the first launch
            val newKey = AndroidCryptoProvider.generateKey()
            prefs.edit()
                .putString(PREF_PRIVATE_KEY, newKey.keyToBin().toHex())
                .apply()
            newKey
        } else {
            AndroidCryptoProvider.keyFromPrivateBin(privateKey.hexToBytes())
        }
    }

    companion object {
        private const val PREF_PRIVATE_KEY = "private_key"
        private const val PREF_ID_METADATA_KEY = "id_metadata"
        private const val PREF_ID_METADATA_BIG_KEY = "id_metadata_big"
        private const val PREF_ID_METADATA_HUGE_KEY = "id_metadata_huge"
        private const val PREF_ID_METADATA_RANGE_18PLUS_KEY = "id_metadata_range_18plus"
        private const val BLOCK_TYPE = "demo_block"
        private const val FIRST_RUN = "first_run"
    }
}
