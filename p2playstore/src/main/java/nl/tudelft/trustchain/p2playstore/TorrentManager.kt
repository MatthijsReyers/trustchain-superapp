package nl.tudelft.trustchain.p2playstore

import android.util.Log
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import kotlinx.coroutines.*
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.foc.util.ExtensionUtils.Companion.TORRENT_EXTENSION
import nl.tudelft.trustchain.foc.util.MagnetUtils.Companion.MAGNET_HEADER_STRING
import java.io.File
import java.io.FileOutputStream
import java.util.*

class TorrentManager(cacheDir: File) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * JlibTorrent session manager, this manages the state/downloading of the torrents/magnet links
     * that point to the actual APK files that make up the apps.
     */
    private val sessionManager: SessionManager = SessionManager()

    /**
     * Location in file system where the downloaded apk files for the different apps are stored.
     */
    private val appsDirectory = File(cacheDir, "p2p-apps")

    init {
        if (!appsDirectory.exists()) {
            appsDirectory.mkdirs()
        }
    }

    private val torrentHandles = ArrayList<TorrentHandle>();
    private val torrentInfos = ArrayList<TorrentInfo>();

//    TODO: Replace with magnet utils..
    fun getHashOfMagnetLink(link: String): String {
        // Find the query part after '?'
        val queryStart = link.indexOf('?')
        if (queryStart == -1 || queryStart == link.length - 1) return ""

        val query = link.substring(queryStart + 1)
        val params = query.split('&')

        for (param in params) {
            if (param.startsWith("xt=")) {
                val value = param.substringAfter("xt=")
                if (value.startsWith("urn:btih:")) {
                    return value.substringAfter("urn:btih:")
                }
            }
        }
        return ""
    }

    /**
     * Actually starts the torrent manager and loads previously started/downloaded torrents and
     * configures the torrent client to continue downloading/seeding those.
     */
    fun start() {
        this.populateKnownTorrents()
        this.initializeTorrentSession()

        val sp = SettingsPack()
        sp.seedingOutgoingConnections(true)
        val params = SessionParams(sp)
        this.sessionManager.start(params)

        scope.launch {
            seedTorrents()
        }
        scope.launch {
            downloadTorrents()
        }
    }

    /**
     * This method looks inside the apps directory for .torrent files created in a previous session
     * that we can immediately open again to resume downloading/seeding
     */
    private fun populateKnownTorrents() {
        appsDirectory.listFiles()?.forEachIndexed { _, file ->
            if (file.name.endsWith(TORRENT_EXTENSION)) {
                TorrentInfo(file).let { torrentInfo ->
                    if (torrentInfo.isValid) {
                        if (!torrentInfos.any { it.infoHash() == torrentInfo.infoHash() }) {
                            torrentInfos.add(torrentInfo)
                        }
                    }
                }
            }
        }
        Log.d("P2P.TorrentManager", "Imported ${torrentInfos.size} known torrents")
    }

    /**
     * For a given magnet link, try to find if we have already registered/downloaded the torrent it
     * points to
     */
    private fun findTorrentInfo(magnetLink: String): TorrentInfo? {
        if (!magnetLink.startsWith(MAGNET_HEADER_STRING)) {
            Log.e("P2P.TorrentManager", "Magnet link does not start with correct header")
            return null;
        }
        // TODO: Replace with Magnet utils...
        val magnetHash: String = this.getHashOfMagnetLink(magnetLink)
        for (info in this.torrentInfos) {
            val knownHash: String = this.getHashOfMagnetLink(info.makeMagnetUri())
            if (knownHash == magnetHash) {
                return info;
            }
        }
        return null;
    }

    /**
     * Configure the jlibtorrent session manager and callbacks to inform us of the state of the
     * torrents.
     */
    private fun initializeTorrentSession() {
        sessionManager.addListener(
            object : AlertListener {
                override fun types(): IntArray? {
                    return null
                }
                override fun alert(alert: Alert<*>) {
                    when (alert.type()) {
                        AlertType.ADD_TORRENT -> {
                            Log.d("P2P.TorrentManager", "Added torrent $alert")
                            (alert as AddTorrentAlert).handle().resume()
                        }
                        AlertType.BLOCK_FINISHED -> {
                            Log.d("P2P.TorrentManager", "Block finished $alert")
                            val a = alert as BlockFinishedAlert
                            val p = (a.handle().status().progress() * 100).toInt()
                            Log.d("P2P.TorrentManager", "Progress $p for ${a.torrentName()}")
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val a = alert as TorrentFinishedAlert
                            Log.d("P2P.TorrentManager", "Download finished for ${a.torrentName()}")
                        }
                        else -> {
                        }
                    }
                }
            }
        )
    }

    /**
     * Resumes seeding previously downloaded apps to
     */
    private suspend fun seedTorrents() {
        while (scope.isActive) {
            try {
                // TODO: Actually implement this function
            }
            catch (e: Exception) {
                Log.e("P2P.TorrentManager", "Exception while seeding apps")
            }
            delay(30_000)
        }
    }

    /**
     * Resumes downloading any unfinished torrents from previous sessions.
     */
    private suspend fun downloadTorrents() {
        Log.d("P2P.TorrentManager", "Downloading torrents")
        while (scope.isActive) {
            try {
                // TODO: Actually implement this function
            }
            catch (e: Exception) {
                Log.e("P2P.TorrentManager", "Exception while downloading apps")
            }
            delay(30_000)
        }
    }

    /**
     * Downloads the specific version of the app described by the given block.
     */
    fun downloadApp(block: TrustChainBlock) {
        val magnetLink = block.transaction["magnetLink"]
        if (magnetLink !is String) {
            throw Exception("block ${block.hashNumber} does not describe a p2p app")
        }

        // Have we already downloaded this app?
        var torrentInfo = this.findTorrentInfo(magnetLink);
        if (torrentInfo != null) {
            Log.d("P2P.TorrentManager", "Magnet link was already known")
            // TODO: Maybe check if it was actually finished or restart if there was a failure?
            return;
        }

        this.waitFor10Nodes();

        torrentInfo = this.downloadTorrentInfo(magnetLink)
        this.torrentInfos.add(torrentInfo)

        // TODO: Replace with magnet utils.
        val torrentHash = this.getHashOfMagnetLink(magnetLink)

        // Create a .torrent file for this torrent so we can resume downloading/seeding after
        // restarting the app without needing to download the torrent info from someone else first.
        val entry: Entry = torrentInfo.toEntry()
        val torrentFile = File(this.appsDirectory, "${torrentHash}.torrent")
        FileOutputStream(torrentFile).use { fos -> fos.write(entry.bencode()) }

        // Now finally actually start the download process.
        val destDir = File(this.appsDirectory, "${torrentHash}")
        this.sessionManager.download(torrentInfo, destDir)
    }

    /**
     * Downloads all the required meta data in order to actually be able to download the torrent
     * the magnet file points to. This is only needed if we have not yet saved this data to the
     * cache folder as a .torrent file.
     */
    private fun downloadTorrentInfo(magnetLink: String): TorrentInfo {
        try {
            val data: ByteArray = sessionManager.fetchMagnet(magnetLink, 30)
            return TorrentInfo.bdecode(data)
        }
        catch (_err: Throwable) {
            throw Exception("Failed to download torrent info");
        }
    }

    /***
     * Blocks until the DHT to contain at least 10 nodes, this is taken from FOC, presumably so we
     * can be relatively certain that we can at least find the info we want,
     */
    private fun waitFor10Nodes() {
        val timer = Timer()
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    val nodes = sessionManager.stats().dhtNodes()
                    // wait for at least 10 nodes in the DHT.
                    if (nodes >= 10) {
                        Log.i("P2P.TorrentManager", "DHT contains $nodes nodes")
                        // signal.countDown();
                        timer.cancel()
                    }
                }
            },
            0,
            1000
        )
    }
}
