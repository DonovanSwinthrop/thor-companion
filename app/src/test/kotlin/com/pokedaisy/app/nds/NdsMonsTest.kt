package com.pokedaisy.app.nds

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [NdsMons] against Black 2 (US) RAM dumps. They hold the game's own code, so they're never committed: kept in
 * `~/tools/ndsdump/` (or `-DndsDumps=<dir>`), taken with the DS player's debug dump (`files/nds-dump`). Skipped
 * without them.
 */
class NdsMonsTest {

    private fun dump(name: String): ByteArray? {
        val dir = System.getProperty("ndsDumps") ?: (System.getProperty("user.home") + "/tools/ndsdump")
        return File(dir, name).takeIf { it.isFile }?.readBytes()
    }

    @Test
    fun partyAfterTheStarter() {
        val ram = dump("b2_tepig.bin")
        assumeTrue("no Black 2 dump", ram != null)
        val party = NdsMons.party(ram!!, 0x0221E3EC)
        assertEquals(1, party.size)
        val tepig = party[0]
        assertEquals(498, tepig.species)
        assertEquals(5, tepig.level)
        assertEquals(21, tepig.hp)
        assertEquals(21, tepig.maxHp)
        assertEquals(listOf(33, 39, 0, 0), tepig.moves.toList())   // Tackle, Tail Whip
        assertEquals(66, tepig.ability)                              // Blaze
    }

    @Test
    fun rivalBattleBattlers() {
        val ram = dump("b2_battle_hit.bin")
        assumeTrue("no Black 2 battle dump", ram != null)
        val b = NdsMons.battlers(ram!!)
        assertEquals(2, b.size)
        assertEquals(498, b[0].species)
        assertEquals(9, b[0].hp)
        assertEquals(21, b[0].maxHp)
        assertEquals(501, b[1].species)
        assertEquals(2, b[1].hp)
        assertEquals(67, b[1].ability)                               // Torrent
        assertEquals(listOf(33, 39, 0, 0), b[1].mon!!.moves.toList())
    }

    @Test
    fun theOneOnTheFieldComesFirst() {
        for ((name, lead) in listOf("b2_wild2.bin" to 498, "b2_switched.bin" to 504)) {
            val ram = dump(name)
            assumeTrue("no $name", ram != null)
            val party = NdsMons.party(ram!!, 0x0221E3EC)
            val sides = NdsMons.sides(ram, NdsMons.battlers(ram), party.map { it.pid }.toSet())
            assertEquals(name, 2, sides.player.size)
            assertEquals(name, lead, sides.player[0].species)          // Tepig, then the Patrat switched in
            assertEquals(name, 504, sides.foe.single().species)        // the wild Patrat
            assertEquals(name, 124, party[1].metLocation)               // caught on Route 19
            assertEquals(name, 117, party[0].metLocation)               // the starter: Aspertia City
        }
    }

    /** A retail ROM from [romDir] (`-DndsRoms=<dir>`, else the user's ROM folder), or null. */
    private fun rom(name: String): NdsRom? {
        val dir = System.getProperty("ndsRoms") ?: "E:/Thor_AYN_Roms/DS"
        return File(dir, name).takeIf { it.isFile }?.let(::NdsRom)
    }

    @Test
    fun movePpFromTheRom() {
        val b2 = rom("Pokémon Black Version 2.nds")
        assumeTrue("no Black 2 ROM", b2 != null)
        val pp = b2!!.movePp()!!
        assertEquals(35, pp[33])                                        // Tackle
        assertEquals(30, pp[39])                                        // Tail Whip
        assertEquals(5, pp[63])                                         // Hyper Beam
        val pt = rom("Pokémon Platinum Version.nds")
        if (pt != null) {
            val ppt = pt.movePp()!!
            assertEquals(35, ppt[33])
            assertEquals(5, ppt[63])
        }
    }

    @Test
    fun healPartyFillsPpAndFixesTheChecksum() {
        val ram = dump("b2_wild2.bin")
        assumeTrue("no b2_wild2.bin", ram != null)
        val base = IntArray(700) { 35 }.also { it[39] = 30 }            // Tackle 35, Tail Whip 30, the rest 35
        val copy = ram!!.copyOf()
        val before = NdsMons.party(copy, 0x0221E3EC)
        NdsMons.healParty(copy, 0x0221E3EC, base) { addr, bytes ->
            System.arraycopy(bytes, 0, copy, addr - NdsMons.RAM_BASE, bytes.size); true
        }
        val after = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(before.size, after.size)                           // still decodes: the new checksum holds
        for ((b, a) in before.zip(after)) {
            assertEquals(b.species, a.species)
            assertEquals(b.moves.toList(), a.moves.toList())
            assertEquals(b.ivs.toList(), a.ivs.toList())
            for (k in 0 until 4) if (a.moves[k] != 0) assertEquals(base[a.moves[k]], a.pp[k])
            assertEquals(a.maxHp, a.hp)
        }
    }

    @Test
    fun healPartyFillsHpAndKeepsTheMonValid() {
        val ram = dump("b2_wild2.bin")
        assumeTrue("no b2_wild2.bin", ram != null)
        val copy = ram!!.copyOf()
        val before = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(6, before[1].hp)                                   // the caught Patrat, hurt
        val healed = NdsMons.healParty(copy, 0x0221E3EC) { addr, bytes ->
            System.arraycopy(bytes, 0, copy, addr - NdsMons.RAM_BASE, bytes.size); true
        }
        assertEquals(2, healed)
        val after = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(2, after.size)                                     // still decodes: the checksum block is untouched
        for ((b, a) in before.zip(after)) {
            assertEquals(b.species, a.species)
            assertEquals(a.maxHp, a.hp)
            assertEquals(0, a.status)
            assertEquals(b.level, a.level)
            assertEquals(b.maxHp, a.maxHp)
        }
    }
}
