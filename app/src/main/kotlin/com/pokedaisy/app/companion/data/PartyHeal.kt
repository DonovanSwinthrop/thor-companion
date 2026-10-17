package com.pokedaisy.app.companion.data

/**
 * The QUICK MENU's HEAL PARTY on a GBA game: full HP and no status for every Pokémon, like a Pokémon Center.
 * struct Pokemon's party fields (status, HP, max HP; [PartyMonLayout]) sit after the encrypted box in plain text,
 * so they're written straight back. PP lives in the encrypted, checksummed box and isn't touched.
 */
object PartyHeal {
    /** Where the party fields can be written: a layout with whole u16 HP (not Quetzal's packed bits) and no R.O.W.E. struct. */
    fun supported(cfg: NativeConfig?): Boolean =
        cfg != null && cfg.playerParty != 0L && !cfg.partyMonLayout.rowe && cfg.partyMonLayout.hpBits == 16

    /**
     * Heals the party through [calls] (the emu thread, between frames). Not in a battle: the battle keeps its own
     * copies and writes them back when it ends. Returns how many were healed, -1 if it couldn't (a battle, no party).
     */
    fun heal(cfg: NativeConfig, calls: GameCalls): Int {
        if (!supported(cfg)) return -1
        if ((calls.read(cfg.gMainInBattle, 1)[0].toInt() and 0x02) != 0) return -1
        val count = calls.read(cfg.playerPartyCount, 1)[0].toInt() and 0xFF
        if (count !in 1..6) return -1
        val l = cfg.partyMonLayout
        val raw = calls.read(cfg.playerParty, count * cfg.monStride)
        var healed = 0
        for (i in 0 until count) {
            val off = i * cfg.monStride
            val mon = decodePartyMon(raw, off, l) ?: continue
            if (mon.isEgg) continue
            val maxHp = u16(raw, off + l.maxHp)
            if (maxHp == 0) continue
            val base = cfg.playerParty + off
            // status1: only its own bits (a shifted / masked field shares the word with others)
            val status = u32(raw, off + l.status)
            val cleared = status and (l.statusMask shl l.statusShift).inv()
            val ok = calls.write(base + l.hp, le(maxHp.toLong(), 2)) &&
                (cleared == status || calls.write(base + l.status, le(cleared, 4)))
            if (ok) healed++
        }
        return healed
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = u16(b, o).toLong() or (u16(b, o + 2).toLong() shl 16)
    private fun le(v: Long, n: Int) = ByteArray(n) { ((v ushr (8 * it)) and 0xFF).toByte() }
}
