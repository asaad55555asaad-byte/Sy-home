package com.example.myhome

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Runs in its own process so it can appear even when the main app crashes at startup. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "(no details)"
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(pad, pad * 2, pad, pad)
        col.setBackgroundColor(Color.BLACK)

        val title = TextView(this)
        title.text = "Sy Home crashed - send a screenshot of this screen"
        title.textSize = 18f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        col.addView(title)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val copy = Button(this)
        copy.text = "Copy"
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("trace", trace))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        val close = Button(this)
        close.text = "Close"
        close.setOnClickListener { finishAndRemoveTask() }
        row.addView(copy)
        row.addView(close)
        col.addView(row)

        val body = TextView(this)
        body.text = trace
        body.textSize = 11f
        body.typeface = Typeface.MONOSPACE
        body.setTextColor(0xFFFF8A80.toInt())
        body.setTextIsSelectable(true)
        col.addView(body)

        val sv = ScrollView(this)
        sv.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(sv)
    }
}
