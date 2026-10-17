package com.pokedaisy.app.companion.data

import com.pokedaisy.app.companion.RrOverlay
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The RUN tab's decoded feed (RrOverlay.decodedState) over real RAM dumps of the games it serves that way: a party
 * comes out, and in a battle both sides' battlers. The JSON lands in build/decoded/ for checking the overlay page with.
 */
class RrOverlayDecodedTest {
    private fun dump(key: String, cfg: NativeConfig, kind: GameKind): org.json.JSONObject {
        activeGame = kind
        val view = buildSnapshotView(readNativeTelemetry(FixtureMemoryReader.load(key), cfg)).copy(game = kind)
        val json = RrOverlay.decodedState(view)
        File("build/decoded").mkdirs()
        File("build/decoded/$key.json").writeText(json.toString(1))
        return json
    }

    @Test fun imperiumField() {
        val j = dump("imperium", NATIVE_IMPERIUM, GameKind.IMPERIUM)
        assertTrue(j.getJSONArray("partyDec").length() > 0)
    }

    @Test fun imperiumBattle() {
        val j = dump("imperium_battle", NATIVE_IMPERIUM, GameKind.IMPERIUM)
        assertTrue(j.getInt("inBattle") == 1)
        assertTrue(!j.getJSONArray("battlersDec").isNull(1))
    }

    @Test fun soulGoldBattle() {
        val j = dump("soulgold_v12_battle", NATIVE_SOULGOLD_V1_2, GameKind.SOULGOLD)
        assertTrue(j.getJSONArray("partyDec").length() > 0)
    }
}
