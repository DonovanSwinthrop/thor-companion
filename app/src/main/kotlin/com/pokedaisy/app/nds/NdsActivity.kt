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
import android.widget.LinearLayout
import android.widget.Toast
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
        layoutScreens()
    }

    /** The touch screen on the second display (or swapped onto the main one), else both stacked here. */
    private fun layoutScreens() {
        (topView.parent as? ViewGroup)?.removeView(topView)
        (bottomView.parent as? ViewGroup)?.removeView(bottomView)
        presentation?.releaseContent()
        presentation?.dismiss()
        presentation = null
        val second = Screens.second(this)?.takeIf { !Prefs(this).singleScreen }
        val (main, other) = if (swapped) bottomView to topView else topView to bottomView
        if (second != null) {
            setContentView(main)
            presentation = DualScreenPresentation(this, second) { other }.also { it.show() }
        } else {
            setContentView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
                addView(main, lp())
                addView(other, lp())
            })
        }
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
        if (::topView.isInitialized) NdsCore.close()
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
        while (running) {
            if (!NdsCore.runFrame(top, bottom)) break
            topView.show(top)
            bottomView.show(bottom)
            val n = NdsCore.readAudio(sound)
            if (n > 0) track.write(sound, 0, n * 2, AudioTrack.WRITE_NON_BLOCKING)
            if (++frames % 600 == 0L) {
                NdsCore.flushSave()   // every ~10 s, between frames
                val now = System.nanoTime()
                Log.i(TAG, "fps=%.1f".format(600e9 / (now - lastLog)))
                lastLog = now
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

    private fun swap() {
        swapped = !swapped
        layoutScreens()
    }

    companion object {
        private const val TAG = "pokedaisy/nds"

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
