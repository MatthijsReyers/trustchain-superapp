package nl.tudelft.trustchain.p2playstore.utils

import android.net.Uri
import androidx.core.net.toUri

data class MagnetLink(
    val raw: String,                        // The original link
    val infoHash: String,                   // from xt=urn:btih:<hash>
    val displayName: String?,               // from dn=
    val fileSize: Long?,                    // from xl= (bytes)
    val trackers: List<String>,             // from tr= (may be empty)
    val webSeeds: List<String>,             // from ws= (may be empty)
    val acceptableSources: List<String>,    // from as= (may be empty)
    val exactSources: List<String>,         // from xs= (may be empty)
    val keywordTopic: List<String>,         // from kt= (may be empty)
    val manifestTopic: String?,             // from mt=
    val selectOnly: List<IntRange>,         // from so= (may be empty)
    val peerEndpoints: List<String>         // from x.pe= (may be empty)
)

object MagnetUtils {

    /**
     * Parses a magnet link, asserts it’s valid, and returns a data object.
     *
     * @param magnetLink the full magnet URI to parse.
     * @throws IllegalArgumentException if the link is malformed or missing required fields.
     */
    @Throws(IllegalArgumentException::class)
    fun parseMagnet(magnetLink: String): MagnetLink {
        val uri = magnetLink.toUri()

        // Scheme must be "magnet"
        require(uri.scheme == "magnet") {
            "Invalid scheme. Expected magnet, got: ${uri.scheme}"
        }

        // Mandatory eXact Topic (xt)
        val rawQuery = uri.encodedQuery ?: throw IllegalArgumentException("No query string found")
        val params = parseQuery(rawQuery)

        val xt = params["xt"]?.firstOrNull()
            ?: throw IllegalArgumentException("magnet link missing xt parameter")
        require(xt.startsWith("urn:btih:")) { "Invalid xt: $xt" }

        val infoHash = xt.removePrefix("urn:btih:")
        val displayName = params["dn"]?.firstOrNull()
        val fileSize = params["xl"]?.firstOrNull()?.toLongOrNull()
        val trackers = params["tr"] ?: emptyList()
        val webSeeds = params["ws"] ?: emptyList()
        val acceptableSources = params["as"] ?: emptyList()
        val exactSources = params["xs"] ?: emptyList()
        val keywordTopic = params["kt"]?.firstOrNull()?.split('+')?.filter { it.isNotBlank() } ?: emptyList()
        val manifestTopic = params["mt"]?.firstOrNull()
        val peerEndpoints = params["x.pe"] ?: emptyList()

        val selectOnly = mutableListOf<IntRange>()
        params["so"]?.flatMap { it.split(",") }?.forEach { part ->
            if (part.contains("-")) {
                val (start, end) = part.split("-").mapNotNull { it.toIntOrNull() }
                selectOnly += start..end
            } else {
                part.toIntOrNull()?.let { selectOnly += it..it }
            }
        }

        return MagnetLink(
            raw = magnetLink,
            infoHash = infoHash,
            displayName = displayName,
            fileSize = fileSize,
            trackers = trackers,
            webSeeds = webSeeds,
            acceptableSources = acceptableSources,
            exactSources = exactSources,
            keywordTopic = keywordTopic,
            manifestTopic = manifestTopic,
            selectOnly = selectOnly,
            peerEndpoints = peerEndpoints
        )
    }

    /**
     * Parses a URL-style query string into a map of keys to lists of values.
     *
     * This method splits a query string (e.g., from a URI) into individual key-value pairs,
     * decodes the values using `Uri.decode`, and returns a map where each key maps to a list
     * of values associated with it. Keys that appear multiple times (e.g., `tr=...&tr=...`)
     * will have their values grouped in a list.
     *
     * Query parameters without an equals sign (`=`) or missing a value are ignored.
     *
     * @param query The raw query string, typically obtained from a URI (e.g., everything after `?`).
     * @return A map of decoded query parameters, where each key maps to one or more associated values.
     *
     * Example:
     * ```
     * val query = "dn=example&tr=udp%3A%2F%2Ftracker.com%2Fannounce&tr=http%3A%2F%2Ftracker2.com"
     * val parsed = parseQuery(query)
     * // Result:
     * // {
     * //   "dn" -> ["example"],
     * //   "tr" -> ["udp://tracker.com/announce", "http://tracker2.com"]
     * // }
     * ```
     */
    private fun parseQuery(query: String): Map<String, List<String>> {
        return query.split("&").mapNotNull { param ->
            val (key, value) = param.split("=", limit = 2).let {
                if (it.size == 2) it[0] to Uri.decode(it[1]) else return@mapNotNull null
            }
            key to value
        }.groupBy({ it.first }, { it.second })
    }
}
