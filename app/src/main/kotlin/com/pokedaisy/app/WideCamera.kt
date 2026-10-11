package com.pokedaisy.app

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * ZOOM and the look-around, over WIDE ([WideScreen]): which window of the scene the game view shows each frame.
 * pk_wide.c draws any window around the game's own 240x160 frame ([MgbaCore.pkWideSetView]); this picks it.
 *
 * - **Zoom steps** ([level]): 1x is the game as large as the view shows all of it - its full height on a wide screen,
 *   its full width on a tall one (a phone upright: the map then goes on above and below); each step out is a whole number of screen
 *   pixels per GBA pixel, so the pixel art stays crisp (the Thor's 1080 px: 6.75 -> 5 / 4 / 3, about 1.35x /
 *   1.7x / 2.25x the map). Only steps showing at least 1.2x as much, inside pk_wide.h's largest window.
 * - **Close up when it matters**: the game wanting it ([MgbaCore.pkWideWantsClose]: a battle, a menu screen, a
 *   text box or a transition while its controls are locked) eases back to 1x and the player; the chosen step
 *   comes back once that's been over for [RETURN_DELAY_NANOS].
 * - **Look-around** (the right stick, [stick]): standing, it moves the view, which stays where it's left; walking
 *   ([walking]) glides it back to the player - or, with the stick held, follows the stick but always keeps the
 *   player on screen.
 *
 * Inputs come from any thread; [frame] runs on the emu thread before each frame, its result is [current].
 */
class WideCamera {
    /** The game view's size, in screen pixels. */
    @Volatile var viewW = 0
    @Volatile var viewH = 0

    /** The zoom step the player picked: 0 = 1x, up to [levels]' last. */
    @Volatile var level = 0

    /** The right stick, -1..1 per axis (right / down positive). */
    @Volatile var stickX = 0f
    @Volatile var stickY = 0f

    /** A direction is held: the player is walking (or trying to). */
    @Volatile var walking = false

    /** What the screen shows: the window pk_wide drew ([x], [y], [w], [h] in the frame's GBA pixels), placed on the
     * view at ([offX], [offY]) screen pixels from its top-left (<= 0: the window's whole pixels reach past the
     * view by the fraction the look-around / zoom has moved), [scale] screen pixels per GBA pixel. */
    data class Window(val x: Int, val y: Int, val w: Int, val h: Int, val offX: Float, val offY: Float, val scale: Float)

    @Volatile var current: Window? = null
        private set

    private var scale = 0f
    private var lookX = 0f
    private var lookY = 0f
    private var lastNanos = 0L
    private var closeOverSince = 0L   // when the game last stopped wanting a close view (0 = it still does)

    /** Screen pixels per GBA pixel for each zoom step, 1x first. */
    fun levels(): FloatArray = levelsFor(viewW, viewH)

    /** How much more of the map step [i] shows than 1x, for the HUD ("1.7x"). */
    fun factor(i: Int): Float {
        val l = levels()
        return if (i in l.indices) l[0] / l[i] else 1f
    }

    /** The next step out, back to 1x after the last; returns the new step. */
    fun cycle(): Int {
        val n = levels().size
        level = if (n <= 1) 0 else (level + 1) % n
        return level
    }

    /** Back to 1x on the player at once (a new game, a state load, WIDE switched on). */
    fun reset() {
        scale = 0f
        lookX = 0f
        lookY = 0f
        lastNanos = 0L
    }

    /** Emu thread, before a frame: the window to draw ([MgbaCore.pkWideSetView]) given whether the last frame wanted
     * the game close up. Null without a view yet. */
    fun frame(closeWanted: Boolean, now: Long = System.nanoTime()): Window? {
        val vw = viewW
        val vh = viewH
        if (vw <= 0 || vh <= 0) return null.also { current = null }
        val steps = levelsFor(vw, vh)
        val fit = steps[0]
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now

        if (closeWanted) closeOverSince = 0L
        else if (closeOverSince == 0L) closeOverSince = now
        val close = closeOverSince == 0L || now - closeOverSince < RETURN_DELAY_NANOS
        val target = if (close) fit else steps[level.coerceIn(0, steps.size - 1)]
        // Zoom eases in log space (the same feel at every step), the look like a spring with no overshoot.
        scale = if (scale <= 0f || dt == 0f) target else scale * (target / scale).pow(ease(dt, ZOOM_TAU))
        if (abs(scale - target) < 0.002f) scale = target

        val fw = vw / scale
        val fh = vh / scale
        val sx = deadZone(stickX)
        val sy = deadZone(stickY)
        val stick = sx != 0f || sy != 0f
        when {
            close -> spring(0f, 0f, dt, LOOK_TAU)
            walking && stick -> {
                // Follow the stick, the player kept on screen: the view's centre at most this far off them.
                val maxX = max(0f, fw / 2 - PLAYER_KEEP)
                val maxY = max(0f, fh / 2 - PLAYER_KEEP)
                spring(sx * maxX, sy * maxY, dt, LOOK_TAU)
            }
            walking -> spring(0f, 0f, dt, RETURN_TAU)
            stick -> {
                val speed = LOOK_SPEED * max(1f, fit / scale)   // further out, the view travels faster
                lookX += sx * speed * dt
                lookY += sy * speed * dt
            }
        }
        // As far as a screen's worth of map past the game's own view each way.
        val limX = max(fw, PK_W.toFloat())
        val limY = max(fh, PK_H.toFloat())
        lookX = lookX.coerceIn(-limX, limX)
        lookY = lookY.coerceIn(-limY, limY)

        val fx = PK_W / 2f + lookX - fw / 2
        val fy = PK_H / 2f + lookY - fh / 2
        val x = floor(fx).toInt()
        val y = floor(fy).toInt()
        val w = min(MAX_W, ceil(fx + fw).toInt() - x)
        val h = min(MAX_H, ceil(fy + fh).toInt() - y)
        return Window(x, y, w, h, (x - fx) * scale, (y - fy) * scale, scale).also { current = it }
    }

    private fun spring(tx: Float, ty: Float, dt: Float, tau: Float) {
        val k = ease(dt, tau)
        lookX += (tx - lookX) * k
        lookY += (ty - lookY) * k
        if (hypot(lookX - tx, lookY - ty) < 0.05f) { lookX = tx; lookY = ty }
    }

    companion object {
        /** The game frame and pk_wide.h's largest window (PK_WIDE_MAX_VIEW_W / _H). */
        const val PK_W = 240
        const val PK_H = 160
        const val MAX_W = 800
        const val MAX_H = 800

        /** A step out must show at least this much more of the map than the one before it. */
        private const val MIN_STEP = 1.2f
        private const val ZOOM_TAU = 0.11f      // s: the zoom's ease (most of the way in ~0.3 s)
        private const val LOOK_TAU = 0.12f      // s: the view following the stick / going back for a close-up
        private const val RETURN_TAU = 0.18f    // s: the view gliding back to a player who starts walking
        private const val LOOK_SPEED = 150f     // GBA px per s at full stick, at 1x
        private const val PLAYER_KEEP = 28f     // GBA px: the player's sprite stays at least this far inside
        private const val DEAD_ZONE = 0.2f
        val RETURN_DELAY_NANOS = 500_000_000L   // the close view is over for this long before the zoom goes back out

        /** Screen pixels per GBA pixel for each step on a [w] x [h] view: 1x (the whole frame as large as it fits),
         * then the whole numbers below it that show at least [MIN_STEP] more each and fit pk_wide's largest window. */
        fun levelsFor(w: Int, h: Int): FloatArray {
            if (w <= 0 || h <= 0) return floatArrayOf(1f)
            val fit = min(w / PK_W.toFloat(), h / PK_H.toFloat())
            val out = arrayListOf(fit)
            var k = floor(fit).toInt()
            if (k.toFloat() == fit) k--
            while (k >= 1) {
                if (w / k.toFloat() > MAX_W || h / k.toFloat() > MAX_H) break
                if (out.last() / k >= MIN_STEP) out += k.toFloat()
                k--
            }
            return out.toFloatArray()
        }

        private fun ease(dt: Float, tau: Float) = 1f - exp(-dt / tau)

        private fun deadZone(v: Float): Float {
            val a = abs(v)
            if (a < DEAD_ZONE) return 0f
            return (a - DEAD_ZONE) / (1f - DEAD_ZONE) * if (v < 0) -1f else 1f
        }
    }
}
