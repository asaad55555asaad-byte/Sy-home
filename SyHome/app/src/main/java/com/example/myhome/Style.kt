package com.example.myhome

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import java.io.File

object Style {
    val DEFAULT_BG = 0xFF050104.toInt()
    val DEFAULT_ACCENT = 0xFFE8A33D.toInt()

    fun accent(p: SharedPreferences): Int = p.getInt("accent", DEFAULT_ACCENT)

    private fun dp(ctx: Context, v: Int): Float = v * ctx.resources.displayMetrics.density

    fun card(ctx: Context, accent: Int): StateListDrawable {
        val glass = ctx.getSharedPreferences("home", Context.MODE_PRIVATE).getBoolean("glass", true)
        val alpha = ctx.getSharedPreferences("home", Context.MODE_PRIVATE).getInt("glassAlpha", 78).coerceIn(20, 100)
        val normal = GradientDrawable().apply {
            setColor(if (glass) Color.argb(alpha * 255 / 100, 20, 12, 22) else 0xFF140C16.toInt())
            cornerRadius = dp(ctx, 12)
            if (glass) setStroke(dp(ctx, 1).toInt(), 0x55FFFFFF)
        }
        val focused = GradientDrawable().apply {
            setColor(0xF02A1F2E.toInt())
            cornerRadius = dp(ctx, 12)
            setStroke(dp(ctx, 3).toInt(), accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    // App / input tile: light glass at rest, white when focused (like the reference design)
    fun tile(ctx: Context, accent: Int): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(0x33FFFFFF)
            cornerRadius = dp(ctx, 18)
        }
        val focused = GradientDrawable().apply {
            setColor(0xF2FFFFFF.toInt())
            cornerRadius = dp(ctx, 18)
            setStroke(dp(ctx, 3).toInt(), accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    // Larger translucent panel with a light border (match card)
    fun panel(ctx: Context, accent: Int): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(0x66000000)
            cornerRadius = dp(22, ctx)
            setStroke(dp(2, ctx).toInt(), 0x66FFFFFF)
        }
        val focused = GradientDrawable().apply {
            setColor(0x88000000.toInt())
            cornerRadius = dp(22, ctx)
            setStroke(dp(3, ctx).toInt(), accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    private fun dp(v: Int, ctx: Context): Float = v * ctx.resources.displayMetrics.density

    fun next(ctx: Context, accent: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)))
        setStroke(dp(ctx, 2).toInt(), accent)
        cornerRadius = dp(ctx, 12)
    }

    fun swatch(ctx: Context, color: Int): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, 10)
            setStroke(dp(ctx, 1).toInt(), 0x66FFFFFF)
        }
        val focused = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, 10)
            setStroke(dp(ctx, 4).toInt(), Color.WHITE)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    fun imageFile(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    // ---------- Backgrounds ----------

    // Bundled photo wallpapers (real images, not solid colors).
    val NATURE = intArrayOf(
        R.drawable.bg_nature_1, R.drawable.bg_nature_2, R.drawable.bg_nature_3,
        R.drawable.bg_nature_4, R.drawable.bg_nature_5, R.drawable.bg_nature_6,
        R.drawable.bg_nature_7, R.drawable.bg_nature_8, R.drawable.bg_nature_9,
        R.drawable.bg_nature_10
    )
    /** No gradient-only slots; every preset is a photo. */
    private val GRADIENT_FROM = 99

    fun isPreset(p: SharedPreferences): Boolean = p.getString("bgMode", "preset") == "preset"

    fun isImageBackground(ctx: Context, p: SharedPreferences): Boolean =
        isPreset(p) || (p.getString("bgMode", "preset") == "image" && imageFile(ctx).exists())

    private var cacheKey = ""
    private var cacheBmp: Bitmap? = null

    // Crops the picture to the screen shape (center crop) so it is never stretched
    private fun cropToScreen(ctx: Context, bmp: Bitmap): Bitmap {
        val dm = ctx.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels).toFloat() / minOf(dm.widthPixels, dm.heightPixels)
        val src = bmp.width.toFloat() / bmp.height
        if (Math.abs(src - target) < 0.01f) return bmp
        val cw: Int
        val ch: Int
        if (src > target) {
            ch = bmp.height
            cw = (ch * target).toInt()
        } else {
            cw = bmp.width
            ch = (cw / target).toInt()
        }
        return Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
    }

    private fun loadBitmap(ctx: Context, p: SharedPreferences): Bitmap? {
        val dm = ctx.resources.displayMetrics
        val shape = "${dm.widthPixels}x${dm.heightPixels}"
        if (isPreset(p)) {
            val idx = p.getInt("bgPreset", 0).coerceIn(0, NATURE.size - 1)
            // Gradient XML drawables are not bitmaps — handled in background().
            if (idx >= GRADIENT_FROM) return null
            val key = "p$idx@$shape"
            if (key == cacheKey && cacheBmp != null) return cacheBmp
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            val raw = BitmapFactory.decodeResource(ctx.resources, NATURE[idx], opts) ?: return null
            val out = cropToScreen(ctx, raw)
            cacheKey = key
            cacheBmp = out
            return out
        }
        val f = imageFile(ctx)
        val key = "f${f.lastModified()}@$shape"
        if (key == cacheKey && cacheBmp != null) return cacheBmp
        val raw = BitmapFactory.decodeFile(f.path) ?: return null
        cacheKey = key
        cacheBmp = raw
        return raw
    }

    /** Advances the bundled nature gallery without touching user-imported backgrounds. */
    fun advanceNature(p: SharedPreferences) {
        val current = p.getInt("bgPreset", 0).coerceIn(0, NATURE.size - 1)
        val next = if (p.getBoolean("bgRandom", false)) {
            var candidate = (NATURE.indices).random()
            if (NATURE.size > 1) while (candidate == current) candidate = (NATURE.indices).random()
            candidate
        } else (current + 1) % NATURE.size
        p.edit().putString("bgMode", "preset").putInt("bgPreset", next).apply()
    }

    fun background(ctx: Context, p: SharedPreferences): Drawable {
        if (isPreset(p)) {
            val idx = p.getInt("bgPreset", 0).coerceIn(0, NATURE.size - 1)
            if (idx >= GRADIENT_FROM) {
                val base = try {
                    ctx.getDrawable(NATURE[idx])?.mutate()
                } catch (_: Throwable) { null }
                if (base != null) {
                    val dim = p.getInt("dim", 40)
                    val night = isNight(p)
                    val shadePct = if (night) maxOf(dim, p.getInt("nightDim", 65)) else dim
                    if (shadePct > 0) {
                        val shade = ColorDrawable(Color.argb(shadePct * 255 / 100, 0, 0, 0))
                        return LayerDrawable(arrayOf(base, shade))
                    }
                    return base
                }
            }
        }
        if (isImageBackground(ctx, p)) {
            val bmp = try {
                loadBitmap(ctx, p)
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) {
                val dim = p.getInt("dim", 40)
                val night = isNight(p)
                val shadePct = if (night) maxOf(dim, p.getInt("nightDim", 65)) else dim
                val shade = ColorDrawable(Color.argb(shadePct * 255 / 100, 0, 0, 0))
                return LayerDrawable(arrayOf(BitmapDrawable(ctx.resources, bmp), shade))
            }
        }
        val color = p.getInt("bgColor", DEFAULT_BG)
        return ColorDrawable(if (isNight(p)) darken(color, 0.72f) else color)
    }

    fun isNight(p: SharedPreferences): Boolean {
        if (p.getBoolean("nightManual", false)) return true
        if (!p.getBoolean("nightAuto", false)) return false
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
        val start = p.getString("nightStart", "22:00") ?: "22:00"
        val end = p.getString("nightEnd", "06:00") ?: "06:00"
        return if (start <= end) now >= start && now < end else now >= start || now < end
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(), (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt())
}
