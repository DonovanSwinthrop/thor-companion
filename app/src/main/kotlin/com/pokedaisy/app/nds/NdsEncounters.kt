package com.pokedaisy.app.nds

/**
 * INFINITE REPEL's "no wild Pokémon at all": a Repel only keeps away wild Pokémon below the lead's level (a low-level
 * lead meets everything), so the map's own encounter rates are set to 0 while it's on.
 *
 * Platinum (and Renegade Platinum) load the current map's encounter file from the ROM into RAM as it is: grass rate
 * u32 +0, twelve (level, species) u32 pairs, surf rate u32 +0xCC, Rock Smash rate u32 +0xF8 (then the rods, left alone:
 * fishing is a choice). Its map headers sit in the game code in RAM (0x020E601C, 24 bytes a map, the encounter file
 * number u16 +0x0E, 0xFFFF for none), so the app reads the current map's number, takes that file from the ROM and
 * finds its exact bytes in RAM: one copy, at a heap address that changes (no fixed pointer leads to it). Checked on a
 * Platinum dump: map 356 -> file 150 -> one copy at 0x022A15A8, grass rate 30. Black 2 and HeartGold / SoulSilver
 * keep theirs in another form: not yet.
 */
class NdsEncounters(private val rom: NdsRom, private val game: NdsFeed.Game) {

    private val platinum = game.pack in setOf("pt", "rp")
    private val files: List<ByteArray>? by lazy {
        if (!platinum) null else rom.file(PT_ENC)?.let { rom.narc(it) }
    }

    /** Where the blocked table is ([found]) and which file it is, so a map change finds the new one. */
    private var foundAt = -1
    private var foundFile = -1

    val supported get() = platinum && files != null

    /**
     * Keeps the current map's encounter rates at 0 (call it every few frames while INFINITE REPEL is on). Returns
     * true when a table is blocked now.
     */
    fun block(ram: ByteArray, zone: Int, write: (Int, ByteArray) -> Boolean): Boolean {
        val id = wildFile(ram, zone)
        if (id < 0) return false
        val at = locate(ram, id)
        if (at < 0) return false
        for (off in RATES) {
            if (u32(ram, at + off) != 0L) write(NdsMons.RAM_BASE + at + off, ByteArray(4))
        }
        return true
    }

    /** Puts the current map's rates back (INFINITE REPEL switched off): the ROM's own values. */
    fun restore(ram: ByteArray, zone: Int, write: (Int, ByteArray) -> Boolean) {
        val id = wildFile(ram, zone)
        if (id < 0) return
        val at = locate(ram, id)
        val file = files?.getOrNull(id) ?: return
        if (at < 0) return
        for (off in RATES) if (off + 4 <= file.size) write(NdsMons.RAM_BASE + at + off, file.copyOfRange(off, off + 4))
    }

    /** The encounter file number of map [zone], or -1. */
    private fun wildFile(ram: ByteArray, zone: Int): Int {
        if (!platinum || zone < 0 || zone >= PT_MAPS) return -1
        val o = PT_HEADERS - NdsMons.RAM_BASE + zone * 24 + 0x0E
        if (o < 0 || o + 2 > ram.size) return -1
        val id = u16(ram, o)
        return if (id == 0xFFFF || id >= (files?.size ?: 0)) -1 else id
    }

    /**
     * The RAM copy of encounter file [id]: its bytes after the grass rate (the rate itself may already be 0), matched
     * exactly. The last place found is checked first, so a full search runs only on a map change.
     */
    private fun locate(ram: ByteArray, id: Int): Int {
        val file = files?.getOrNull(id) ?: return -1
        if (file.size < 0x100) return -1
        val key = file.copyOfRange(4, 4 + KEY_BYTES)
        if (foundFile == id && foundAt >= 0 && matches(ram, foundAt + 4, key)) return foundAt
        val i = indexOf(ram, key)
        if (i < 4) return -1
        foundAt = i - 4
        foundFile = id
        return foundAt
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
    private fun u32(b: ByteArray, o: Int) = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)

    companion object {
        private const val PT_ENC = "fielddata/encountdata/pl_enc_data.narc"
        private const val PT_HEADERS = 0x020E601C
        private const val PT_MAPS = 593
        /** Grass, surf and Rock Smash rates (fishing's are left alone). */
        private val RATES = intArrayOf(0x00, 0xCC, 0xF8)
        /** How many of the file's bytes after the grass rate must match: the twelve grass slots. */
        private const val KEY_BYTES = 96
    }
}
