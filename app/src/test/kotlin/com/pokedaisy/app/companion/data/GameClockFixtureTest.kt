package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GAME CLOCK reads the Crystal family's own time from HRAM: hHours 0xFF94, hMinutes 0xFF96, hSeconds 0xFF98 (pokecrystal's
 * hram.asm), apart from the cart clock's latched registers (hRTCHours 0xFF8F ...). Pinned on the fixtures, whose game
 * time was set to 10:xx when each new game started.
 */
class GameClockFixtureTest {
    private fun time(fixture: String): Triple<Int, Int, Int> {
        val r = FixtureMemoryReader.load(fixture)
        fun at(a: Long) = r.readCoreMemory(a, 1)[0].toInt() and 0xFF
        return Triple(at(0xFF94), at(0xFF96), at(0xFF98))
    }

    @Test fun crystal() = assertEquals(Triple(10, 11, 5), time("crystal"))

    @Test fun crystalAustralia() = assertEquals(Triple(10, 11, 47), time("crystal_au"))

    @Test fun crystalClear() = assertEquals(Triple(10, 35, 30), time("crystal_clear"))

    @Test fun prism() = assertEquals(Triple(10, 20, 32), time("prism"))
}
