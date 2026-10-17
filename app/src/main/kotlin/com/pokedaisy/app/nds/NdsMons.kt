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

    /** Gen 4's party Pokémon is 0xEC bytes: the same 0x88-byte box and battle stats at +0x88, with more after them. */
    const val GEN4_PARTY_MON_BYTES = 0xEC

    fun partyBytes(gen4: Boolean) = if (gen4) GEN4_PARTY_MON_BYTES else PARTY_MON_BYTES

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
        /** Where and at what level it was met (block D): a place name id, as the zone table's. */
        val metLocation: Int,
        val metLevel: Int,
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
    fun decode(ram: ByteArray, off: Int, party: Boolean = true, gen4: Boolean = false): Mon? {
        if (off < 0 || off + (if (party) partyBytes(gen4) else 0x88) > ram.size) return null
        val pid = u32(ram, off)
        val flags = u16(ram, off + 4)
        val chk = u16(ram, off + 6)
        // event Pokemon can have PID 0: only an all-zero header is an empty slot
        if ((pid == 0L && chk == 0) || flags and 0xFFF8 != 0) return null
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
            // Gen 4 has no nature byte: it's the PID mod 25
            ability = d[0x0D].toInt() and 0xFF, nature = if (gen4) (pid % 25).toInt() else d[0x39].toInt() and 0xFF,
            moves = IntArray(4) { u16(d, 0x20 + 2 * it) }, pp = IntArray(4) { d[0x28 + it].toInt() and 0xFF },
            ivs = ivs, evs = evs, isEgg = (ivWord ushr 30) and 1L == 1L,
            // Platinum / HGSS keep the met place in block B (+0x46); Gen 5 in block D
            metLocation = u16(d, if (gen4) 0x46 else 0x78), metLevel = d[0x7C].toInt() and 0x7F,
            level = level, hp = hp, maxHp = maxHp, stats = stats, status = status,
        )
    }

    /**
     * Heals the party in [ram] (a snapshot): full HP and no status for every Pokémon, written back to the game
     * through [write] (address, bytes). The battle stats (0x88..) are XOR'd with a stream seeded by the PID, so
     * the same crypt that reads them writes them. With [basePp] (each move's base PP by move id, [NdsRom.movePp])
     * PP is filled too: it lives in block B, inside the checksummed, encrypted body, so the body is decrypted, its
     * PP set to base + base * ppUps / 5 (the games' CalculatePPWithBonus), summed for the new checksum and encrypted
     * again with it. Returns how many were healed.
     */
    fun healParty(ram: ByteArray, addr: Int, basePp: IntArray? = null, gen4: Boolean = false, write: (Int, ByteArray) -> Boolean): Int {
        val stride = partyBytes(gen4)
        val off = addr - RAM_BASE
        val count = if (off >= 4 && off < ram.size) u32(ram, off - 4).toInt() else 0
        if (count !in 1..6) return 0
        var healed = 0
        for (i in 0 until count) {
            val mon = off + i * stride
            if (decode(ram, mon, gen4 = gen4) == null) continue
            val pid = u32(ram, mon)
            if (basePp != null) {
                val body = crypt(ram, mon + 8, 0x80, u16(ram, mon + 6).toLong())
                val b = ORDERS[(((pid ushr 13) and 31) % 24).toInt()].indexOf('B') * 0x20
                for (k in 0 until 4) {
                    val move = u16(body, b + 2 * k)
                    val base = basePp.getOrElse(move) { 0 }
                    if (move == 0 || base == 0) continue
                    val ups = body[b + 0x0C + k].toInt() and 3
                    body[b + 0x08 + k] = (base + base * ups / 5).toByte()
                }
                var sum = 0
                for (w in 0 until 0x80 step 2) sum += u16(body, w)
                val chk = sum and 0xFFFF
                val out = ByteArray(2 + 0x80)
                out[0] = chk.toByte()
                out[1] = (chk ushr 8).toByte()
                // crypt() XORs from its source offset, so the plain body goes through it at the new checksum's seed
                System.arraycopy(crypt(body, 0, 0x80, chk.toLong()), 0, out, 2, 0x80)
                write(addr + i * stride + 6, out)
            }
            val stats = crypt(ram, mon + 0x88, 0x14, pid)
            // status word 0..3, level 4, hp 6, maxHp 8
            for (k in 0 until 4) stats[k] = 0
            stats[6] = stats[8]
            stats[7] = stats[9]
            // back through the same crypt: crypt() reads from a ByteArray offset, so lay the plain bytes in a scratch copy
            val scratch = ByteArray(0x14 + 0x88)
            System.arraycopy(stats, 0, scratch, 0x88, 0x14)
            val enc = crypt(scratch, 0x88, 0x14, pid)
            if (write(addr + i * stride + 0x88, enc)) healed++
        }
        return healed
    }

    /**
     * The stats (hp, atk, def, spe, spa, spd) the games compute: ((2B + IV + EV/4) L / 100) + 5, times 1.1 / 0.9 for the
     * stat [nature] raises / lowers (natures go atk, def, spe, spa, spd: raised = nature / 5, lowered = nature % 5);
     * HP is (2B + IV + EV/4) L / 100 + L + 10.
     */
    fun stats(base: IntArray, ivs: IntArray, evs: IntArray, level: Int, nature: Int): IntArray = IntArray(6) { s ->
        val core = (2 * base[s] + ivs[s] + evs[s] / 4) * level / 100
        if (s == 0) core + level + 10
        else {
            val v = core + 5
            val up = nature / 5 + 1
            val down = nature % 5 + 1
            when {
                up == down -> v
                s == up -> v * 110 / 100
                s == down -> v * 90 / 100
                else -> v
            }
        }
    }

    private fun shuffle(pid: Long) = ORDERS[(((pid ushr 13) and 31) % 24).toInt()]

    /**
     * Gives party slot [slot] at [addr] the nature [nature] (0-24), the way a nature-changing item would, and computes
     * its battle stats again from [base] (the species' base stats), its IVs, EVs and level; HP and max HP are kept.
     * Gen 5 keeps the nature in a byte of block B (the checksummed body: decrypted, changed, summed, encrypted again).
     * Gen 4 ([gen4]) has no such byte: the nature is the PID mod 25, so the Pokémon gets a new PID ([gen4Pid]), its
     * blocks are shuffled into the new PID's order and everything after the body is encrypted again with it.
     * False if the slot holds no valid Pokémon (or, Gen 4, no PID keeps what it has to).
     */
    fun setNature(ram: ByteArray, addr: Int, slot: Int, nature: Int, base: IntArray, gen4: Boolean = false,
                  write: (Int, ByteArray) -> Boolean): Boolean {
        val stride = partyBytes(gen4)
        val mon = addr - RAM_BASE + slot * stride
        val m = decode(ram, mon, gen4 = gen4) ?: return false
        val pid = u32(ram, mon)
        var body = crypt(ram, mon + 8, 0x80, u16(ram, mon + 6).toLong())
        var newPid = pid
        if (gen4) {
            val plain = ByteArray(0x80)   // A B C D
            val old = shuffle(pid)
            for (i in 0 until 4) System.arraycopy(body, i * 0x20, plain, (old[i] - 'A') * 0x20, 0x20)
            newPid = gen4Pid(pid, u16(plain, 0x04), u16(plain, 0x06), nature) ?: return false
            val now = shuffle(newPid)
            body = ByteArray(0x80)
            for (i in 0 until 4) System.arraycopy(plain, (now[i] - 'A') * 0x20, body, i * 0x20, 0x20)
        } else {
            body[shuffle(pid).indexOf('B') * 0x20 + 0x19] = nature.toByte()
        }
        var sum = 0
        for (w in 0 until 0x80 step 2) sum += u16(body, w)
        val chk = sum and 0xFFFF
        val out = ByteArray(0x88)
        System.arraycopy(ram, mon, out, 0, 6)   // flags kept
        for (k in 0 until 4) out[k] = (newPid ushr (8 * k)).toByte()
        out[6] = chk.toByte()
        out[7] = (chk ushr 8).toByte()
        System.arraycopy(crypt(body, 0, 0x80, chk.toLong()), 0, out, 8, 0x80)
        // the battle stats and the rest after the body: one stream from the PID
        val ext = crypt(ram, mon + 0x88, stride - 0x88, pid)
        val level = ext[4].toInt() and 0xFF
        val now = stats(base, m.ivs, m.evs, level, nature)
        for (s in 1 until 6) {   // atk..spd at +0x0A..; hp / max hp (+6 / +8) stay
            ext[0x08 + 2 * s] = now[s].toByte()
            ext[0x09 + 2 * s] = (now[s] ushr 8).toByte()
        }
        if (!write(addr + slot * stride, out)) return false
        return write(addr + slot * stride + 0x88, crypt(ext, 0, ext.size, newPid))
    }

    /**
     * A Gen 4 PID like [pid] with nature [nature] (PID mod 25) that keeps what else the PID decides: the low byte
     * (gender against the species' ratio, ability slot in bit 0), shiny or not (trainer id [tid] ^ secret id [sid] ^
     * both PID halves < 8), and Wurmple's evolution ((PID >> 16) mod 10 < 5). Searches the high half first, then the
     * low half's high byte too (a shiny's high half is tied to the low half). Null if none fits.
     */
    fun gen4Pid(pid: Long, tid: Int, sid: Int, nature: Int): Long? {
        val lo = (pid and 0xFFFF).toInt()
        val hi = (pid ushr 16).toInt()
        val shiny = (tid xor sid xor lo xor hi) < 8
        val wurmple = hi % 10 < 5
        for (d in 0 until 256) {
            val newLo = ((((lo ushr 8) + d) and 0xFF) shl 8) or (lo and 0xFF)
            val his = if (shiny) (0 until 8).map { tid xor sid xor newLo xor it }
                      else (1..0xFFFF).map { (hi + it) and 0xFFFF }
            for (h in his) {
                val p = (h.toLong() shl 16) or newLo.toLong()
                if (p % 25 != nature.toLong() || (h % 10 < 5) != wurmple) continue
                if (!shiny && (tid xor sid xor newLo xor h) < 8) continue
                return p
            }
        }
        return null
    }

    /** The party at [addr] (its count is the u32 before it): up to 6 Pokémon. */
    fun party(ram: ByteArray, addr: Int, gen4: Boolean = false): List<Mon> {
        val off = addr - RAM_BASE
        if (off < 4 || off > ram.size) return emptyList()
        val count = u32(ram, off - 4).toInt()
        if (count !in 1..6) return emptyList()
        return (0 until count).mapNotNull { decode(ram, off + it * partyBytes(gen4), gen4 = gen4) }
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
