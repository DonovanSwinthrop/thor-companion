package com.pokedaisy.app.nds

/**
 * INFINITE REPEL's "no wild Pokémon at all": a Repel only keeps away wild Pokémon below the lead's level (a low-level
 * lead meets everything), so the map's own encounter rates are set to 0 while it's on. Fishing is left alone.
 *
 * The current map's encounter file is copied from the ROM into RAM as it is, so the app reads the map's number, takes
 * that file from the ROM and finds its exact bytes in RAM (after the rates, which may already be 0), at an address no
 * fixed pointer leads to:
 *  - Platinum / Renegade Platinum: map headers in the game code in RAM (0x020E601C, 24 bytes a map, file u16 +0x0E,
 *    0xFFFF none), files in `fielddata/encountdata/pl_enc_data.narc`: grass rate u32 +0, twelve (level, species) u32
 *    pairs, surf rate u32 +0xCC, Rock Smash rate u32 +0xF8 (then the rods). Checked: map 356 -> file 150 -> one copy
 *    at 0x022A15A8, grass rate 30.
 *  - Black / White 2 and Black / White (and their hacks): map headers in the ROM (`a/0/1/2`, 48 bytes a map, file u16
 *    +0x14 low 13 bits), files in `a/1/2/7` (BW2) or `a/1/2/6` (BW): 232 bytes a season (one or four): 8 rate bytes
 *    (grass, dark grass, shaking grass, surf, rippling surf, fishing, rippling fishing, a flag) then the slots. Checked:
 *    Route 19 (map 437, file 128) in every Black 2 dump there, at 0x0223B7C0.
 * HeartGold / SoulSilver: not yet.
 */
class NdsEncounters(private val rom: NdsRom, private val game: NdsFeed.Game) {

    private val platinum = game.pack in setOf("pt", "rp")
    private val gen5 = game.pack in setOf("b2", "bb2r", "b1", "bbk")

    private val files: List<ByteArray>? by lazy {
        val path = when {
            platinum -> PT_ENC
            gen5 && rom.code.startsWith("IRB") -> "a/1/2/6"
            gen5 -> "a/1/2/7"
            else -> null
        } ?: return@lazy null
        rom.file(path)?.let { rom.narc(it) }
    }

    /** Black / White's map headers (from the ROM; Platinum's are read from RAM). */
    private val gen5Zones: ByteArray? by lazy { if (gen5) rom.file("a/0/1/2")?.let { rom.narc(it).firstOrNull() } else null }

    private var foundAt = -1
    private var foundKey: ByteArray? = null

    val supported get() = (platinum || gen5) && files != null && (platinum || gen5Zones != null)

    /** Keeps the current map's encounter rates at 0 (every few frames while INFINITE REPEL is on); true when blocked. */
    fun block(ram: ByteArray, zone: Int, write: (Int, ByteArray) -> Boolean): Boolean {
        val t = table(ram, zone) ?: return false
        for ((off, len) in rates()) {
            if ((0 until len).any { ram[t.at + off + it].toInt() != 0 }) write(NdsMons.RAM_BASE + t.at + off, ByteArray(len))
        }
        return true
    }

    /** Puts the current map's rates back (INFINITE REPEL switched off): the ROM's own values. */
    fun restore(ram: ByteArray, zone: Int, write: (Int, ByteArray) -> Boolean) {
        val t = table(ram, zone) ?: return
        for ((off, len) in rates()) write(NdsMons.RAM_BASE + t.at + off, t.rom.copyOfRange(off, off + len))
    }

    /** Which bytes are rates (offset, length): grass, surf, Rock Smash for Platinum; the five non-fishing ones for Gen 5. */
    private fun rates(): List<Pair<Int, Int>> =
        if (platinum) listOf(0x00 to 4, 0xCC to 4, 0xF8 to 4) else listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1)

    /** The loaded table: where it is in RAM and the ROM's bytes for it (that season's, for Gen 5). */
    private class Table(val at: Int, val rom: ByteArray)

    private fun table(ram: ByteArray, zone: Int): Table? {
        val id = wildFile(ram, zone)
        if (id < 0) return null
        val file = files?.getOrNull(id) ?: return null
        // the candidates: the whole file (Platinum), or each season's 232 bytes (Gen 5)
        val parts = if (platinum) listOf(file) else (0 until file.size / GEN5_SEASON).map {
            file.copyOfRange(it * GEN5_SEASON, (it + 1) * GEN5_SEASON)
        }
        for (part in parts) {
            val skip = if (platinum) 4 else 8                     // after the rates (which may be 0 already)
            if (part.size < skip + KEY_BYTES) continue
            val key = part.copyOfRange(skip, skip + KEY_BYTES)
            if (key.all { it.toInt() == 0 }) continue             // an empty table matches anywhere: never write it
            if (foundKey?.contentEquals(key) == true && matches(ram, foundAt + skip, key)) return Table(foundAt, part)
            val i = indexOf(ram, key)
            if (i < skip) continue
            foundAt = i - skip
            foundKey = key
            return Table(foundAt, part)
        }
        return null
    }

    /** The encounter file number of map [zone], or -1. */
    private fun wildFile(ram: ByteArray, zone: Int): Int {
        if (zone < 0) return -1
        val n = files?.size ?: return -1
        val id = if (platinum) {
            if (zone >= PT_MAPS) return -1
            val o = PT_HEADERS - NdsMons.RAM_BASE + zone * 24 + 0x0E
            if (o < 0 || o + 2 > ram.size) return -1
            u16(ram, o).takeIf { it != 0xFFFF } ?: return -1
        } else {
            val z = gen5Zones ?: return -1
            val o = zone * 48 + 0x14
            if (o + 2 > z.size) return -1
            u16(z, o) and 0x1FFF
        }
        return if (id < n) id else -1
    }

    private fun matches(ram: ByteArray, at: Int, key: ByteArray): Boolean {
        if (at < 0 || at + key.size > ram.size) return false
        for (k in key.indices) if (ram[at + k] != key[k]) return false
        return true
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        val first = needle[0]
        var i = 0
        val last = hay.size - needle.size
        while (i <= last) {
            if (hay[i] == first && matches(hay, i, needle)) return i
            i++
        }
        return -1
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    companion object {
        private const val PT_ENC = "fielddata/encountdata/pl_enc_data.narc"
        private const val PT_HEADERS = 0x020E601C
        private const val PT_MAPS = 593
        private const val GEN5_SEASON = 232
        /** How many bytes after the rates must match: Platinum's twelve grass slots (96 B), Gen 5's twelve (48 B). */
        private const val KEY_BYTES = 48
    }
}
