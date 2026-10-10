package com.pokedaisy.app

/**
 * SETTINGS > ASPECT > WIDE: the overworld drawn past the GBA's 240 px, so the game fills a wide
 * screen with its own pixels square. The strips beside the real frame are rebuilt every frame
 * from the game's memory (map grid, tilesets, VRAM, sprites) by the native pk_wide.c, which
 * checks each frame against what the game actually drew and fades the strips out wherever it
 * can't (battles, menu screens, transitions). The middle is always the game's own frame.
 *
 * Its tables are per build: only the retail ROMs below so far (the QoL builds move every address).
 */
object WideScreen {
    /** pk_wide.h's PK_WIDE_* ids. */
    const val OFF = 0
    const val FIRERED_REV1 = 1
    const val EMERALD = 2

    /** Most GBA pixels drawn per side (pk_wide.h's PK_WIDE_MAX_MARGIN; 21:9 needs 67). */
    const val MAX_MARGIN = 96

    /** Retail ROMs by CRC32 ([SaveStates.crc32]'s form): FireRed (USA, Europe) rev 1, Emerald (USA, Europe). */
    private val BY_CRC = mapOf(
        "84ee4776" to FIRERED_REV1,
        "1f1c08fb" to EMERALD,
    )

    /** The native table for the ROM with this CRC32, or [OFF] if WIDE doesn't know it. */
    fun gameFor(crc: String): Int = BY_CRC[crc] ?: OFF

    /**
     * GBA pixels per side for a view [w] x [h] px: as many as fit with the game at the view's full
     * height (so its pixels stay square), the wide frame never wider than the view.
     */
    fun marginFor(w: Int, h: Int): Int {
        if (w <= 0 || h <= 0) return 0
        return ((w.toLong() * 160 / h - 240) / 2).toInt().coerceIn(0, MAX_MARGIN)
    }
}
