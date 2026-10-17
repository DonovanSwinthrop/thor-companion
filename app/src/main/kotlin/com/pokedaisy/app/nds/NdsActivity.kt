package com.pokedaisy.app.nds

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pokedaisy.app.ComposeHostOwner
import com.pokedaisy.app.GameTitles
import com.pokedaisy.app.companion.ui.theme.AppTheme
import com.pokedaisy.app.DualScreenPresentation
import com.pokedaisy.app.LibraryActivity
import com.pokedaisy.app.Prefs
import com.pokedaisy.app.SaveBackups
import com.pokedaisy.app.SavesLocation
import com.pokedaisy.app.Screens
import java.io.File

/**
 * A DS game (melonDS, [NdsCore]). With a second screen (the Thor) the DS's top screen is the main display and
 * its touch screen the bottom one; on one screen they stack. R3 (or SELECT + R) swaps which screen shows where.
 * BACK or L3 opens the pause menu ([NdsMenu]: save / load state, fast forward, swap, close); holding R2 fast-forwards.
 * The save is the game's own `.sav`, found and backed up like the GBA games' (SavesLocation, SaveBackups).
 */
class NdsActivity : Activity() {

    private lateinit var rom: File
    private lateinit var save: File
    private lateinit var topView: NdsScreenView
    private lateinit var bottomView: NdsScreenView
    private var presentation: DualScreenPresentation? = null
    /** The game's RUN tab pack, if it has one ([NdsFeed]); the touch screen's frame holds the tab over it. */
    private var feedGame: NdsFeed.Game? = null
    private lateinit var touchFrame: FrameLayout
    private var runView: android.webkit.WebView? = null
    private var runOpen by mutableStateOf(false)
    private var swapped = false

    @Volatile private var running = false
    /** The ROM is in (loaded off the main thread); [resumed]: between onResume and onPause. */
    private var loaded = false
    private var resumed = false
    private var thread: Thread? = null
    private var audio: AudioTrack? = null
    /** Buttons held now (UI thread), and ones pressed since the last frame ([latched]): a tap whose press and
     * release land between two frames (a keyboard's, or a quick one) still reaches the game. */
    @Volatile private var keys = 0
    private val latched = java.util.concurrent.atomic.AtomicInteger(0)

    private val owner = ComposeHostOwner()
    private var menuOpen by mutableStateOf(false)
    private var menuCursor by mutableIntStateOf(0)
    private var fastForward by mutableStateOf(false)
    private var hasState by mutableStateOf(false)
    private var clockHour by mutableIntStateOf(-1)
    private var shortcuts by mutableStateOf(true)

    /** INFINITE REPEL: the feed tops the game's repel counter up while it's on. */
    private var infiniteRepel by mutableStateOf(false)

    /** A wild battle is on (the feed's last read): R runs away while [shortcuts] is on. */
    @Volatile private var wildBattle = false

    /** The battle shortcuts' touches on the game's battle screen, played a frame at a time. */
    private val taps = NdsShortcuts.Taps()
    private val shortcutRam = ByteArray(0x400000)
    @Volatile private var paused = false
    @Volatile private var ffHeld = false
    private val stateFile by lazy { File(File(filesDir, "nds-states").apply { mkdirs() }, rom.nameWithoutExtension + ".state") }
    private val menuView by lazy {
        ComposeView(this).apply {
            setContent {
                AppTheme {
                    if (menuOpen) NdsMenu(GameTitles.label(this@NdsActivity, Prefs(this@NdsActivity), rom), menuItems(), menuCursor, fastForward, hasState, clockHour, shortcuts, infiniteRepel, ::pick)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(LibraryActivity.EXTRA_ROM)
        rom = path?.let(::File)?.takeIf { it.isFile } ?: run { finish(); return }
        save = SavesLocation.saveFor(this, Prefs(this), rom)
        if (save.exists()) SaveBackups.snapshot(save, maxBytes = 8L shl 20)
        val touch: (Int, Int, Boolean) -> Unit = { x, y, down -> NdsCore.touch(x, y, down) }
        topView = NdsScreenView(this)
        bottomView = NdsScreenView(this, touch)
        feedGame = if (com.pokedaisy.app.companion.RrOverlay.available(this)) NdsFeed.gameFor(rom) else null
        shortcuts = Prefs(this).battleShortcuts
        infiniteRepel = Prefs(this).infiniteRepel(rom.name)
        touchFrame = FrameLayout(this).apply {
            val match = ViewGroup.LayoutParams.MATCH_PARENT
            addView(bottomView, FrameLayout.LayoutParams(match, match))
            if (feedGame != null) {
                // RUN: the touch screen gives way to the RUN tab, and back (bottom right, clear of the tab bar)
                addView(ComposeView(this@NdsActivity).apply {
                    setContent { AppTheme { NdsRunButton(runOpen) { toggleRun() } } }
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM or android.view.Gravity.END))
            }
        }
        owner.create()
        hasState = stateFile.isFile
        layoutScreens()
        // A DS ROM runs to 512 MB: read it off the main thread (a slow device froze long enough for "isn't responding")
        Thread({
            val ok = NdsCore.load(rom.absolutePath, save.absolutePath, jit = true)
            runOnUiThread {
                if (isDestroyed) {
                    if (ok) NdsCore.close()
                } else if (!ok) {
                    Toast.makeText(this, com.pokedaisy.app.companion.i18n.tr("This DS game wouldn't load"), Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    loaded = true
                    if (resumed) start()
                }
            }
        }, "nds-load").start()
    }

    /** The touch screen on the second display (or swapped onto the main one), else both stacked here. */
    private fun layoutScreens() {
        (topView.parent as? ViewGroup)?.removeView(topView)
        (touchFrame.parent as? ViewGroup)?.removeView(touchFrame)
        (menuView.parent as? ViewGroup)?.removeView(menuView)
        presentation?.releaseContent()
        presentation?.dismiss()
        presentation = null
        val second = Screens.second(this)?.takeIf { !Prefs(this).singleScreen }
        val (main, other) = if (swapped) touchFrame to topView else topView to touchFrame
        val screens = if (second != null) {
            presentation = DualScreenPresentation(this, second) { other }.also { it.show() }
            main
        } else {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
                addView(main, lp())
                addView(other, lp())
            }
        }
        // the pause menu over the main display's screen
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        setContentView(FrameLayout(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            // the activity steers the menu (dispatchKeyEvent): with nothing focusable Android doesn't eat the
            // first D-pad press to leave touch mode
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            addView(screens, FrameLayout.LayoutParams(match, match))
            addView(menuView, FrameLayout.LayoutParams(match, match))
        })
        hideBars()
    }

    private fun hideBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::topView.isInitialized) return
        owner.resume()
        resumed = true
        if (loaded) start()
    }

    override fun onPause() {
        resumed = false
        stop()
        if (loaded) NdsCore.flushSave()
        super.onPause()
    }

    override fun onDestroy() {
        stop()
        presentation?.dismiss()
        if (::topView.isInitialized) {
            if (loaded) NdsCore.close()
            owner.destroy()
        }
        super.onDestroy()
    }

    private fun start() {
        if (running) return
        running = true
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(48000 / 60 * 4 * 6)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        audio = track
        thread = Thread({ loop(track) }, "nds-emu").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun stop() {
        running = false
        thread?.join(500)
        thread = null
        audio?.run { pause(); flush(); release() }
        audio = null
    }

    private fun loop(track: AudioTrack) {
        val top = IntArray(NdsCore.WIDTH * NdsCore.HEIGHT)
        val bottom = IntArray(NdsCore.WIDTH * NdsCore.HEIGHT)
        val sound = ShortArray(4096 * 2)
        var next = System.nanoTime()
        var frames = 0L
        var lastLog = System.nanoTime()
        var lastFlush = 0L
        while (running) {
            if (paused) {
                Thread.sleep(16)
                next = System.nanoTime()
                continue
            }
            // fast forward: FF_FRAMES frames a tick, the last one shown, the sound skipped
            val ff = fastForward || ffHeld
            repeat(if (ff) FF_FRAMES - 1 else 0) {
                runFrame(top, bottom)
                NdsCore.readAudio(sound)
                frames++
            }
            if (!runFrame(top, bottom)) break
            topView.show(top)
            bottomView.show(bottom)
            val n = NdsCore.readAudio(sound)
            if (n > 0 && !ff) track.write(sound, 0, n * 2, AudioTrack.WRITE_NON_BLOCKING)
            // a fast-forward tick runs several frames: act when a multiple was crossed, not hit (it never was at FF)
            val before = frames
            if (com.pokedaisy.app.BuildConfig.DEBUG && (before + 1) / 60 != (before - (if (ff) FF_FRAMES - 1 else 0)) / 60) { debugDump(); debugPoke() }
            if ((before + 1) / FEED_EVERY != (before - (if (ff) FF_FRAMES - 1 else 0)) / FEED_EVERY) feed()
            if (++frames - lastFlush >= 600) {
                NdsCore.flushSave()   // every ~10 s of game, between frames
                val now = System.nanoTime()
                Log.i(TAG, "fps=%.1f".format((frames - lastFlush) * 1e9 / (now - lastLog)))
                lastLog = now
                lastFlush = frames
            }
            next += NdsCore.FRAME_NANOS
            val wait = next - System.nanoTime()
            if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
            else if (wait < -NdsCore.FRAME_NANOS * 4) next = System.nanoTime()   // fell behind: don't race to catch up
        }
        Log.i(TAG, "emulation stopped after $frames frames")
    }

    private val feedRam = ByteArray(0x400000)

    /** Each move's base PP, from the ROM's own move table (HEAL PARTY fills PP with it); null if it can't be read. */
    private val movePp by lazy { runCatching { NdsRom(rom).movePp() }.getOrNull() }

    /** The RUN tab's state, twice a second (emu thread, between frames). */
    private fun feed() {
        val game = feedGame ?: return
        if (!NdsCore.readRam(NdsMons.RAM_BASE, feedRam)) return
        wildBattle = runCatching { NdsFeed.wildBattle(feedRam, game) }.getOrDefault(false)
        // INFINITE REPEL: never let the steps run low (the game then never asks to use another)
        if (infiniteRepel && game.repel != 0 && NdsFeed.repelSteps(feedRam, game) < REPEL_TOP / 2) {
            NdsCore.writeRam(game.repel, byteArrayOf(REPEL_TOP.toByte()))
        }
        runCatching { NdsFeed.state(feedRam, game) }.getOrNull()?.let { com.pokedaisy.app.companion.RrOverlay.postDecoded(it) }
    }

    private fun toggleRun() {
        val game = feedGame ?: return
        runOpen = !runOpen
        if (runOpen) {
            val web = com.pokedaisy.app.companion.RrOverlay.view(this, game.pack)
            (web.parent as? ViewGroup)?.removeView(web)
            touchFrame.addView(web, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            runView = web
        } else {
            runView?.let { touchFrame.removeView(it) }
            runView = null
        }
    }

    /** Debug builds: `files/nds-poke` lines of "address hexbytes" are written into main RAM, then the file goes (finding
     * addresses: give the game an item, a counter, a flag, and watch what it does with it). */
    private fun debugPoke() {
        val req = File(filesDir, "nds-poke")
        if (!req.exists()) return
        val lines = runCatching { req.readLines() }.getOrDefault(emptyList())
        req.delete()
        for (line in lines) {
            val parts = line.trim().split(' ', '\t').filter { it.isNotEmpty() }
            if (parts.size != 2) continue
            val addr = parts[0].removePrefix("0x").toLongOrNull(16)?.toInt() ?: continue
            val bytes = parts[1].chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
            Log.i(TAG, "poke 0x%08x %s: %b".format(addr, parts[1], NdsCore.writeRam(addr, bytes)))
        }
    }

    /** Debug builds: `files/nds-dump` present = main RAM (4 MB) written to `files/nds-ram.bin` (fixtures for the readers). */
    private fun debugDump() {
        val req = File(filesDir, "nds-dump")
        if (!req.exists()) return
        req.delete()
        val ram = ByteArray(0x400000)
        if (NdsCore.readRam(0x02000000, ram)) File(filesDir, "nds-ram.bin").writeBytes(ram)
        Log.i(TAG, "RAM dumped")
    }

    private var pressedLastFrame = 0

    /** One frame with the buttons held now plus any tapped since the last two frames. */
    private fun runFrame(top: IntArray, bottom: IntArray): Boolean {
        val tapped = latched.getAndSet(0)
        taps.frame { x, y, down -> NdsCore.touch(x, y, down) }
        NdsCore.setKeys(keys or tapped or pressedLastFrame)
        pressedLastFrame = tapped
        return NdsCore.runFrame(top, bottom)
    }

    // ------------------------------------------------------------------------------------------- keys
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL) {
            if (down) setMenu(!menuOpen)
            return true
        }
        if (menuOpen) {
            if (down) menuKey(event.keyCode)
            return true
        }
        // battle shortcuts, wild battles only (the keys' releases are kept from the game too): R taps the game's RUN,
        // L throws the best ball in the bag (BAG, POKé BALLS, the ball, USE)
        if (shortcuts && wildBattle && (event.keyCode == KeyEvent.KEYCODE_BUTTON_R1 || event.keyCode == KeyEvent.KEYCODE_W)) {
            if (down) taps.play(NdsShortcuts.run)
            return true
        }
        val bagAt = feedGame?.bag ?: 0
        if (shortcuts && wildBattle && bagAt != 0 && (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1 || event.keyCode == KeyEvent.KEYCODE_Q)) {
            if (down && !taps.busy && NdsCore.readRam(NdsMons.RAM_BASE, shortcutRam)) NdsShortcuts.ball(shortcutRam, bagAt)?.let(taps::play)
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            ffHeld = event.action == KeyEvent.ACTION_DOWN
            return true
        }
        val bit = KEY_MAP[event.keyCode]
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR && event.action == KeyEvent.ACTION_UP) {
            swap(); return true
        }
        if (bit == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            keys = keys or bit
            latched.getAndUpdate { it or bit }
            if (keys and (NdsCore.SELECT or NdsCore.R) == NdsCore.SELECT or NdsCore.R) {
                keys = keys and (NdsCore.SELECT or NdsCore.R).inv()
                swap()
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            keys = keys and bit.inv()
        }
        return true
    }

    // Analog triggers (no key events on some devices): R2 past half way holds fast forward.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val r2 = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
            ffHeld = r2 > 0.5f
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ------------------------------------------------------------------------------------------- menu
    private fun setMenu(open: Boolean) {
        menuOpen = open
        paused = open
        if (open) {
            menuCursor = 0
            keys = 0
            NdsCore.setKeys(0)
            ffHeld = false
            hasState = stateFile.isFile
            clockHour = NdsCore.clockHour()
        }
    }

    /** Moves the DS clock by [hours] and shows the new hour; the menu stays open so it can be stepped. */
    private fun stepClock(hours: Int) {
        NdsCore.shiftClock(hours)
        clockHour = NdsCore.clockHour()
    }

    /** The pause menu's rows: HEAL PARTY and BATTLE SHORTCUTS only where the game's party is mapped (a pack exists). */
    private fun menuItems() = NdsMenuItem.entries.filter {
        when (it) {
            NdsMenuItem.HEAL_PARTY -> feedGame != null
            // the shortcuts need the battle and the bag mapped (Gen 5 so far)
            NdsMenuItem.SHORTCUTS -> (feedGame?.bag ?: 0) != 0
            NdsMenuItem.REPEL -> (feedGame?.repel ?: 0) != 0
            else -> true
        }
    }

    private fun menuKey(code: Int) {
        val items = menuItems()
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> menuCursor = (menuCursor + items.size - 1) % items.size
            KeyEvent.KEYCODE_DPAD_DOWN -> menuCursor = (menuCursor + 1) % items.size
            KeyEvent.KEYCODE_DPAD_LEFT -> if (items[menuCursor] == NdsMenuItem.TIME) stepClock(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (items[menuCursor] == NdsMenuItem.TIME) stepClock(1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> pick(items[menuCursor])
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> setMenu(false)
        }
    }

    // The core is idle while the menu is open (paused), so states are taken / put back between frames.
    private fun pick(item: NdsMenuItem) {
        Log.i(TAG, "menu: $item")
        when (item) {
            NdsMenuItem.RESUME -> setMenu(false)
            NdsMenuItem.SAVE_STATE -> {
                hasState = NdsCore.saveState(stateFile.absolutePath)
                setMenu(false)
            }
            NdsMenuItem.LOAD_STATE -> if (stateFile.isFile) {
                NdsCore.loadState(stateFile.absolutePath)
                setMenu(false)
            }
            NdsMenuItem.FAST_FORWARD -> fastForward = !fastForward
            NdsMenuItem.HEAL_PARTY -> {
                // between frames the emulation thread is parked ([paused]): read the party, write it healed
                val game = feedGame
                if (game != null && NdsCore.readRam(NdsMons.RAM_BASE, feedRam)) {
                    // not in a battle: the battle keeps its own copy of the HP
                    if (NdsMons.battlers(feedRam).isEmpty()) NdsMons.healParty(feedRam, NdsFeed.partyAddr(feedRam, game), movePp, game.gen4, NdsCore::writeRam)
                }
                setMenu(false)
            }
            NdsMenuItem.TIME -> stepClock(1)
            NdsMenuItem.REPEL -> {
                infiniteRepel = !infiniteRepel
                Prefs(this).setInfiniteRepel(rom.name, infiniteRepel)
            }
            NdsMenuItem.SHORTCUTS -> {
                shortcuts = !shortcuts
                Prefs(this).battleShortcuts = shortcuts
            }
            NdsMenuItem.SWAP -> {
                setMenu(false)
                swap()
            }
            NdsMenuItem.CLOSE -> {
                setMenu(false)
                finish()
            }
        }
    }

    // A window losing focus mid-press never gets the key's release: let go of everything.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            keys = 0
            NdsCore.setKeys(0)
            ffHeld = false
        }
    }

    private fun swap() {
        swapped = !swapped
        layoutScreens()
    }

    companion object {
        private const val TAG = "pokedaisy/nds"
        /** Fast forward runs this many frames per frame shown (4x). */
        private const val FF_FRAMES = 4
        /** Frames between RUN tab updates (twice a second). */
        private const val FEED_EVERY = 30L

        /** INFINITE REPEL's top-up: a Max Repel's steps, rewritten once half are walked. */
        private const val REPEL_TOP = 250

        private val KEY_MAP = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to NdsCore.A,
            KeyEvent.KEYCODE_BUTTON_B to NdsCore.B,
            KeyEvent.KEYCODE_BUTTON_X to NdsCore.X,
            KeyEvent.KEYCODE_BUTTON_Y to NdsCore.Y,
            KeyEvent.KEYCODE_BUTTON_L1 to NdsCore.L,
            KeyEvent.KEYCODE_BUTTON_R1 to NdsCore.R,
            KeyEvent.KEYCODE_BUTTON_START to NdsCore.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to NdsCore.SELECT,
            KeyEvent.KEYCODE_DPAD_UP to NdsCore.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to NdsCore.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to NdsCore.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to NdsCore.RIGHT,
            // a keyboard (the test emulator): Z / X = A / B, A / S = Y / X, Q / W = L / R, Enter / Shift
            KeyEvent.KEYCODE_Z to NdsCore.A,
            KeyEvent.KEYCODE_X to NdsCore.B,
            KeyEvent.KEYCODE_A to NdsCore.Y,
            KeyEvent.KEYCODE_S to NdsCore.X,
            KeyEvent.KEYCODE_Q to NdsCore.L,
            KeyEvent.KEYCODE_W to NdsCore.R,
            KeyEvent.KEYCODE_ENTER to NdsCore.START,
            KeyEvent.KEYCODE_SHIFT_RIGHT to NdsCore.SELECT,
        )

        /** What the library plays here: DS ROMs. */
        fun isNdsRom(file: File) = file.extension.equals("nds", ignoreCase = true)
    }
}
