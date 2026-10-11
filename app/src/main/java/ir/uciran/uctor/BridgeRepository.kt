package ir.uciran.uctor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bridges come from (in priority order):
 *  1. the cache downloaded from this repo's "bridges" branch (filled hourly by the GitHub Action),
 *  2. the list embedded in the APK at build time (assets/bridges.json),
 *  3. community lists on GitHub (fallback if our own list can't be reached).
 */
object BridgeRepository {

    class BridgeSet(val updated: String, val map: Map<String, List<String>>) {
        fun get(t: String): List<String> = map[t].orEmpty()
        fun total() = map.values.sumOf { it.size }
        fun summary(): String {
            val parts = listOf("webtunnel", "obfs4", "snowflake", "meek_lite", "vanilla")
                .joinToString("  ·  ") { "$it: ${get(it).size}" }
            return if (updated.isNotBlank()) "$parts\nآخرین به‌روزرسانی: $updated" else parts
        }
    }

    private const val CACHE = "bridges_cache.json"
    @Volatile private var current: BridgeSet? = null

    private val COMMUNITY_TEXT = listOf(
        "https://raw.githubusercontent.com/center2055/OnionHop-Bridges-Collector/main/bridge/webtunnel_tested.txt",
        "https://raw.githubusercontent.com/center2055/OnionHop-Bridges-Collector/main/bridge/obfs4_tested.txt",
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/main/TOR-BRIDGES/TOR_BRIDGES_TOP100.txt",
    )

    fun load(ctx: Context): BridgeSet {
        current?.let { return it }
        val cache = File(ctx.filesDir, CACHE)
        val fromCache = if (cache.exists()) runCatching { parseJson(cache.readText()) }.getOrNull() else null
        val fromAssets = runCatching {
            ctx.assets.open("bridges.json").bufferedReader().use { parseJson(it.readText()) }
        }.getOrNull()
        val merged = merge(fromCache, fromAssets)
        current = merged
        return merged
    }

    fun invalidate() { current = null }

    private fun parseJson(text: String): BridgeSet {
        val o = JSONObject(text)
        val src = o.optJSONObject("bridges") ?: o
        val map = LinkedHashMap<String, MutableList<String>>()
        val keys = src.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = src.optJSONArray(k) ?: continue
            for (i in 0 until arr.length()) {
                val line = Planner.normalize(arr.optString(i))
                val t = Planner.typeOf(line)
                if (line.isEmpty() || t !in Planner.KNOWN) continue
                val l = map.getOrPut(t) { mutableListOf() }
                if (line !in l) l += line
            }
        }
        return BridgeSet(o.optString("updated", ""), map)
    }

    private fun merge(vararg sets: BridgeSet?): BridgeSet {
        val map = LinkedHashMap<String, MutableList<String>>()
        var updated = ""
        for (s in sets) {
            if (s == null) continue
            if (updated.isBlank()) updated = s.updated
            s.map.forEach { (k, v) ->
                val l = map.getOrPut(k) { mutableListOf() }
                v.forEach { if (it !in l) l += it }
            }
        }
        return BridgeSet(updated, map)
    }

    private fun ownUrls(): List<String> {
        val repo = BuildConfig.BRIDGE_REPO.trim()
        if (repo.isEmpty()) return emptyList()
        return listOf(
            "https://raw.githubusercontent.com/$repo/bridges/bridges.json",
            "https://cdn.jsdelivr.net/gh/$repo@bridges/bridges.json",
            "https://fastly.jsdelivr.net/gh/$repo@bridges/bridges.json",
            "https://gcore.jsdelivr.net/gh/$repo@bridges/bridges.json",
        )
    }

    fun refreshDirect(ctx: Context): Boolean = refresh(ctx, Proxy.NO_PROXY, 10_000)

    /** Uses Tor's HTTP tunnel port, so the download itself goes through Tor (no DNS leak). */
    fun refreshViaTor(ctx: Context, httpPort: Int): Boolean =
        httpPort > 0 && refresh(ctx, Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", httpPort)), 45_000)

    private fun refresh(ctx: Context, proxy: Proxy, timeout: Int): Boolean {
        val cache = File(ctx.filesDir, CACHE)
        for (u in ownUrls()) {
            val body = fetch(u, proxy, timeout) ?: continue
            val set = runCatching { parseJson(body) }.getOrNull() ?: continue
            if (set.total() == 0) continue
            cache.writeText(body)
            invalidate()
            return true
        }
        // Fallback: community lists (plain text)
        val collected = LinkedHashMap<String, MutableList<String>>()
        for (u in COMMUNITY_TEXT) {
            val body = fetch(u, proxy, timeout) ?: continue
            Planner.parseLines(body).forEach { (t, l) -> collected.getOrPut(t) { mutableListOf() }.addAll(l) }
        }
        if (collected.isEmpty()) return false
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val json = JSONObject()
            .put("updated", now)
            .put("bridges", JSONObject().apply { collected.forEach { (k, v) -> put(k, JSONArray(v.distinct())) } })
        cache.writeText(json.toString())
        invalidate()
        return true
    }

    private fun fetch(u: String, proxy: Proxy, timeout: Int): String? = try {
        val c = URL(u).openConnection(proxy) as HttpURLConnection
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) UCTor")
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
