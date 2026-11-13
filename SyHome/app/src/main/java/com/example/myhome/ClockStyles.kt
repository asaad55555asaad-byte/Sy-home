package com.example.myhome

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextClock
import android.widget.TextView

/** Visually distinct clock looks shared by home and standby screens. */
object ClockStyles {
    const val COUNT = 8

    fun names(ar: Boolean): List<String> = if (ar) listOf(
        "كلاسيكي", "دائري", "لوحة رقمية", "نيون", "كبسولة", "إطار ذهبي", "LED", "زجاج كبير"
    ) else listOf(
        "Classic", "Circle", "Digital panel", "Neon", "Capsule", "Gold frame", "LED", "Big glass"
    )

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun apply(ctx: Context, clock: TextView, style: Int, accent: Int, big: Boolean = false) {
        val s = style.coerceIn(0, COUNT - 1)
        val padH = if (big) dp(ctx, 28) else dp(ctx, 16)
        val padV = if (big) dp(ctx, 12) else dp(ctx, 6)

        // Full reset
        clock.setPadding(0, 0, 0, 0)
        clock.background = null
        clock.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        clock.setTextColor(Color.WHITE)
        clock.letterSpacing = 0f
        clock.typeface = Typeface.create("serif", Typeface.NORMAL)

        when (s) {
            0 -> { // Classic serif
                clock.typeface = Typeface.create("serif", Typeface.NORMAL)
                clock.setShadowLayer(4f, 0f, 2f, 0x99000000.toInt())
            }
            1 -> { // Circle badge
                clock.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                clock.setPadding(padH + dp(ctx, 8), padV + dp(ctx, 10), padH + dp(ctx, 8), padV + dp(ctx, 10))
                clock.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xCC101018.toInt())
                    setStroke(dp(ctx, 3), accent)
                }
                clock.setShadowLayer(12f, 0f, 4f, 0xAA000000.toInt())
            }
            2 -> { // Digital panel (monospace, dark rect, green)
                clock.typeface = Typeface.create("monospace", Typeface.BOLD)
                clock.letterSpacing = 0.12f
                clock.setTextColor(0xFF7CFFB2.toInt())
                clock.setPadding(padH, padV, padH, padV)
                clock.background = GradientDrawable().apply {
                    setColor(0xEE06120C.toInt())
                    cornerRadius = dp(ctx, 6).toFloat()
                    setStroke(dp(ctx, 2), 0xFF1AFF8C.toInt())
                }
                clock.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
            3 -> { // Neon outline glow
                clock.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                clock.letterSpacing = 0.08f
                clock.setTextColor(accent)
                clock.setPadding(padH, padV, padH, padV)
                clock.background = GradientDrawable().apply {
                    setColor(0x22000000)
                    cornerRadius = dp(ctx, 10).toFloat()
                    setStroke(dp(ctx, 2), accent)
                }
                clock.setShadowLayer(18f, 0f, 0f, accent)
            }
            4 -> { // Capsule / pill
                clock.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                clock.setPadding(padH + dp(ctx, 12), padV + dp(ctx, 2), padH + dp(ctx, 12), padV + dp(ctx, 2))
                clock.background = GradientDrawable().apply {
                    setColor(0xDD1A1220.toInt())
                    cornerRadius = dp(ctx, 48).toFloat()
                    setStroke(dp(ctx, 2), 0x66FFFFFF.toInt())
                }
                clock.setShadowLayer(8f, 0f, 3f, 0x88000000.toInt())
            }
            5 -> { // Gold frame
                clock.typeface = Typeface.create("serif", Typeface.BOLD)
                clock.setTextColor(0xFFFFF3D0.toInt())
                clock.setPadding(padH, padV, padH, padV)
                clock.background = GradientDrawable().apply {
                    setColor(0xCC1A1208.toInt())
                    cornerRadius = dp(ctx, 4).toFloat()
                    setStroke(dp(ctx, 3), 0xFFD4AF37.toInt())
                }
                clock.setShadowLayer(6f, 0f, 2f, 0xAA000000.toInt())
            }
            6 -> { // LED 7-segment vibe
                clock.typeface = Typeface.create("monospace", Typeface.BOLD)
                clock.letterSpacing = 0.2f
                clock.setTextColor(0xFFFF3B30.toInt())
                clock.setPadding(padH, padV, padH, padV)
                clock.background = GradientDrawable().apply {
                    setColor(0xFF0A0A0A.toInt())
                    cornerRadius = dp(ctx, 3).toFloat()
                    setStroke(dp(ctx, 1), 0xFF333333.toInt())
                }
                clock.setShadowLayer(10f, 0f, 0f, 0xFFFF3B30.toInt())
            }
            7 -> { // Big glass card
                clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                clock.setPadding(padH + dp(ctx, 6), padV + dp(ctx, 4), padH + dp(ctx, 6), padV + dp(ctx, 4))
                clock.background = GradientDrawable().apply {
                    setColor(0x55FFFFFF.toInt())
                    cornerRadius = dp(ctx, 16).toFloat()
                    setStroke(dp(ctx, 1), 0x99FFFFFF.toInt())
                }
                clock.setShadowLayer(10f, 0f, 4f, 0x66000000)
            }
        }
        clock.requestLayout()
        clock.invalidate()
    }

    fun applyTextClockFormats(clock: TextClock, prefs: android.content.SharedPreferences) {
        val base = if (prefs.getBoolean("clock24", true)) "HH:mm" else "h:mm a"
        val f = if (prefs.getBoolean("clockSeconds", false)) {
            if (prefs.getBoolean("clock24", true)) "HH:mm:ss" else "h:mm:ss a"
        } else base
        clock.format24Hour = f
        clock.format12Hour = f
    }
}
