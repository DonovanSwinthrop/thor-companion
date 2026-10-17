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
    private var swapped = false

    @Volatile private var running = false
    private var thread: Thread? = null
    private var audio: AudioTrack? = null
    private var keys = 0

    private val owner = ComposeHostOwner()
    private var menuOpen by mutableStateOf(false)
    private var menuCursor by mutableIntStateOf(0)
    private var fastForward by mutableStateOf(false)
    private var hasState by mutableStateOf(false)
    @Volatile private var paused = false
    @Volatile private var ffHeld = false
    private val stateFile by lazy { File(File(filesDir, "nds-states").apply { mkdirs() }, rom.nameWithoutExtension + ".state") }
    private val menuView by lazy {
        ComposeView(this).apply {
            setContent {
                AppTheme {
                    if (menuOpen) NdsMenu(GameTitles.label(this@NdsActivity, Prefs(this@NdsActivity), rom), menuCursor, fastForward, hasState, ::pick)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(LibraryActivity.EXTRA_ROM)
        rom = path?.let(::File)?.takeIf { it.isFile } ?: run { finish(); return }
        save = SavesLocation.saveFor(this, Prefs(this), rom)
        if (save.exists()) SaveBackups.snapshot(save)
        val touch: (Int, Int, Boolean) -> Unit = { x, y, down -> NdsCore.touch(x, y, down) }
        topView = NdsScreenView(this)
        bottomView = NdsScreenView(this, touch)
        if (!NdsCore.load(rom.absolutePath, save.absolutePath, jit = true)) {
            Toast.makeText(this, "This DS game wouldn't load.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        owner.create()
        hasState = stateFile.isFile
        layoutScreens()
    }

    /** The touch screen on the second display (or swapped onto the main one), else both stacked here. */
    private fun layoutScreens() {
        (topView.parent as? ViewGroup)?.removeView(topView)
        (bottomView.parent as? ViewGroup)?.removeView(bottomView)
        (menuView.parent as? ViewGroup)?.removeView(menuView)
        presentation?.releaseContent()
        presentation?.dismiss()
        presentation = null
        val second = Screens.second(this)?.takeIf { !Prefs(this).singleScreen }
        val (main, other) = if (swapped) bottomView to topView else topView to bottomView
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
        start()
    }

    override fun onPause() {
        stop()
        NdsCore.flushSave()
        super.onPause()
    }

    override fun onDestroy() {
        stop()
        presentation?.dismiss()
        if (::topView.isInitialized) {
            NdsCore.close()
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
                NdsCore.runFrame(top, bottom)
                NdsCore.readAudio(sound)
                frames++
            }
            if (!NdsCore.runFrame(top, bottom)) break
            topView.show(top)
            bottomView.show(bottom)
            val n = NdsCore.readAudio(sound)
            if (n > 0 && !ff) track.write(sound, 0, n * 2, AudioTrack.WRITE_NON_BLOCKING)
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
            if (keys and (NdsCore.SELECT or NdsCore.R) == NdsCore.SELECT or NdsCore.R) {
                keys = keys and (NdsCore.SELECT or NdsCore.R).inv()
                swap()
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            keys = keys and bit.inv()
        }
        NdsCore.setKeys(keys)
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
        }
    }

    private fun menuKey(code: Int) {
        val items = NdsMenuItem.entries
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> menuCursor = (menuCursor + items.size - 1) % items.size
            KeyEvent.KEYCODE_DPAD_DOWN -> menuCursor = (menuCursor + 1) % items.size
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

    private fun swap() {
        swapped = !swapped
        layoutScreens()
    }

    companion object {
        private const val TAG = "pokedaisy/nds"
        /** Fast forward runs this many frames per frame shown (3x). */
        private const val FF_FRAMES = 3

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
