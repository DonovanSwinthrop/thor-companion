package com.pokedaisy.app.companion

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pokedaisy.app.companion.data.MemoryReader
import org.json.JSONObject
import java.util.Calendar

/**
 * The Radical Red nuzlocke overlay (calc, lines, theory craft, future bosses, dex, items) on the NUZLOCKE tab.
 *
 * The overlay is the web app from the PC overlay (`overlay.html` and its scripts), copied into
 * `assets/overlay/` at build time by `scripts/sync_overlay.sh` - it holds sprites and data taken from the
 * game and the community docs, so it is never committed (.gitignore). Without it the tab says so.
 *
 * [sample] runs on the emulator thread with the other reads and builds the same JSON the PC overlay's mGBA
 * script sends (`thor/state.js` there, keep in step); the page gets it through `window.__rrFeed`
 * (`rr-web.js`'s android mode).
 */
object RrOverlay {
    private const val HOST = "appassets.androidplatform.net"
    private const val URL = "https://$HOST/overlay/overlay.html?android"

    /** The games the overlay has data for, and its `?game=` for each (Radical Red is the default page). */
    fun gameParam(game: com.pokedaisy.app.companion.data.GameKind?): String? = when (game) {
        com.pokedaisy.app.companion.data.GameKind.RADICAL_RED -> "rr"
        com.pokedaisy.app.companion.data.GameKind.UNBOUND -> "unbound"
        com.pokedaisy.app.companion.data.GameKind.SWORD_SHIELD -> "swsh"
        else -> null
    }
    private var loadedGame: String? = null

    // Radical Red 4.1 (FireRed USA base) RAM, from rr_overlay.lua / thor/ram_map.js.
    private const val SAVE_BLOCK1_PTR = 0x03005008L
    private const val MAP_HEADER = 0x02036DFCL
    private const val IN_BATTLE = 0x03003529L
    private const val BATTLE_TYPE = 0x02022B4CL
    private const val BATTLE_OUTCOME = 0x02023E8AL
    private const val TRAINER_A = 0x020386AEL
    private const val TRAINER_B = 0x020386B0L
    private const val BATTLE_MONS = 0x02023BE4L
    private const val PARTY_INDEXES = 0x02023BCEL
    private const val PARTY_COUNT = 0x02024029L
    private const val PLAYER_PARTY = 0x02024284L
    private const val ENEMY_PARTY = 0x0202402CL
    private const val GAME_MODE = 0x0203B25AL
    private const val PC_BOXES = 0x02029318L
    private const val STORAGE_PTR = 0x03005010L
    private const val BOX20 = 0x0203CB44L
    private const val BOX23 = 0x02027434L
    private const val BOX25 = 0x02024638L
    private const val WEATHER = 0x02023F1CL
    private const val SIDE_STATUSES = 0x02023DDEL
    private const val SIDE_TIMERS = 0x02023DE4L
    private const val STATUSES3 = 0x02023DFCL
    private const val TERRAIN = 0x0203E028L
    private const val NEW_BATTLE_STRUCT_PTR = 0x0203E038L
    private const val BOX_BYTES = 30 * 58
    private const val PC_BYTES = 19 * BOX_BYTES

    private val main = Handler(Looper.getMainLooper())
    private const val PREFS = "pokedaisy"
    private const val MODE_KEY = "rr_overlay_mode"

    /** The overlay's mode, picked in its own settings: "nuzlocke" or "play" (a normal playthrough). Names the tab. */
    val mode = androidx.compose.runtime.mutableStateOf("nuzlocke")

    /** The tab's name before the page has loaded: the mode it last reported. */
    fun loadMode(context: Context) {
        mode.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODE_KEY, "nuzlocke") ?: "nuzlocke"
    }
    private var web: WebView? = null
    @Volatile private var lastState: String? = null
    @Volatile private var lastPc: String? = null
    private var samples = 0
    private var frame = 0

    /** Emulator thread, about once a second, only while Radical Red runs. */
    fun sample(reader: MemoryReader) {
        val state = runCatching { buildState(reader) }.getOrNull() ?: return
        // On the title screen the save is only half loaded (party and map yes, PC boxes no): it read as "every route
        // unused". gPokemonStoragePtr is set once the game proper has started.
        if (state.optInt("partyCount") == 0 || !state.has("map")) return
        if (!inEwram(runCatching { reader.u32(STORAGE_PTR) }.getOrDefault(0L))) return
        lastState = JSONObject().put("type", "state").put("state", state).toString()
        post(lastState!!)
        // The PC is ~70 KB of hex: every 5th sample is plenty (boxes change only in the game's PC).
        if (samples++ % 5 == 0) {
            runCatching { buildPc(reader) }.getOrNull()?.let {
                lastPc = it.put("type", "pc").toString()
                post(lastPc!!)
            }
            post(JSONObject().put("type", "clock").put("hour", Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).toString())
        }
    }

    /** A game was closed or switched: the next page load starts empty. */
    fun reset() {
        lastState = null; lastPc = null; samples = 0
    }

    private fun MemoryReader.bytes(addr: Long, len: Int) = readCoreMemory(addr, len)
    private fun MemoryReader.u8(addr: Long) = bytes(addr, 1)[0].toInt() and 0xFF
    private fun MemoryReader.u16(addr: Long) = bytes(addr, 2).let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) }
    private fun MemoryReader.u32(addr: Long) = bytes(addr, 4).let { b ->
        (0..3).fold(0L) { acc, i -> acc or ((b[i].toLong() and 0xFF) shl (8 * i)) }
    }
    private fun MemoryReader.hex(addr: Long, len: Int) = bytes(addr, len).toHex()
    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]; out[i * 2 + 1] = HEX[v and 0xF]
        }
        return String(out)
    }
    private val HEX = "0123456789abcdef".toCharArray()
    private fun inEwram(p: Long) = p in 0x02000000L until 0x02040000L

    private fun buildState(r: MemoryReader): JSONObject {
        val sb1 = r.u32(SAVE_BLOCK1_PTR)
        val inBattle = r.u8(IN_BATTLE) and 2 != 0
        val s = JSONObject()
            .put("v", 1).put("frame", frame++)
            .put("inBattle", if (inBattle) 1 else 0)
            .put("battleType", r.u32(BATTLE_TYPE)).put("outcome", r.u8(BATTLE_OUTCOME))
            .put("trainerA", r.u16(TRAINER_A)).put("trainerB", r.u16(TRAINER_B))
            .put("mapsec", r.u8(MAP_HEADER + 0x14)).put("mode", r.u8(GAME_MODE))
            .put("partyCount", r.u8(PARTY_COUNT)).put("party", r.hex(PLAYER_PARTY, 600))
        if (inEwram(sb1)) {
            val mp = r.bytes(sb1 + 4, 2)
            s.put("map", org.json.JSONArray().put(mp[0].toInt() and 0xFF).put(mp[1].toInt() and 0xFF))
            s.put("flags", r.hex(sb1 + 0xEE0, 0x120))
        }
        if (inBattle) {
            s.put("enemy", r.hex(ENEMY_PARTY, 600)).put("battleMons", r.hex(BATTLE_MONS, 0x58 * 4))
                .put("partyIdx", r.hex(PARTY_INDEXES, 8))
                .put("weather", r.u16(WEATHER)).put("sides", r.hex(SIDE_STATUSES, 4))
                .put("timers", r.hex(SIDE_TIMERS, 24)).put("terrain", r.u8(TERRAIN))
                .put("status3", r.hex(STATUSES3, 16))
            val nbs = r.u32(NEW_BATTLE_STRUCT_PTR)
            if (inEwram(nbs)) s.put("nbs", r.hex(nbs, 0x18))
        }
        return s
    }

    private fun buildPc(r: MemoryReader): JSONObject {
        // Boxes 1-19 move around in RAM: follow gPokemonStoragePtr (+4 skips the current-box byte).
        val ptr = r.u32(STORAGE_PTR)
        val at = if (inEwram(ptr)) ptr + 4 else PC_BOXES
        val extra = r.bytes(BOX20, 3 * BOX_BYTES) + r.bytes(BOX23, 2 * BOX_BYTES) + r.bytes(BOX25, BOX_BYTES)
        return JSONObject().put("pc", r.hex(at, PC_BYTES)).put("x", extra.toHex())
    }

    private fun post(json: String) = main.post {
        web?.evaluateJavascript(
            "window.__rrFeed ? __rrFeed($json) : (window.__rrQueue = window.__rrQueue || []).push($json)", null)
    }

    /** True when the build carries the overlay's files (scripts/sync_overlay.sh). */
    fun available(context: Context): Boolean =
        runCatching { context.assets.open("overlay/overlay.html").close() }.isSuccess

    /** The tab's page, made once per screen (its context) and game, kept while tabs switch, so the overlay keeps its place. */
    @SuppressLint("SetJavaScriptEnabled")
    fun view(context: Context, game: String): WebView {
        web?.let { w ->
            if (w.context === context && loadedGame == game) { (w.parent as? ViewGroup)?.removeView(w); return w }
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy(); web = null
            reset()
        }
        val w = WebView(context)
        w.setBackgroundColor(0xFF101217.toInt())
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // the overlay's settings, stars, boss orders
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = 100
        }
        val assets = context.assets
        val appContext = context.applicationContext
        w.webViewClient = object : WebViewClient() {
            // The overlay is served from the APK's assets on an https origin, so its planner Worker and
            // localStorage work (file:// pages get neither).
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.host != HOST) return null
                val path = url.path.orEmpty().trimStart('/')
                if (!path.startsWith("overlay/") || path.contains("..")) return null
                return runCatching {
                    WebResourceResponse(mimeOf(path), "utf-8", assets.open(path))
                }.getOrElse { WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null) }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.host != HOST
        }
        w.addJavascriptInterface(object {
            /** The page is listening: give it the latest state at once instead of waiting a second. */
            @JavascriptInterface fun ready() {
                main.post { lastPc?.let(::post); lastState?.let(::post) }
            }
            /** The player switched NUZLOCKE / PLAYTHROUGH in the overlay's settings (and on every page load). */
            @JavascriptInterface fun setMode(m: String) {
                val v = if (m == "play") "play" else "nuzlocke"
                main.post {
                    mode.value = v
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODE_KEY, v).apply()
                }
            }
        }, "RRAndroid")
        w.loadUrl(if (game == "rr") URL else "$URL&game=$game")
        web = w
        loadedGame = game
        return w
    }

    private fun mimeOf(path: String) = when (path.substringAfterLast('.')) {
        "html" -> "text/html"
        "js" -> "application/javascript"
        "css" -> "text/css"
        "json", "webmanifest" -> "application/json"
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }
}
