package com.example.myhome

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.URL

object ImageLoader {
    private val cache = LruCache<String, Bitmap>(40)
    private val ui = Handler(Looper.getMainLooper())

    fun load(url: String?, view: ImageView) {
        view.tag = url
        if (url.isNullOrEmpty()) {
            view.setImageDrawable(null)
            return
        }
        val hit = cache.get(url)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        Thread {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = URL(url).openStream().use { BitmapFactory.decodeStream(it, null, opts) }
                if (bmp != null) {
                    cache.put(url, bmp)
                    ui.post { if (view.tag == url) view.setImageBitmap(bmp) }
                }
            } catch (e: Exception) {
            }
        }.start()
    }
}
