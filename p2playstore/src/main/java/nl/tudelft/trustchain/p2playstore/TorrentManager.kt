package nl.tudelft.trustchain.p2playstore

import android.util.Log
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.alerts.AddTorrentAlert
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.foc.util.ExtensionUtils.Companion.TORRENT_EXTENSION
import nl.tudelft.trustchain.p2playstore.models.P2playApp
import nl.tudelft.trustchain.p2playstore.utils.MagnetLink
import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils
import java.io.File
import java.io.FileOutputStream
import java.util.*

class TorrentManager(cacheDir: File) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _onStarted = MutableSharedFlow<MagnetLink>()
    private val _onProgress = MutableSharedFlow<Pair<MagnetLink, Int>>()
    private val _onFinished = MutableSharedFlow<MagnetLink>()

    val onStarted = _onStarted.asSharedFlow()
    val onProgress = _onProgress.asSharedFlow()

    /**
     * Flow that emits an event when a torrent finishes downloading.
     */
    val onFinished = _onFinished.asSharedFlow()

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

    /**
     * List of all torrents the manager knows about/manages.
     */
    private val torrentInfos = ArrayList<TorrentInfo>();

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
            resumeTorrents()
        }
    }

    /**
     * Checks if the torrent for the magnetlink of this app version has finished downloading.
     */
    fun downloadProgress(app: P2playApp): Int? {
        val info = this.findTorrentInfo(app.magnetLink)
        try {
            val tmp = this.sessionManager.find(info?.infoHash())
            if (tmp.status().isFinished) {
                return 100;
            }
            return (tmp.status().progress() * 100).toInt();
        }
        catch (err: Throwable) {
            Log.e("P2P.TorrentManager", "Failed to find torrent handle: $err")
            return null;
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
                    Log.d("P2P.TorrentManager", "Found ${torrentInfo.infoHash()} torrent")
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
    private fun findTorrentInfo(magnetLink: MagnetLink): TorrentInfo? {
        for (info in this.torrentInfos) {
            val knownHash = MagnetUtils.parseMagnet(info.makeMagnetUri())
            if (knownHash.infoHash == magnetLink.infoHash) {
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
                            val a = (alert as AddTorrentAlert)
                            val link = MagnetUtils.parseMagnet(a.handle().makeMagnetUri())
                            a.handle().resume()
                            scope.launch {
                                _onStarted.emit(link)
                            }
                        }
                        AlertType.BLOCK_FINISHED -> {
                            Log.d("P2P.TorrentManager", "Block finished $alert")
                            val a = alert as BlockFinishedAlert
                            val p = (a.handle().status().progress() * 100).toInt()
                            val link = MagnetUtils.parseMagnet(a.handle().makeMagnetUri())
                            Log.d("P2P.TorrentManager", "Progress $p for ${a.torrentName()}")
                            scope.launch {
                                _onProgress.emit(Pair(link, p))
                            }
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val a = alert as TorrentFinishedAlert
                            Log.d("P2P.TorrentManager", "Download finished for ${a.torrentName()}")
                            val link = MagnetUtils.parseMagnet(a.handle().makeMagnetUri())
                            scope.launch {
                                _onFinished.emit(link);
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
        )
    }

    /**
     * Resumes downloading/seeding previously downloaded/started app downloads.
     */
    private suspend fun resumeTorrents() {
        while (scope.isActive) {
            for (info in this.torrentInfos) {
                try {
                    val hash = info.infoHash()
                    val handle: TorrentHandle? = this.sessionManager.find(hash)
                    if (handle == null) {
                        val destDir = File(this.appsDirectory, hash.toString())
                        this.sessionManager.download(info, destDir)
                    }
                }
                catch (e: Exception) {
                    Log.e("P2P.TorrentManager", "Exception while seeding apps")
                }
                delay(10_000)
            }
        }
    }

    /**
     * Downloads the specific version of the app described by the given block.
     */
    fun downloadApp(app: P2playApp) {
        Log.d("P2P.TorrentManager", "Downloading app: ${app.magnetLink.infoHash}")

        // Have we already downloaded this app?
        var torrentInfo = this.findTorrentInfo(app.magnetLink);
        if (torrentInfo != null) {
            Log.d("P2P.TorrentManager", "Magnet link was already known")
            // TODO: Maybe check if it was actually finished or restart if there was a failure?
            return;
        }

        this.waitFor100Nodes();

        torrentInfo = this.downloadTorrentInfo(app.magnetLink)
        this.torrentInfos.add(torrentInfo)

        // Create a .torrent file for this torrent so we can resume downloading/seeding after
        // restarting the app without needing to download the torrent info from someone else first.
        val entry: Entry = torrentInfo.toEntry()
        val torrentFile = File(this.appsDirectory, "${app.magnetLink.infoHash}.torrent")
        FileOutputStream(torrentFile).use { fos -> fos.write(entry.bencode()) }

        // Now finally actually start the download process.
        val destDir = File(this.appsDirectory, app.magnetLink.infoHash)
        this.sessionManager.download(torrentInfo, destDir)
    }

    fun downloadMagnetLink(magnetLink: MagnetLink) {

        Log.d("P2P.TorrentManager", "Downloading app: ${magnetLink.infoHash}")

        // Have we already downloaded this app?
        var torrentInfo = this.findTorrentInfo(magnetLink);
        if (torrentInfo != null) {
            Log.d("P2P.TorrentManager", "Magnet link was already known")
            // TODO: Maybe check if it was actually finished or restart if there was a failure?
            return;
        }

        this.waitFor100Nodes();

        torrentInfo = this.downloadTorrentInfo(magnetLink)
        this.torrentInfos.add(torrentInfo)

        // Create a .torrent file for this torrent so we can resume downloading/seeding after
        // restarting the app without needing to download the torrent info from someone else first.
        val entry: Entry = torrentInfo.toEntry()
        val torrentFile = File(this.appsDirectory, "${magnetLink.infoHash}.torrent")
        FileOutputStream(torrentFile).use { fos -> fos.write(entry.bencode()) }

        // Now finally actually start the download process.
        val destDir = File(this.appsDirectory, magnetLink.infoHash)
        this.sessionManager.download(torrentInfo, destDir)
    }

    /**
     * Downloads all the required meta data in order to actually be able to download the torrent
     * the magnet file points to. This is only needed if we have not yet saved this data to the
     * cache folder as a .torrent file.
     */
    private fun downloadTorrentInfo(link: MagnetLink): TorrentInfo {
        try {
            val data: ByteArray = sessionManager.fetchMagnet(link.raw, 30)
            return TorrentInfo.bdecode(data)
        }
        catch (err: Throwable) {
            Log.e("P2P.TorrentManager", "Failed to download torrent info: $err")
            throw Exception("Failed to download torrent info");
        }
    }

    /***
     * Blocks until the DHT to contain at least 10 nodes, this is taken from FOC, presumably so we
     * can be relatively certain that we can at least find the info we want,
     */
    private fun waitFor100Nodes() {
        val timer = Timer()
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    val nodes = sessionManager.stats().dhtNodes()
                    // wait for at least 10 nodes in the DHT.
                    if (nodes >= 100) {
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
