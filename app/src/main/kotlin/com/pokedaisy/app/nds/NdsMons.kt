package com.pokedaisy.app.nds

/**
 * Gen 4 / 5 Pokémon in DS main RAM (a 4 MB snapshot from 0x02000000, [NdsCore.readRam]).
 *
 * A Pokémon is the save format: PID, a flags word, a checksum, then four 32-byte blocks (A: species, item, OT,
 * exp, ability, EVs; B: moves, PP, IVs, nature; C: nickname; D: met data) shuffled by ((PID >> 13) & 31) % 24
 * and XOR'd with an LCG seeded by the checksum; a party Pokémon carries its battle stats after them (0x88..0xDB)
 * XOR'd with an LCG seeded by the PID. The checksum (the 64 decrypted words summed) is what tells a real one from
 * noise.
 *
 * In a battle Gen 5 keeps each battler in a heap block its allocator labels "btl_pokeparam.c" (the game's own
 * debug name, in the block header): species, max / current HP, ability and level in plain view, and a pointer to
 * the battle's copy of the full Pokémon. Found by that label, so no per-game battle address is needed.
 * Checked on Black 2 (US) RAM: the party at 0x0221E3EC, a rival battle's two battlers.
 */
object NdsMons {

    const val RAM_BASE = 0x02000000
    const val PARTY_MON_BYTES = 0xDC

    class Mon(
        val pid: Long,
        val species: Int,
        val item: Int,
        val otId: Int,
        val ability: Int,
        val nature: Int,
        val moves: IntArray,
        val pp: IntArray,
        /** hp, atk, def, spe, spa, spd: the games' own order. */
        val ivs: IntArray,
        val evs: IntArray,
        val isEgg: Boolean,
        /** Party data (null for a box Pokémon). */
        val level: Int?,
        val hp: Int?,
        val maxHp: Int?,
        /** hp, atk, def, spe, spa, spd. */
        val stats: IntArray?,
        val status: Int,
    )

    /** A battler: the live numbers, plus the battle's copy of the Pokémon. */
    class Battler(val species: Int, val hp: Int, val maxHp: Int, val ability: Int, val level: Int, val mon: Mon?,
                  /** The block's address the battle's party arrays point at. */
                  val addr: Long)

    /** Both sides' battlers, each side's Pokémon on the field first. */
    class Sides(val player: List<Battler>, val foe: List<Battler>)

    private val ORDERS = arrayOf(
        "ABCD", "ABDC", "ACBD", "ACDB", "ADBC", "ADCB", "BACD", "BADC", "BCAD", "BCDA", "BDAC", "BDCA",
        "CABD", "CADB", "CBAD", "CBDA", "CDAB", "CDBA", "DABC", "DACB", "DBAC", "DBCA", "DCAB", "DCBA",
    )

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)

    /** [len] bytes from [off], XOR'd word by word with the games' LCG from [seed]. */
    private fun crypt(src: ByteArray, off: Int, len: Int, seed: Long): ByteArray {
        val out = ByteArray(len)
        var s = seed
        var i = 0
        while (i < len) {
            s = (s * 0x41C64E6D + 0x6073) and 0xFFFFFFFFL
            val w = u16(src, off + i) xor (s ushr 16).toInt()
            out[i] = w.toByte()
            out[i + 1] = (w ushr 8).toByte()
            i += 2
        }
        return out
    }

    /** The Pokémon at [off] in [ram] (an offset from [RAM_BASE]), or null if no valid one is there. */
    fun decode(ram: ByteArray, off: Int, party: Boolean = true): Mon? {
        if (off < 0 || off + (if (party) PARTY_MON_BYTES else 0x88) > ram.size) return null
        val pid = u32(ram, off)
        val flags = u16(ram, off + 4)
        if (pid == 0L || flags and 0xFFF8 != 0) return null
        val chk = u16(ram, off + 6)
        val body = crypt(ram, off + 8, 0x80, chk.toLong())
        var sum = 0
        for (i in 0 until 0x80 step 2) sum += u16(body, i)
        if (sum and 0xFFFF != chk) return null
        // unshuffle into A B C D, each block at 0x20 * its letter
        val order = ORDERS[(((pid ushr 13) and 31) % 24).toInt()]
        val d = ByteArray(0x80)
        for (i in 0 until 4) System.arraycopy(body, i * 0x20, d, (order[i] - 'A') * 0x20, 0x20)
        val species = u16(d, 0x00)
        if (species == 0 || species > 2000) return null
        val ivWord = u32(d, 0x30)
        val ivs = IntArray(6) { ((ivWord ushr (5 * it)) and 31).toInt() }
        val evs = IntArray(6) { d[0x10 + it].toInt() and 0xFF }
        var level: Int? = null
        var hp: Int? = null
        var maxHp: Int? = null
        var stats: IntArray? = null
        var status = 0
        if (party) {
            val s = crypt(ram, off + 0x88, 0x14, pid)
            status = u32(s, 0).toInt()
            level = s[4].toInt() and 0xFF
            hp = u16(s, 6)
            maxHp = u16(s, 8)
            stats = IntArray(6) { u16(s, 8 + 2 * it) }
        }
        return Mon(
            pid = pid, species = species, item = u16(d, 0x02), otId = u16(d, 0x04),
            ability = d[0x0D].toInt() and 0xFF, nature = d[0x39].toInt() and 0xFF,
            moves = IntArray(4) { u16(d, 0x20 + 2 * it) }, pp = IntArray(4) { d[0x28 + it].toInt() and 0xFF },
            ivs = ivs, evs = evs, isEgg = (ivWord ushr 30) and 1L == 1L,
            level = level, hp = hp, maxHp = maxHp, stats = stats, status = status,
        )
    }

    /** The party at [addr] (its count is the u32 before it): up to 6 Pokémon. */
    fun party(ram: ByteArray, addr: Int): List<Mon> {
        val off = addr - RAM_BASE
        if (off < 4 || off > ram.size) return emptyList()
        val count = u32(ram, off - 4).toInt()
        if (count !in 1..6) return emptyList()
        return (0 until count).mapNotNull { decode(ram, off + it * PARTY_MON_BYTES) }
    }

    private val TAG = "btl_pokeparam.c".toByteArray() + 0

    /** The battlers alive in RAM now (each once: the game keeps two copies of each), in memory order. */
    fun battlers(ram: ByteArray): List<Battler> {
        val out = ArrayList<Battler>()
        val seen = HashSet<Long>()
        var i = indexOf(ram, TAG, 0)
        while (i >= 0) {
            val s = i + 0x20
            if (s + 0x14 <= ram.size) {
                val species = u16(ram, s + 4)
                val maxHp = u16(ram, s + 6)
                val hp = u16(ram, s + 8)
                val level = ram[s + 0x10].toInt() and 0xFF
                val ptr = u32(ram, s - 8)
                // the label also sits in the game's code, with no battler after it
                if (species in 1..2000 && maxHp in 1..999 && hp <= maxHp && level in 1..100 &&
                    ptr in RAM_BASE.toLong() until RAM_BASE.toLong() + ram.size
                ) {
                    val mon = decode(ram, (ptr - RAM_BASE).toInt())
                    if (mon == null || seen.add(mon.pid)) {
                        out += Battler(species, hp, maxHp, u16(ram, s + 0xE), level, mon, RAM_BASE.toLong() + i + 0x18)
                    }
                }
            }
            i = indexOf(ram, TAG, i + 1)
        }
        return out
    }

    /**
     * [battlers] split into the player's ([partyPids]: the party's PIDs) and the foe's, each with the one on the
     * field first. The battle keeps each side's party as an array of pointers to these blocks and reorders it on a
     * switch, so the field's Pokémon is the first entry: the first such array in memory decides the order.
     */
    fun sides(ram: ByteArray, battlers: List<Battler>, partyPids: Set<Long>): Sides {
        val (mine, theirs) = battlers.partition { it.mon != null && it.mon.pid in partyPids }
        return Sides(fieldFirst(ram, mine), fieldFirst(ram, theirs))
    }

    private fun fieldFirst(ram: ByteArray, side: List<Battler>): List<Battler> {
        if (side.size < 2) return side
        val byAddr = side.associateBy { it.addr }
        var o = 0
        while (o + 8 <= ram.size) {
            val a = byAddr[u32(ram, o)]
            if (a != null && byAddr[u32(ram, o + 4)] != null) {
                // the array, from here: its order first, then any it doesn't list
                val listed = ArrayList<Battler>()
                var k = o
                while (k + 4 <= ram.size) {
                    val b = byAddr[u32(ram, k)] ?: break
                    if (b in listed) break
                    listed += b
                    k += 4
                }
                return listed + side.filter { it !in listed }
            }
            o += 4
        }
        return side
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        val first = needle[0]
        var i = from
        val last = hay.size - needle.size
        while (i <= last) {
            if (hay[i] == first) {
                var k = 1
                while (k < needle.size && hay[i + k] == needle[k]) k++
                if (k == needle.size) return i
            }
            i++
        }
        return -1
    }
}
