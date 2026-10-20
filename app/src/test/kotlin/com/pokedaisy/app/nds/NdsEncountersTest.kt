package com.pokedaisy.app.nds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [NdsEncounters.block] / [NdsEncounters.restore] on RAM copies. Needs the ROM and a RAM dump (never committed:
 * `~/tools/ndsdump/`, ROMs in `E:/Thor_AYN_Roms/DS`); skipped without them.
 */
class NdsEncountersTest {

    private fun dump(name: String): ByteArray? {
        val dir = System.getProperty("ndsDumps") ?: (System.getProperty("user.home") + "/tools/ndsdump")
        return File(dir, name).takeIf { it.isFile }?.readBytes()
    }

    private fun romFile(name: String): File? {
        val dir = System.getProperty("ndsRoms") ?: "E:/Thor_AYN_Roms/DS"
        return File(dir, name).takeIf { it.isFile }
    }

    private class Case(val enc: NdsEncounters, val original: ByteArray, val zone: Int) {
        val copy = original.copyOf()
        val write = { a: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, a - NdsMons.RAM_BASE, bytes.size); true }
        fun changed() = original.indices.filter { original[it] != copy[it] }
    }

    /** Black 2, Route 19 (map 437): rates at 0x0223B7C0. */
    private fun black2(): Case? {
        val b2 = romFile("Pokémon Black Version 2.nds")
        val ram = dump("b2_wild2.bin")
        assumeTrue("no Black 2 ROM / dump", b2 != null && ram != null)
        val game = NdsFeed.gameFor(b2!!)!!
        return Case(NdsEncounters(NdsRom(b2), game), ram!!, NdsFeed.zone(ram, game))
    }

    /** Platinum, map 356: grass rate at 0x022A15A8. */
    private fun platinum(): Case? {
        val pt = romFile("Pokémon Platinum Version.nds")
        val ram = dump("pt_rep2.bin")
        assumeTrue("no Platinum ROM / dump", pt != null && ram != null)
        val game = NdsFeed.gameFor(pt!!)!!
        return Case(NdsEncounters(NdsRom(pt), game), ram!!, NdsFeed.zone(ram, game))
    }

    @Test
    fun black2BlockThenRestoreIsByteForByte() {
        val c = black2()!!
        fun rates() = (0 until 5).map { c.copy[0x23B7C0 + it].toInt() and 0xFF }
        assertEquals(437, c.zone)
        val before = rates()
        assertTrue(before.any { it != 0 })
        assertTrue(c.enc.block(c.copy, c.zone, c.write))
        assertEquals(listOf(0, 0, 0, 0, 0), rates())
        assertTrue(c.changed().all { it in 0x23B7C0 until 0x23B7C5 })
        c.enc.restore(c.copy, c.zone, c.write)
        assertEquals(before, rates())
        assertEquals(emptyList<Int>(), c.changed())
    }

    @Test
    fun black2RestoreWithoutBlockChangesNothing() {
        val c = black2()!!
        c.enc.restore(c.copy, c.zone, c.write)
        assertEquals(emptyList<Int>(), c.changed())
    }

    @Test
    fun platinumBlockThenRestoreIsByteForByte() {
        val c = platinum()!!
        assertEquals(356, c.zone)
        assertEquals(30, c.copy[0x2A15A8].toInt() and 0xFF)
        assertTrue(c.enc.block(c.copy, c.zone, c.write))
        assertEquals(0, c.copy[0x2A15A8].toInt() and 0xFF)
        c.enc.restore(c.copy, c.zone, c.write)
        assertEquals(emptyList<Int>(), c.changed())
    }

    @Test
    fun platinumRestoreWithoutBlockChangesNothing() {
        val c = platinum()!!
        c.enc.restore(c.copy, c.zone, c.write)
        assertEquals(emptyList<Int>(), c.changed())
    }
}
