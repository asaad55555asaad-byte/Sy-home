package com.example.myhome

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** A rounded tile with one symbol in the middle (used for the Sy Home settings card). */
class GlyphDrawable(private val glyph: String, private val bg: Int, private val fg: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = b.height() * 0.22f
        p.color = bg
        canvas.drawRoundRect(RectF(b), r, r, p)
        p.color = fg
        p.textAlign = Paint.Align.CENTER
        p.textSize = b.height() * 0.62f
        val y = b.exactCenterY() - (p.descent() + p.ascent()) / 2
        canvas.drawText(glyph, b.exactCenterX(), y, p)
    }

    override fun setAlpha(alpha: Int) {
        p.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        p.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
