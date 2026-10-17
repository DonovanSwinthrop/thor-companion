package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The QUICK MENU's HEAL ([PartyHeal]) on real RAM: a fixture's party is hurt and poisoned in a copy, healed through
 * a [GameCalls] over that copy, and decoded again - full HP, no status, the same Pokémon.
 */
class PartyHealTest {

    /** EWRAM + IWRAM as one writable copy, read and written the way the emu thread's [GameCalls] would. */
    private class Ram(fixture: String) : GameCalls {
        val ewram: ByteArray
        val iwram: ByteArray

        init {
            val r = FixtureMemoryReader.load(fixture)
            ewram = r.readCoreMemory(0x02000000L, 0x40000)
            iwram = r.readCoreMemory(0x03000000L, 0x8000)
        }

        private fun at(addr: Long): Pair<ByteArray, Int> =
            if (addr >= 0x03000000L) iwram to (addr - 0x03000000L).toInt() else ewram to (addr - 0x02000000L).toInt()

        override fun call(fn: Long, a0: Int, a1: Int, a2: Int, a3: Int) = -1L
        override fun read(addr: Long, len: Int): ByteArray = at(addr).let { (b, o) -> b.copyOfRange(o, o + len) }
        override fun write(addr: Long, bytes: ByteArray): Boolean = at(addr).let { (b, o) -> bytes.copyInto(b, o); true }
    }

    private fun party(ram: Ram, cfg: NativeConfig): List<Pair<Int, Mon>> {
        val count = ram.read(cfg.playerPartyCount, 1)[0].toInt() and 0xFF
        val raw = ram.read(cfg.playerParty, count * cfg.monStride)
        return (0 until count).mapNotNull { i -> decodePartyMon(raw, i * cfg.monStride, cfg.partyMonLayout)?.let { i to it } }
    }

    private fun check(fixture: String, cfg: NativeConfig) {
        val ram = Ram(fixture)
        val l = cfg.partyMonLayout
        val before = party(ram, cfg)
        assertTrue("$fixture has a party", before.isNotEmpty())
        // hurt and poison every one (status bit 3 = poison), straight into the plaintext party fields
        for ((i, _) in before) {
            val base = cfg.playerParty + i * cfg.monStride
            ram.write(base + l.hp, byteArrayOf(1, 0))
            ram.write(base + l.status, byteArrayOf(8, 0, 0, 0))
        }
        assertTrue(party(ram, cfg).all { (_, m) -> m.hp == 1 })
        val healed = PartyHeal.heal(cfg, ram)
        assertEquals(fixture, before.size, healed)
        val after = party(ram, cfg)
        assertEquals(before.size, after.size)
        for ((b, a) in before.zip(after)) {
            assertEquals(fixture, b.second.species, a.second.species)
            assertEquals(fixture, a.second.maxHp, a.second.hp)
            assertEquals(fixture, 0, a.second.status)
        }
    }

    @Test fun fireRed() = check("firered_vanilla", NATIVE_FIRERED_REV1)

    @Test fun emerald() = check("emerald_vanilla", NATIVE_EMERALD_RETAIL)

    @Test
    fun notInABattle() {
        val ram = Ram("emerald_vanilla")
        val cfg = NATIVE_EMERALD_RETAIL
        val b = ram.read(cfg.gMainInBattle, 1)
        ram.write(cfg.gMainInBattle, byteArrayOf((b[0].toInt() or 0x02).toByte()))
        assertEquals(-1, PartyHeal.heal(cfg, ram))
    }
}
