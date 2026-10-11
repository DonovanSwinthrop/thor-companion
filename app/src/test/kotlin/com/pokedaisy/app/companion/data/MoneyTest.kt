package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Money (the top-screen status bar), per fixture save. */
class MoneyTest {
    private fun money(key: String, cfg: NativeConfig) = readMoney(FixtureMemoryReader.load(key), cfg)

    @Test
    fun `retail saves`() {
        assertEquals(224300L, money("firered_vanilla", NATIVE_FIRERED_REV1))
        assertEquals(6098L, money("leafgreen_rev1", NATIVE_LEAFGREEN_REV1))
        assertEquals(185092L, money("emerald_vanilla", NATIVE_EMERALD_RETAIL))
        // Ruby / Sapphire don't XOR it.
        assertEquals(4961L, money("ruby_rev2", NATIVE_RUBY))
        assertEquals(398179L, money("sapphire_rev2", NATIVE_SAPPHIRE))
    }

    @Test
    fun `hacks keep their base game's offset`() {
        assertEquals(3848L, money("unbound", NATIVE_UNBOUND))
        assertEquals(2999L, money("celia", NATIVE_CELIA))
        // Heart and Soul's SaveBlock1 has 4 extra bytes up front.
        assertEquals(3000L, money("heart_and_soul", NATIVE_HEART_AND_SOUL))
        assertEquals(3000L, money("lazarus", NATIVE_LAZARUS))
        // Rogue's SaveBlock1 grew (104-byte party mons): money at +0x4A8; this save has none.
        assertEquals(0L, money("emerald_rogue", NATIVE_EMERALD_ROGUE))
        assertEquals(3000L, money("glazed", NATIVE_GLAZED))
        assertEquals(3000L, money("imperium", NATIVE_IMPERIUM))
        // Quetzal's SaveBlock1 is its own: money at +0x918, the key at SB2+0x2C.
        assertEquals(7450L, money("quetzal", NATIVE_QUETZAL))
        assertEquals(48937L, money("quetzal_johto", NATIVE_QUETZAL))
        // Quetzal's money goes past 999,999 (its START menu shows 1048458).
        assertEquals(1_048_458L, money("quetzal_es", NATIVE_QUETZAL_ES))
        assertEquals(1748L, money("unbound_fr", NATIVE_UNBOUND_FR))
        assertEquals(2000L, money("orange_islands", NATIVE_ORANGE_ISLANDS))
        assertEquals(3080L, money("ultra_violet", NATIVE_ULTRA_VIOLET))
        assertEquals(5000L, money("sword_shield_up", NATIVE_SWORD_SHIELD))
        // Recharged Yellow keeps no key: SaveBlock1+0x29C plain, every release (its trainer card says P4804).
        assertEquals(4804L, money("recharged_yellow_v180", NATIVE_RECHARGED_YELLOW_V1_8_0))
        assertEquals(4804L, money("recharged_yellow", NATIVE_RECHARGED_YELLOW_V1_9_7))
        // Super Mariomon: vanilla's offsets in every release (its card says P3100).
        for ((key, cfg) in listOf("mariomon_v1_5_2" to NATIVE_MARIOMON_V1_5_2, "mariomon_v1_5_1" to NATIVE_MARIOMON_V1_5_1,
                "mariomon_v1_3" to NATIVE_MARIOMON_V1_3, "mariomon_v1_2_2" to NATIVE_MARIOMON_V1_2_2, "mariomon_v1_2_1" to NATIVE_MARIOMON_V1_2_1)) {
            assertEquals(key, 3100L, money(key, cfg))
        }
    }

    @Test
    fun `QoL ROMs - found through the struct's map`() {
        val reader = FixtureMemoryReader.load("emerald_qol")
        val t = decodeTelemetry(reader.readCoreMemory(reader.findMagic("QOLT".toByteArray(Charsets.US_ASCII)), TELEMETRY_SIZE))
        assertEquals(185092L, findStructMoney(reader, GameKind.EMERALD, t.mapGroup, t.mapNum)?.second)
        // Another map: no candidate matches, so no money rather than a guess.
        assertNull(findStructMoney(reader, GameKind.EMERALD, t.mapGroup, t.mapNum + 1))
    }

    @Test
    fun `FireRed QoL - its own save block pointers, not retail's`() {
        // This capture's struct is a refresh behind its save (map 3.56 vs the
        // save's 3.16 - the struct refreshes about once a second), so match
        // the save's map, as the app does a refresh later.
        val reader = FixtureMemoryReader.load("firered_qol")
        val (cfg, money) = findStructMoney(reader, GameKind.FIRERED, 3, 16)!!
        assertEquals(224300L, money)
        assertEquals(0x03005018L, cfg.saveBlock1Ptr)
        assertNull(findStructMoney(reader, GameKind.FIRERED, 3, 56))
    }
}
