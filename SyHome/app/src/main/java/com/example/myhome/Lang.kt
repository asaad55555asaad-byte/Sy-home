package com.example.myhome

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object Lang {
    fun saved(base: Context): String =
        base.getSharedPreferences("home", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"

    fun isTv(ctx: Context): Boolean {
        val um = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return um?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    // Applies the chosen language ("auto" follows the device). On a phone the screen is
    // scaled so the TV layout (about 960 x 540 dp) fits, which makes it usable as a preview.
    fun wrap(base: Context): Context = try {
        wrapInner(base)
    } catch (e: Exception) {
        base
    }

    private fun wrapInner(base: Context): Context {
        val lang = saved(base)
        val cfg = Configuration(base.resources.configuration)
        var changed = false
        if (lang != "auto") {
            val loc = Locale(lang)
            Locale.setDefault(loc)
            cfg.setLocale(loc)
            cfg.setLayoutDirection(loc)
            changed = true
        }
        if (!isTv(base)) {
            val dm = base.resources.displayMetrics
            val w = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
            val h = minOf(dm.widthPixels, dm.heightPixels).toFloat()
            val target = (minOf(w / 960f, h / 540f) * 160f).toInt()
            if (target in 60 until cfg.densityDpi) {
                cfg.densityDpi = target
                changed = true
            }
        }
        return if (changed) base.createConfigurationContext(cfg) else base
    }
}
