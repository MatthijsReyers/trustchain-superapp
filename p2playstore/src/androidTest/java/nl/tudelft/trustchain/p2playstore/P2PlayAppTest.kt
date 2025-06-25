package nl.tudelft.trustchain.p2playstore

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.trustchain.p2playstore.P2pStoreCommunity.Companion.JOIN_BLOCK
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.transactionData.JoinDaoTransactionData
import org.junit.Assert.assertNotNull
import org.junit.Test

class P2PlayAppTest : TrustChainTest() {

    private val entranceFee = 120L
    private val iconIndex = 3
    private val appName = "Test app"
    private val appDescription = "This app is created by a unit test"
    private val magnetLink = "magnet:?xt=urn:btih:9f65cf30a02d654151bd26108a6fe91f7c000409" +
        "&dn=test-app.apk&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
    private val category = "Democracy"
    val threshold = 55

    @Test
    fun createAppTest() {
        this.init()

        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

        val data = P2playApp.createApp(
            entranceFee,
            iconIndex,
            appName,
            appDescription,
            magnetLink,
            category,
            threshold,
            context
        ).getData()

        val trustChain: TrustChainCommunity = IPv8Android.getInstance().getOverlay()!!
        val appBlock = trustChain.database.getBlocksWithType(JOIN_BLOCK)
            .map { b -> JoinDaoTransactionData(b).getData() }
            .find { app -> app.DAO_ID == data.DAO_ID }

        assertNotNull(
            "There should exist a JOIN block after creating an app",
            appBlock
        )
    }
}
