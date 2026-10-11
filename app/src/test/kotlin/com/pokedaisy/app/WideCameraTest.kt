package com.pokedaisy.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WideCameraTest {
    private fun thor() = WideCamera().apply { viewW = 1920; viewH = 1080 }

    /** Runs [seconds] of frames at 60 Hz; returns the last window. */
    private fun WideCamera.run(seconds: Float, close: Boolean = false, start: Long = 1L): WideCamera.Window {
        var t = start
        var w: WideCamera.Window? = null
        repeat((seconds * 60).toInt()) {
            w = frame(close, t)
            t += 16_666_667L
        }
        return w!!
    }

    @Test fun stepsAreWholePixelsOnTheThor() {
        // 6.75 screen px per GBA px at 1x; 6 shows too little more (180 rows), 2 more than pk_wide's window.
        assertArrayEquals(floatArrayOf(6.75f, 5f, 4f, 3f), WideCamera.levelsFor(1920, 1080), 0.001f)
    }

    @Test fun aPhoneUprightGoesOnAboveAndBelow() {
        // 1080 wide, ~1400 for the game above the companion: 1x is the frame's full width (4.5), the map above / below.
        assertArrayEquals(floatArrayOf(4.5f, 3f, 2f), WideCamera.levelsFor(1080, 1400), 0.001f)
        val w = WideCamera().apply { viewW = 1080; viewH = 1400 }.run(0.1f)
        assertEquals(0, w.x)
        assertTrue(w.y < -70 && w.y + w.h > 230)
    }

    @Test fun oneXIsTheGameAtFullHeight() {
        val w = thor().run(0.1f)
        assertEquals(6.75f, w.scale, 0.001f)
        // The frame (0..240) sits in the middle of a window 1920 / 6.75 = 284.4 px wide.
        assertTrue(w.x <= -22 && w.x + w.w >= 262)
        assertEquals(0, w.y)
    }

    @Test fun zoomsOutAndBackInForACloseUp() {
        val cam = thor()
        cam.level = 3
        val out = cam.run(1.5f)   // a start counts as a close-up for half a second
        assertEquals(3f, out.scale, 0.001f)
        assertTrue(out.w in 640..642 && out.h in 360..362)
        val close = cam.run(1f, close = true, start = 2_000_000_000L)
        assertEquals(6.75f, close.scale, 0.001f)
        // Back out only once the close-up has been over for a moment.
        val back = cam.run(1.5f, start = 3_000_000_000L)
        assertEquals(3f, back.scale, 0.001f)
    }

    @Test fun lookStaysWhereItsLeftUntilThePlayerWalks() {
        val cam = thor()
        cam.run(1f)
        cam.stickX = 1f
        cam.run(0.5f, start = 1_100_000_000L)
        cam.stickX = 0f
        val moved = cam.run(0.5f, start = 1_700_000_000L)
        assertTrue("the view went right", moved.x > 20)
        cam.walking = true
        val back = cam.run(1.5f, start = 2_300_000_000L)
        assertTrue("and back to the player once walking", back.x in -24..-21)
    }

    @Test fun walkingWithTheStickKeepsThePlayerOnScreen() {
        val cam = thor()
        cam.walking = true
        cam.stickX = 1f
        val w = cam.run(2f)
        // The player (at the frame's middle, x 120) stays at least 28 px inside the window.
        assertTrue(w.x + w.w - 120 >= 28)
        assertTrue(w.x > 0)
    }
}
