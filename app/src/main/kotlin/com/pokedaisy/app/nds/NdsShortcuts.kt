package com.pokedaisy.app.nds

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The DS pause menu's BATTLE SHORTCUTS: in a wild battle R runs away and L throws the best ball in the bag, by
 * touching the game's own battle screen the way a player would (Gen 5's: positions read off Black 2's screens,
 * in DS pixels). [Taps] plays them on the emulation thread, a step a frame.
 */
object NdsShortcuts {

    /** One touch: the stylus down at ([x], [y]) for [hold] frames, then [wait] frames before the next. */
    class Tap(val x: Int, val y: Int, val hold: Int = 4, val wait: Int = 30)

    /** Taps queued by the UI thread, played by the emulation thread ([frame], once a frame). */
    class Taps {
        private val queue = ConcurrentLinkedQueue<Tap>()
        private var current: Tap? = null
        private var step = 0

        val busy get() = current != null || queue.isNotEmpty()

        fun play(taps: List<Tap>) {
            if (!busy) queue.addAll(taps)
        }

        /** Advances a frame: touches or lets go through [touch] (x, y, down). */
        fun frame(touch: (Int, Int, Boolean) -> Unit) {
            val t = current ?: queue.poll()?.also { current = it; step = 0 } ?: return
            when {
                step < t.hold -> touch(t.x, t.y, true)
                step == t.hold -> touch(t.x, t.y, false)
            }
            if (++step > t.hold + t.wait) current = null
        }
    }

    // Black 2's battle touch screen
    private val RUN = Tap(128, 181)
    // the bag slides in over ~1 s: a tap before it's up is lost (the next one then lands on the wrong pocket)
    // Platinum's first bag of a battle took over 75 frames to take touches (the POKé BALLS tap was lost and the
    // slot tap opened HP/PP RESTORE); later ones are quicker
    private val BAG = Tap(39, 168, wait = 150)
    // Platinum's pocket and pages slide in more slowly than Black 2's: a page turn tapped too soon is lost
    private val BALLS_POCKET = Tap(192, 47, wait = 90)
    private val NEXT_PAGE = Tap(59, 174, wait = 40)
    private val USE = Tap(107, 175)
    private val SLOT_X = intArrayOf(64, 192)
    private val SLOT_Y = intArrayOf(32, 80, 128)

    val run: List<Tap> = listOf(RUN)

    /**
     * Ball item ids, best first for a plain throw: Ultra, Great, Poké, then the ones that are at least a Poké Ball
     * everywhere (Premier, Luxury, Heal, Friend...), then the situational ones. Master (1) and Cherish (16) are never
     * thrown by a shortcut.
     */
    private val BALL_PRIORITY = intArrayOf(2, 3, 4, 12, 11, 14, 497, 15, 13, 10, 8, 9, 6, 7, 492, 493, 494, 495, 496, 498, 499, 576)
    private val BALLS = (BALL_PRIORITY + intArrayOf(1, 16, 5, 500)).toSet()

    /**
     * The taps that throw the best ball in the items pocket at [bag] (u16 item, u16 count pairs; Gen 5 keeps balls
     * there, and the battle bag's POKé BALLS page lists them in that order, six a page), or null with none to throw.
     */
    fun ball(ram: ByteArray, bag: Int, slots: Int = 310): List<Tap>? {
        val balls = balls(ram, bag, slots)
        val best = bestBall(ram, bag, slots) ?: return null
        val index = balls.indexOf(best)
        val slot = index % 6
        return buildList {
            add(BAG)
            add(BALLS_POCKET)
            repeat(index / 6) { add(NEXT_PAGE) }
            add(Tap(SLOT_X[slot % 2], SLOT_Y[slot / 2], wait = 45))
            add(USE)
        }
    }

    /** The best ball's item id in the items pocket at [bag] (what [ball] throws), or null. */
    fun bestBall(ram: ByteArray, bag: Int, slots: Int = 310): Int? = balls(ram, bag, slots).let { b -> BALL_PRIORITY.firstOrNull { it in b } }

    /**
     * The balls in the pocket at [bag] ([slots] slots of u16 item, u16 count; Gen 5's items pocket has 310, Gen 4's own
     * Poké Balls pocket 15), in bag order. Nothing below main RAM (a Gen 4 bag before its save block is known).
     */
    private fun balls(ram: ByteArray, bag: Int, slots: Int = 310): List<Int> {
        val off = bag - NdsMons.RAM_BASE
        val out = ArrayList<Int>()
        if (off < 0) return out
        for (i in 0 until slots) {
            val o = off + 4 * i
            if (o < 0 || o + 4 > ram.size) break
            val id = (ram[o].toInt() and 0xFF) or ((ram[o + 1].toInt() and 0xFF) shl 8)
            val n = (ram[o + 2].toInt() and 0xFF) or ((ram[o + 3].toInt() and 0xFF) shl 8)
            if (id in BALLS && n > 0) out += id
        }
        return out
    }
}
