package com.pokedaisy.app

import com.pokedaisy.app.companion.Smoothing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** SHADERS > SMOOTHING's passes: every asset exists and comes out as GLSL ES 3.00 the device can compile. */
class SmoothingShadersTest {
    private fun asset(name: String) = File("src/main/assets/shaders/$name").readText()

    @Test fun everyPassHasItsAsset() {
        for (mode in Smoothing.entries) {
            val passes = ScreenShaders.smoothingPasses(mode) ?: continue
            for (p in passes) assertTrue("${p.asset} missing", File("src/main/assets/shaders/${p.asset}").isFile)
        }
        assertEquals(null, ScreenShaders.smoothingPasses(Smoothing.OFF))
        assertEquals(5, ScreenShaders.smoothingPasses(Smoothing.SCALEFX)!!.size)
    }

    @Test fun sourceIsEs300WithTheStageDefined() {
        for (mode in Smoothing.entries) {
            for (p in ScreenShaders.smoothingPasses(mode) ?: continue) {
                for (stage in listOf("VERTEX", "FRAGMENT")) {
                    val src = ScreenShaders.smoothingSource(asset(p.asset), stage)
                    assertTrue(src.startsWith("#version 300 es\n#define $stage\n"))
                    // ScaleFX's own desktop "#version 130" is gone: one #version, first.
                    assertEquals(1, Regex("#version").findAll(src).count())
                }
            }
        }
    }

    @Test fun xbrDeltaGlobalsAreConst() {
        val src = ScreenShaders.smoothingSource(asset("xbr-lv2.glsl"), "FRAGMENT")
        for (name in listOf("delta", "delta_l", "delta_u")) {
            assertTrue("$name not const", Regex("^const vec4 $name\\s*=", RegexOption.MULTILINE).containsMatchIn(src))
        }
        assertFalse(Regex("^vec4 delta", RegexOption.MULTILINE).containsMatchIn(src))
    }
}
