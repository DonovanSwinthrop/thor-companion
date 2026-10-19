package com.pokedaisy.app.companion.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.pokedaisy.app.companion.data.DecompIconSource
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV0
import com.pokedaisy.app.companion.data.NATIVE_FIRERED_REV1
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.RomFileReader
import com.pokedaisy.app.companion.data.activeGame
import org.junit.Before
import org.junit.Test

/**
 * The WILD tab on the Thor's bottom screen (1240x1080): this map's wild Pokémon with and without a working REPEL. Its list
 * is given ([CompanionScreen]'s previewWild), so it renders without a ROM; icons and the party backdrop come from the
 * retail FireRed ROM when it's on this machine (rev 1, or rev 0 with its own icon tables).
 *
 *     ./gradlew :app:recordPaparazziDebug --tests '*WildScreenshotTest'
 */
class WildScreenshotTest {
    @get:org.junit.Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1240, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE),
        theme = "android:Theme.Material.NoActionBar",
    )

    @Before
    fun game() {
        activeGame = GameKind.FIRERED
        OptionColors.inGame = true
        val art = java.io.File("build/rom-art-paparazzi").also { RomArt.dirOverride = it }
        val file = java.io.File(RomFileReader.FIRERED_REV1_PATH).takeIf { it.isFile } ?: return
        val bytes = file.readBytes()
        RomArt.extractTo(bytes, art)
        RomFileReader.load(file.path)?.let { rom ->
            DecompIconSource.reader = rom
            // The header's revision byte (0xBC) picks the icon tables.
            DecompIconSource.tables = (if (bytes[0xBC].toInt() == 1) NATIVE_FIRERED_REV1 else NATIVE_FIRERED_REV0).iconTables
            (WILD.map { it.species } + SampleCompanion.snapshot.party.map { it.species }).forEach { DecompIconSource.get(it) }
            DecompIconSource.getItem(86) // the REPEL row's icon
        }
    }

    private fun shot(repel: Int = 0) = paparazzi.snapshot {
        val snap = if (repel > 0) SampleCompanion.snapshot.copy(progress = repelProgress(repel)) else SampleCompanion.snapshot
        CompanionScreen(snap, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "WILD", previewWild = WILD, itemUse = RepelItemUse)
    }

    @Test fun wild() = shot()

    @Test fun wildRepel() = shot(repel = 87)

    /** The default bar (WILD on it, GUIDE under SETTINGS > TOOLS), the endless spray switch off. */
    @Test fun wildOnBar() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SprayGame(100), initialTab = "WILD", previewWild = WILD, itemUse = RepelItemUse)
    }

    /** The endless spray on (WILD ENCOUNTERS OFF), the game's own spray still counting down. */
    @Test fun wildSprayOn() = paparazzi.snapshot {
        CompanionScreen(
            SampleCompanion.snapshot.copy(progress = repelProgress(87)), SampleCompanion.Slots, SprayGame(0),
            initialTab = "WILD", previewWild = WILD, itemUse = RepelItemUse,
        )
    }

    /** A game where WILD ENCOUNTERS works, at [percent]. */
    private class SprayGame(private var percent: Int) : com.pokedaisy.app.companion.CompanionSettings by SampleCompanion.Settings() {
        override val wildEncountersSupported get() = true
        override val wildEncounters get() = percent
        override fun setWildEncounters(percent: Int) { this.percent = percent }
    }

    @Test fun party() = paparazzi.snapshot {
        CompanionScreen(SampleCompanion.snapshot, SampleCompanion.Slots, SampleCompanion.Settings(), initialTab = "PARTY", previewWild = WILD)
    }

    private companion object {
        val WILD = listOf(
            HudWild("GRASS / CAVE", 16, 2, 5, 50, seen = true, caught = true),
            HudWild("GRASS / CAVE", 19, 2, 4, 45, seen = true, caught = false),
            HudWild("GRASS / CAVE", 21, 3, 5, 5, seen = false, caught = false),
            HudWild("SURFING", 72, 5, 20, 100, seen = true, caught = false),
            HudWild("OLD ROD", 129, 5, 5, 100, seen = true, caught = true),
        )
    }
}

/** An [com.pokedaisy.app.companion.ItemUse] whose game counts REPEL steps in FireRed's VAR_REPEL_STEP_COUNT. */
private object RepelItemUse : com.pokedaisy.app.companion.ItemUse {
    override fun canUse(itemId: Int) = false
    override fun use(itemId: Int, done: (com.pokedaisy.app.companion.data.ItemUseOutcome) -> Unit) = Unit
    override val repelVar = 0x4020
}
