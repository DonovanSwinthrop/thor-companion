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

    /**
     * A Gen 4 text file (Platinum `msgdata/pl_msg.narc`, HeartGold / SoulSilver `a/0/2/7`): its lines, decrypted. The
     * table's (offset, length) pairs are XOR'd with seed * 0x2FD * (i + 1), each line's characters with a key from
     * 0x91BD3 * (i + 1) stepping 0x493D; 0xF100 starts a 9-bit line packed 15 bits to a word. The characters are the
     * games' own table: letters, digits and the punctuation names use. Null outside Gen 4.
     */
    fun gen4Text(index: Int): List<String>? {
        val path = when {
            code.startsWith("CPU") -> "msgdata/pl_msg.narc"
            code.startsWith("IPK") || code.startsWith("IPG") -> "a/0/2/7"
            else -> return null
        }
        val data = narc(file(path) ?: return null).getOrNull(index) ?: return null
        val lines = u16(data, 0)
        val seed = u16(data, 2)
        return List(lines) { i ->
            val k = (seed * 0x2FD * (i + 1)) and 0xFFFF
            val key = k or (k shl 16)
            val off = u32(data, 4 + 8 * i) xor key
            val len = u32(data, 8 + 8 * i) xor key
            var ck = (0x91BD3 * (i + 1)) and 0xFFFF
            var chars = List(len) { j -> (u16(data, off + 2 * j) xor ck).also { ck = (ck + 0x493D) and 0xFFFF } }
            if (chars.firstOrNull() == 0xF100) {
                val out = ArrayList<Int>()
                var bits = 0L
                var n = 0
                for (w in chars.drop(1)) {
                    bits = bits or ((w and 0x7FFF).toLong() shl n)
                    n += 15
                    while (n >= 9) { out += (bits and 0x1FF).toInt(); bits = bits ushr 9; n -= 9 }
                }
                chars = out.map { if (it == 0x1FF) 0xFFFF else it }
            }
            buildString {
                for (c in chars) {
                    if (c == 0xFFFF) break
                    when (c) {
                        in 0x121..0x12A -> append('0' + (c - 0x121))
                        in 0x12B..0x144 -> append('A' + (c - 0x12B))
                        in 0x145..0x15E -> append('a' + (c - 0x145))
                        0x1DE -> append(' ')
                        0x188 -> append('é')
                        0x1AB -> append('!')
                        0x1AC -> append('?')
                        0x1AD -> append(',')
                        0x1AE -> append('.')
                        0x1B3 -> append('\'')
                        0x1BB -> append('♂')
                        0x1BC -> append('♀')
                        0x1BE -> append('-')
                        0x1C2 -> append('&')
                        0x1C4 -> append(':')
                        0x1E0 -> append("PK")
                        0x1E1 -> append("MN")
                    }
                }
            }
        }
    }

    /** Species names, in the game's own language (Gen 5 text 90; Platinum 412, HGSS 821, in capitals there, so title case). */
    fun speciesNames(): List<String>? = when {
        code.startsWith("IR") -> gen5Text(90)
        code.startsWith("CPU") -> gen4Text(412)?.map(::titleCase)
        code.startsWith("IPK") || code.startsWith("IPG") -> gen4Text(821)?.map(::titleCase)
        else -> null
    }

    /** Nature names, Hardy to Quirky (Gen 5 text 27; Platinum 202, HGSS 34). */
    fun natureNames(): List<String>? = when {
        code.startsWith("IR") -> gen5Text(27)
        code.startsWith("CPU") -> gen4Text(202)
        code.startsWith("IPK") || code.startsWith("IPG") -> gen4Text(34)
        else -> null
    }

    /**
     * Each species' base stats (hp, atk, def, spe, spa, spd), the first six bytes of its personal data: Gen 5
     * `a/0/1/6`, Platinum `poketool/personal/pl_personal.narc`, HGSS `a/0/0/2`. A hack's changed stats come with it.
     */
    fun baseStats(): List<IntArray>? {
        val path = when {
            code.startsWith("IR") -> "a/0/1/6"
            code.startsWith("CPU") -> "poketool/personal/pl_personal.narc"
            code.startsWith("IPK") || code.startsWith("IPG") -> "a/0/0/2"
            else -> return null
        }
        return narc(file(path) ?: return null).map { p -> IntArray(6) { if (p.size > it) p[it].toInt() and 0xFF else 0 } }
    }

    private fun titleCase(s: String): String {
        if (s.any { it.isLowerCase() }) return s
        val b = StringBuilder(s.lowercase())
        for (i in b.indices) if (i == 0 || b[i - 1] == ' ' || b[i - 1] == '-') b[i] = b[i].uppercaseChar()
        return b.toString()
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
