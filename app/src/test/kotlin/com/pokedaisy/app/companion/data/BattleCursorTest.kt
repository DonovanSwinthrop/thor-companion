package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battle menus' cursors (NativeConfig.actionCursor / moveCursor) on a real capture: the German
 * Emerald battle (English Emerald's RAM) stopped at the action menu, FIGHT highlighted.
 */
class BattleCursorTest {
    @Test
    fun `the action cursor sits on FIGHT at the action menu`() {
        val ram = FixtureMemoryReader.load("emerald_de_battle")
        val cfg = NATIVE_EMERALD_DE
        assertEquals(0, ram.readCoreMemory(cfg.actionCursor, 1)[0].toInt())
        assertTrue((ram.readCoreMemory(cfg.moveCursor, 1)[0].toInt() and 0xFF) in 0..3)
    }

    @Test
    fun `only the configs checked headless have them`() {
        assertEquals(0x020244ACL to 0x020244B0L, NATIVE_EMERALD_RETAIL.actionCursor to NATIVE_EMERALD_RETAIL.moveCursor)
        assertEquals(0x02023FF8L to 0x02023FFCL, NATIVE_FIRERED_REV1.actionCursor to NATIVE_FIRERED_REV1.moveCursor)
        // Copies whose RAM differs (or isn't checked) are zeroed; the hacks' base configs never had them.
        listOf(NATIVE_EMERALD_JA, NATIVE_LEAFGREEN_REV1, NATIVE_EMERALD, NATIVE_FIRERED_REV0, NATIVE_UNBOUND).forEach {
            assertEquals(0L to 0L, it.actionCursor to it.moveCursor)
        }
    }
}
