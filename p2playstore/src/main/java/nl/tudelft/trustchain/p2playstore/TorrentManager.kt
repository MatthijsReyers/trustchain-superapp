package nl.tudelft.trustchain.p2playstore

import android.util.Log
import android.widget.Toast
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import kotlinx.coroutines.*
import mu.KotlinLogging
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.foc.util.ExtensionUtils.Companion.supportedAppExtensions
import nl.tudelft.trustchain.foc.util.ExtensionUtils.Companion.TORRENT_EXTENSION
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.ADDRESS_TRACKER
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.ADDRESS_TRACKER_APPENDER
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.constructMagnetLink
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.DISPLAY_NAME_APPENDER
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.MAGNET_HEADER_STRING
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.PRE_HASH_STRING
import nl.tudelft.trustchain.common.freedomOfComputing.AppPayload
import nl.tudelft.trustchain.foc.DOWNLOAD_DELAY
import nl.tudelft.trustchain.foc.GOSSIP_DELAY
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.Pair

class TorrentManager(activity: P2PlayStoreMainActivity) {

    /**
     * Location in file system where the torrent files are stored, note that this folder seems to
     * be shared among all apps in the super app and thus contains files and torrents that do not
     * belong to the P2PlayStore
     */
    private val appDirectory = activity.applicationContext.cacheDir

    private val scope = CoroutineScope(Dispatchers.IO)

    public val sessionManager: SessionManager = SessionManager()

    private val torrentHandles = ArrayList<TorrentHandle>();
    private val torrentInfos = ArrayList<TorrentInfo>();

    public fun start() {
        this.populateKnownTorrents()
        this.initializeTorrentSession()

        val sp = SettingsPack()
        sp.seedingOutgoingConnections(true)
        val params = SessionParams(sp)
        Log.d("TorrentManager", "Starting session manager")
        this.sessionManager.start(params)

        scope.launch {
            seedTorrents()
        }
        scope.launch {
            downloadTorrents()
        }
    }

    private fun populateKnownTorrents() {
        Log.d("TorrentManager", "populating known Torrents")
        Log.d("TorrentManager", "found: ${appDirectory.listFiles()}")
        appDirectory.listFiles()?.forEachIndexed { _, file ->
            if (file.name.endsWith(TORRENT_EXTENSION)) {
                Log.d("TorrentManager", "found torrent file: ${file.name}")
                TorrentInfo(file).let { torrentInfo ->
                    if (torrentInfo.isValid) {
//                        TODO: FOC performs some extra validation to ensure the torrent actually
//                         contains an APK file, which is probably smart but disabled for debugging
//                        if (isTorrentOkay(torrentInfo, appDirectory)) { }
                        if (!torrentInfos.any { it.infoHash() == torrentInfo.infoHash() }) {
                            torrentInfos.add(torrentInfo)
                        }
                    }
                }
            }
        }
        Log.d("TorrentManager", "${torrentInfos.size} torrents known")
    }

    public fun magnetLinkIsKnown(magnetLink: String): Boolean {
        if (!magnetLink.startsWith(MAGNET_HEADER_STRING)) {
            Log.e("TorrentManager", "Magnet link does not start with correct header")
            return false;
        }

//        return true;
        return false;
    }


    fun getMagnetLink(
        magnetLink: String,
        torrentName: String
    ) {
        if (!magnetLink.startsWith(MAGNET_HEADER_STRING)) {
            Log.e("TorrentManager", "Magnet link does not start with correct header")
            return
        }

        val startIndexName = magnetLink.indexOf(DISPLAY_NAME_APPENDER)
        val stopIndexName =
            if (magnetLink.contains(ADDRESS_TRACKER_APPENDER)) magnetLink.indexOf(ADDRESS_TRACKER)
            else magnetLink.length

        val magnetNameRaw = magnetLink.substring(startIndexName + 4, stopIndexName)
        Log.d("TorrentManager", "Magnet name raw: $magnetNameRaw")
        val magnetName = magnetNameRaw.replace('+', ' ', false)
        val magnetInfoHash = magnetLink.substring(PRE_HASH_STRING.length, startIndexName)
        Log.d("TorrentManager", "Magnet name: $magnetName")

        val sp = SettingsPack()
        sp.seedingOutgoingConnections(true)
        val params =
            SessionParams(sp)
        sessionManager.start(params)

        val timer = Timer()
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    val nodes = sessionManager.stats().dhtNodes()
                    // wait for at least 10 nodes in the DHT.
                    if (nodes >= 10) {
                        Log.i("TorrentManager", "DHT contains $nodes nodes")
                        // signal.countDown();
                        timer.cancel()
                    }
                }
            },
            0,
            1000
        )

        Log.i("TorrentManager", "Fetching the magnet uri, please wait...")
        val data: ByteArray?
        try {
            data = sessionManager.fetchMagnet(magnetLink, 30)
        } catch (e: Exception) {
            Log.e("TorrentManager", "Failed to retrieve the magnet: $e")
//            TODO: Handle failure/communicate it back to the user.
            return
        }

        var signal = CountDownLatch(0)

        if (data != null) {
            val torrentInfo = TorrentInfo.bdecode(data)
            sessionManager.download(torrentInfo, appDirectory)
            Log.e("TorrentManager", "Fetched info for torrent $torrentName, trying to download")
            signal.await(1, TimeUnit.MINUTES)

            if (signal.count.toInt() == 1) {
                Log.e("TorrentManager", "Download timed out")
                signal = CountDownLatch(0)
                sessionManager.find(torrentInfo.infoHash())?.let { torrentHandle ->
                    sessionManager.remove(torrentHandle)
                }
                Log.e("TorrentManager", "Failed to retrieve the magnet")
//            TODO: Handle failure/communicate it back to the user.
            } else {
                Log.i("TorrentManager", "Finished downloading torrent $magnetName")
//            TODO: Handle success/communicate it back to the user.
            }
        }
        else {
            Log.e("TorrentManager", "Failed to retrieve the magnet")
//            TODO: Handle failure/communicate it back to the user.
        }
    }

    private fun initializeTorrentSession() {
        sessionManager.addListener(
            object : AlertListener {
                override fun types(): IntArray? {
                    return null
                }
                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.ADD_TORRENT -> {
                            Log.d("TorrentManager", "Added torrent $alert")
                            (alert as AddTorrentAlert).handle().resume()
                        }
                        AlertType.BLOCK_FINISHED -> {
                            Log.d("TorrentManager", "Block finished $alert")
                            val a = alert as BlockFinishedAlert
                            val p = (a.handle().status().progress() * 100).toInt()
                            Log.d("TorrentManager", "Progress $p for ${a.torrentName()}")
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val a = alert as TorrentFinishedAlert
                            Log.d("TorrentManager", "Download finished for ${a.torrentName()}")
                        }
                        else -> {
                        }
                    }
                }
            }
        )
    }

    private suspend fun seedTorrents() {
        while (scope.isActive) {
            try {
            }
            catch (e: Exception) {
                Log.e("TorrentManager", "Exception while seeding apps")
            }
            delay(GOSSIP_DELAY)
        }
    }

    private suspend fun downloadTorrents() {
        Log.d("TorrentManager", "Downloading torrents")

    }

}
