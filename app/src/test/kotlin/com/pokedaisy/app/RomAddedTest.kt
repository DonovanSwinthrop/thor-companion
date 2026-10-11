package com.pokedaisy.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The library's DATE ADDED and NEW tag ([RomAdded]). */
class RomAddedTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Before fun use() { RomAdded.dirOverride = tmp.root; RomAdded.reload() }
    @After fun done() { RomAdded.dirOverride = null; RomAdded.reload() }

    private val hour = 3_600_000L
    private fun rom(name: String) = File(tmp.root, name)

    /** The first time the store is kept, the library already there isn't new (an app update). */
    @Test fun aLibraryFromBeforeIsNotNew() {
        RomAdded.record(null, listOf(rom("a.gba"), rom("b.gba")), initial = false, now = 1000)
        assertFalse(RomAdded.isNew(null, rom("a.gba"), now = 2000))
        assertEquals(1000L, RomAdded.addedAt(null, rom("b.gba")))
    }

    @Test fun aGameAddedLaterIsNewForADay() {
        RomAdded.record(null, listOf(rom("a.gba")), initial = false, now = 0)
        RomAdded.record(null, listOf(rom("a.gba"), rom("c.gba")), initial = false, now = 10 * hour)
        assertTrue(RomAdded.isNew(null, rom("c.gba"), now = 11 * hour))
        assertTrue(RomAdded.isNew(null, rom("c.gba"), now = 33 * hour))
        assertFalse(RomAdded.isNew(null, rom("c.gba"), now = 35 * hour))
        // Seen again later: keeps its date.
        RomAdded.record(null, listOf(rom("c.gba")), initial = false, now = 20 * hour)
        assertEquals(10 * hour, RomAdded.addedAt(null, rom("c.gba")))
    }

    /** First-time setup (or a folder just linked) brings games in dated, not NEW. */
    @Test fun setupGamesAreNotNew() {
        RomAdded.record(null, emptyList(), initial = false, now = 0)
        RomAdded.record(null, listOf(rom("d.gba")), initial = true, now = hour)
        assertFalse(RomAdded.isNew(null, rom("d.gba"), now = 2 * hour))
        assertEquals(hour, RomAdded.addedAt(null, rom("d.gba")))
    }

    @Test fun keptAcrossLaunches() {
        RomAdded.record(null, emptyList(), initial = false, now = 0)
        RomAdded.record(null, listOf(rom("e.gba")), initial = false, now = hour)
        RomAdded.reload()
        assertTrue(RomAdded.isNew(null, rom("e.gba"), now = 2 * hour))
    }

    /** Deleted and added again: new again. */
    @Test fun aDeletedGameAddedAgainIsNew() {
        RomAdded.record(null, emptyList(), initial = false, now = 0)
        RomAdded.record(null, listOf(rom("f.gba")), initial = true, now = hour)
        RomAdded.forget(null, rom("f.gba"))
        RomAdded.record(null, listOf(rom("f.gba")), initial = false, now = 2 * hour)
        assertTrue(RomAdded.isNew(null, rom("f.gba"), now = 3 * hour))
    }
}
