package nl.tudelft.trustchain.p2playstore

import nl.tudelft.trustchain.p2playstore.utils.MagnetUtils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the MagnetUtils class.
 */
class MagnetUtilsTest {
    @Test
    fun parseMagnet_isCorrect() {
        val magnetUri = "magnet:?xt=urn:btih:9f65cf30a02d654151bd26108a6fe91f7c000409" +
            "&dn=test-app.apk" +
            "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce" +
            "&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce" +
            "&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.skyts.net%3A6969%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.ololosh.space%3A6969%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.bittor.pw%3A1337%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.bittor.pw%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Fopen.free-tracker.ga%3A6969%2Fannounce" +
            "&tr=udp%3A%2F%2Fns-1.x-fins.com%3A6969%2Fannounce" +
            "&tr=udp%3A%2F%2Fleet-tracker.moe%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Fisk.richardsw.club%3A6969%2Fannounce" +
            "&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce" +
            "&tr=http%3A%2F%2Fwww.torrentsnipe.info%3A2701%2Fannounce" +
            "&tr=http%3A%2F%2Fwww.genesis-sp.org%3A2710%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker810.xyz%3A11450%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.xiaoduola.xyz%3A6969%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.vanitycore.co%3A6969%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.sbsub.com%3A2710%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.ipv6tracker.org%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.dmcomic.org%3A2710%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.corpscorp.online%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.bz%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Ftracker.bt-hash.com%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Ft.jaekr.sh%3A6969%2Fannounce" +
            "&tr=http%3A%2F%2Fshubt.net%3A2710%2Fannounce" +
            "&tr=http%3A%2F%2Fservandroidkino.ru%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Fseeders-paradise.org%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Fretracker.spark-rostov.ru%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Fopen.trackerlist.xyz%3A80%2Fannounce" +
            "&tr=http%3A%2F%2Fhighteahop.top%3A6960%2Fannounce" +
            "&tr=http%3A%2F%2Ffinbytes.org%3A80%2Fannounce.php" +
            "&tr=http%3A%2F%2Fbuny.uk%3A6969%2Fannounce" +
            "&tr=http%3A%2F%2Fbt1.xxxxbt.cc%3A6969%2Fannounce"

        val magnet = MagnetUtils.parseMagnet(magnetUri)

        assertEquals(magnetUri, magnet.link)
        assertEquals("9f65cf30a02d654151bd26108a6fe91f7c000409", magnet.infoHash)
        assertEquals("test-app.apk", magnet.displayName)
        assertEquals(36, magnet.trackers.size) // Validate total number of trackers
        assertEquals("udp://tracker.opentrackr.org:1337/announce", magnet.trackers[0])
        assertEquals("http://tracker.opentrackr.org:1337/announce", magnet.trackers[1])
    }
}

