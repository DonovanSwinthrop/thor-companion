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
}
