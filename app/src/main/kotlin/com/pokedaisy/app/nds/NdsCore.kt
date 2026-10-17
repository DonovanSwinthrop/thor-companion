package com.pokedaisy.app.nds

/**
 * DS games: melonDS's core (`cpp/nds/pokedaisy_nds.cpp`). One game at a time, driven from [NdsActivity]'s
 * emulation thread. The free BIOS and generated firmware boot retail games directly: no BIOS files needed.
 */
object NdsCore {
    init {
        System.loadLibrary("pokedaisy_nds")
    }

    const val WIDTH = 256
    const val HEIGHT = 192

    /** The DS's buttons as [setKeys] takes them. */
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val RIGHT = 1 shl 4
    const val LEFT = 1 shl 5
    const val UP = 1 shl 6
    const val DOWN = 1 shl 7
    const val R = 1 shl 8
    const val L = 1 shl 9
    const val X = 1 shl 10
    const val Y = 1 shl 11

    /** A DS frame: 560190 cycles at 33.513982 MHz = 59.8261 Hz. */
    const val FRAME_NANOS = 16_715_000L

    /** Loads [rom] with [save] (read if it exists, written there as the game saves). False if it won't load. */
    @JvmStatic external fun load(rom: String, save: String, jit: Boolean): Boolean

    /** Runs one frame; both screens' pixels (ARGB, [WIDTH] x [HEIGHT]) go into [top] / [bottom]. */
    @JvmStatic external fun runFrame(top: IntArray, bottom: IntArray): Boolean

    /** Audio made so far, 48 kHz stereo interleaved; returns sample frames. */
    @JvmStatic external fun readAudio(out: ShortArray): Int

    /** The buttons held now (the bits above). */
    @JvmStatic external fun setKeys(pressed: Int)

    /** The stylus on the touch screen at ([x], [y]) in DS pixels, or lifted. */
    @JvmStatic external fun touch(x: Int, y: Int, down: Boolean)

    /** Writes the save to its file if the game changed it. */
    @JvmStatic external fun flushSave(): Boolean

    /** Main RAM (0x02000000-0x023FFFFF) at [addr] into [out]; false outside it or with no game. */
    @JvmStatic external fun readRam(addr: Int, out: ByteArray): Boolean

    @JvmStatic external fun saveState(path: String): Boolean
    @JvmStatic external fun loadState(path: String): Boolean

    /** Saves and lets the game go. */
    @JvmStatic external fun close()
}
