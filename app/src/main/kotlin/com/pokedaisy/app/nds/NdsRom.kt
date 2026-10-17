package com.pokedaisy.app.nds

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads files out of a DS ROM, read-only: the header's file name table (FNT) and allocation table (FAT), and the
 * games' NARC archives (BTAF offsets, GMIF data). Enough for the pause menu's heal to take each move's base PP from
 * the game itself, so a hack that changed a move's PP heals to its own numbers.
 */
class NdsRom(private val file: File) {

    val code: String = read(12, 4).toString(Charsets.US_ASCII)

    private fun read(off: Long, len: Int): ByteArray = RandomAccessFile(file, "r").use { f ->
        ByteArray(len).also { f.seek(off); f.readFully(it) }
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = u16(b, o) or (u16(b, o + 2) shl 16)

    private val header = read(0, 0x50)
    private val fnt by lazy { read(u32(header, 0x40).toLong() and 0xFFFFFFFFL, u32(header, 0x44)) }
    private val fat by lazy { read(u32(header, 0x48).toLong() and 0xFFFFFFFFL, u32(header, 0x4C)) }

    /** The file at [path] ("a/0/2/1"), or null if the ROM has none. */
    fun file(path: String): ByteArray? {
        var dir = 0xF000
        val parts = path.split('/')
        for ((i, name) in parts.withIndex()) {
            val entryOff = u32(fnt, (dir and 0xFFF) * 8)
            var id = u16(fnt, (dir and 0xFFF) * 8 + 4)
            var p = entryOff
            var found = -1
            while (p < fnt.size) {
                val t = fnt[p].toInt() and 0xFF
                if (t == 0) break
                val len = t and 0x7F
                val n = String(fnt, p + 1, len, Charsets.US_ASCII)
                if (t and 0x80 != 0) {   // a sub-directory: its id follows the name
                    val sub = u16(fnt, p + 1 + len)
                    if (n == name && i < parts.size - 1) { found = sub; break }
                    p += 1 + len + 2
                } else {
                    if (n == name && i == parts.size - 1) { found = id; break }
                    id++
                    p += 1 + len
                }
            }
            if (found < 0) return null
            if (i == parts.size - 1) {
                val start = u32(fat, found * 8).toLong() and 0xFFFFFFFFL
                val end = u32(fat, found * 8 + 4).toLong() and 0xFFFFFFFFL
                return read(start, (end - start).toInt())
            }
            dir = found
        }
        return null
    }

    /** A NARC's files ([data] is the archive). */
    fun narc(data: ByteArray): List<ByteArray> {
        val headerSize = u16(data, 0x0C)
        val btaf = headerSize
        val count = u16(data, btaf + 8)
        val btnf = btaf + u32(data, btaf + 4)
        val gmif = btnf + u32(data, btnf + 4)
        return List(count) {
            val s = u32(data, btaf + 12 + 8 * it)
            val e = u32(data, btaf + 16 + 8 * it)
            data.copyOfRange(gmif + 8 + s, gmif + 8 + e)
        }
    }

    /**
     * A Gen 5 (I*) text file from `a/0/0/2`: its lines, decrypted (each line XOR'd with a key that rolls back from its
     * last character; 0xF100 starts a 9-bit packed line). Null outside Gen 5.
     */
    fun gen5Text(index: Int): List<String>? {
        if (!code.startsWith("IR")) return null
        val data = narc(file("a/0/0/2") ?: return null).getOrNull(index) ?: return null
        val lines = u16(data, 2)
        val block = u32(data, 12)
        return List(lines) { i ->
            val off = u32(data, block + 4 + 8 * i)
            val len = u16(data, block + 4 + 8 * i + 4)
            val enc = IntArray(len) { u16(data, block + off + 2 * it) }
            var key = enc.last() xor 0xFFFF
            val dec = IntArray(len)
            for (j in len - 1 downTo 0) {
                dec[j] = enc[j] xor key
                key = ((key ushr 3) or (key shl 13)) and 0xFFFF
            }
            var chars = dec.toList()
            if (chars.firstOrNull() == 0xF100) {
                val out = ArrayList<Int>()
                var bits = 0L
                var n = 0
                for (w in chars.drop(1)) {
                    bits = bits or (w.toLong() shl n)
                    n += 16
                    while (n >= 9) { out += (bits and 0x1FF).toInt(); bits = bits ushr 9; n -= 9 }
                }
                chars = out.map { if (it == 0x1FF) 0xFFFF else it }
            }
            buildString {
                for (c in chars) {
                    if (c == 0xFFFF) break
                    when (c) {
                        0xF000 -> {}
                        0x246D -> append('♂')
                        0x246E -> append('♀')
                        0xFFFE -> append(' ')
                        else -> if (c < 0xD800) append(c.toChar())
                    }
                }
            }
        }
    }

    /** Gen 5's species names (text 90) and nature names (text 27), in the game's own language. */
    fun gen5SpeciesNames(): List<String>? = gen5Text(90)
    fun gen5NatureNames(): List<String>? = gen5Text(27)

    /** Each species' base stats (hp, atk, def, spe, spa, spd) from Gen 5's personal data `a/0/1/6`. */
    fun gen5BaseStats(): List<IntArray>? {
        if (!code.startsWith("IR")) return null
        return narc(file("a/0/1/6") ?: return null).map { p -> IntArray(6) { if (p.size > it) p[it].toInt() and 0xFF else 0 } }
    }

    /**
     * Each move's base PP by move id, from the game's move table: Gen 5 (I*) `a/0/2/1`, PP at +5; Platinum
     * `poketool/waza/pl_waza_tbl.narc` and HeartGold / SoulSilver `a/0/1/1`, PP at +6. Null for a game it doesn't know.
     */
    fun movePp(): IntArray? {
        val (path, at) = when {
            code.startsWith("IR") -> "a/0/2/1" to 5
            code.startsWith("CPU") -> "poketool/waza/pl_waza_tbl.narc" to 6
            code.startsWith("IPK") || code.startsWith("IPG") -> "a/0/1/1" to 6
            else -> return null
        }
        val moves = narc(file(path) ?: return null)
        return IntArray(moves.size) { moves[it].let { m -> if (m.size > at) m[at].toInt() and 0xFF else 0 } }
    }
}
