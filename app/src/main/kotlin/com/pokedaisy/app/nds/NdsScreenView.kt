package com.pokedaisy.app.nds

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

/**
 * One DS screen: its 256x192 picture at the largest size that keeps its shape, sharp pixels (no filtering),
 * black around it. [onTouch] set = a touch screen: taps and drags become stylus presses in DS pixels.
 */
@SuppressLint("ViewConstructor")
class NdsScreenView(context: Context, private val onTouch: ((x: Int, y: Int, down: Boolean) -> Unit)? = null) : View(context) {

    private val bitmap = Bitmap.createBitmap(NdsCore.WIDTH, NdsCore.HEIGHT, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = Rect()
    private val lock = Any()

    init {
        setBackgroundColor(Color.BLACK)
    }

    /** New pixels (any thread): drawn on the next frame. */
    fun show(pixels: IntArray) {
        synchronized(lock) { bitmap.setPixels(pixels, 0, NdsCore.WIDTH, 0, 0, NdsCore.WIDTH, NdsCore.HEIGHT) }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        val scale = minOf(w.toFloat() / NdsCore.WIDTH, h.toFloat() / NdsCore.HEIGHT)
        // whole multiples when they fill most of the room keep the pixels even
        val whole = scale.toInt().toFloat()
        val s = if (whole >= 1f && whole / scale > 0.9f) whole else scale
        val dw = (NdsCore.WIDTH * s).toInt()
        val dh = (NdsCore.HEIGHT * s).toInt()
        dst.set((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh)
        synchronized(lock) { canvas.drawBitmap(bitmap, null, dst, paint) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cb = onTouch ?: return false
        if (dst.isEmpty) return true
        val x = ((event.x - dst.left) * NdsCore.WIDTH / dst.width()).toInt()
        val y = ((event.y - dst.top) * NdsCore.HEIGHT / dst.height()).toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> cb(x, y, true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cb(x, y, false)
        }
        return true
    }
}
