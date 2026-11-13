package com.example.syinstaller

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private val WHITE = Color.WHITE
private val PINK = 0xFFF4A6C8.toInt()
private val MUTED = 0xFFCDB0C4.toInt()
private const val TV_PKG = "com.example.myhome"
private const val TV_ACTIVITY = "com.example.myhome.MainActivity"

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var state = "home"
    private var scanGen = 0
    private var logView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var doneBtn: TextView? = null
    private val reqApk = 21

    // Arabic by default; the language link at the bottom toggles it
    override fun attachBaseContext(base: Context) {
        val lang = base.getSharedPreferences("inst", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
        val loc = Locale(lang)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("inst", MODE_PRIVATE)
        showHome()
    }

    override fun onBackPressed() {
        if (state != "home") {
            scanGen++
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    // ---------- UI helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun lp(top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun text(s: CharSequence, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun screen(content: LinearLayout): View {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF4A0A2E.toInt(), 0xFF2A0620.toInt(), 0xFF100208.toInt())
        )
        content.setPadding(dp(24), dp(56), dp(24), dp(32))
        sv.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return sv
    }

    private fun pill(label: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.textSize = 20f
        t.setTextColor(WHITE)
        t.typeface = Typeface.DEFAULT_BOLD
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(20), 0, dp(20))
        t.background = if (primary) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF9A1250.toInt(), 0xFF6B0B38.toInt())
            ).apply { cornerRadius = dp(100).toFloat() }
        } else {
            rounded(0xFF4A2A44.toInt(), 100)
        }
        t.isClickable = true
        t.isFocusable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun chip(label: String, dot: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(14), dp(10), dp(14), dp(10))
        c.background = rounded(0xFF3A1535.toInt(), 40)
        val d = View(this)
        d.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dot)
        }
        c.addView(d, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
        c.addView(text(label, 14f, WHITE))
        return c
    }

    private fun hintRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isClickable = true
        row.setPadding(0, dp(12), 0, dp(12))
        val d = View(this)
        d.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFC857.toInt())
        }
        row.addView(d, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) })
        row.addView(text(getString(R.string.hint_first), 17f, PINK, true))
        row.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.dev_title)
                .setMessage(R.string.dev_steps)
                .setPositiveButton(R.string.ok, null).show()
        }
        return row
    }

    // ---------- Screens ----------

    private fun showHome() {
        state = "home"
        val c = col()
        c.addView(text(getString(R.string.title), 40f, WHITE, true), lp(top = 72))
        c.addView(text(getString(R.string.subtitle), 18f, PINK, true), lp(top = 28))
        c.addView(text(getString(R.string.desc), 17f, MUTED), lp(top = 20))

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val m = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        m.marginEnd = dp(8)
        chips.addView(chip(getString(R.string.chip_wifi), 0xFF2ECC71.toInt()), m)
        chips.addView(chip(getString(R.string.chip_nopc), 0xFFF4A6C8.toInt()), LinearLayout.LayoutParams(m))
        chips.addView(chip(getString(R.string.chip_free), 0xFFFFC857.toInt()), LinearLayout.LayoutParams(m))
        c.addView(chips, lp(top = 24))

        c.addView(pill(getString(R.string.btn_install), true) { showScan(false) }, lp(top = 40))
        c.addView(pill(getString(R.string.btn_restore), false) { showScan(true) }, lp(top = 14))
        c.addView(hintRow(), lp(top = 36))

        val isAr = resources.configuration.locales[0].language == "ar"
        val lang = text(getString(R.string.lang_toggle), 17f, 0xFF6EA8FF.toInt())
        lang.gravity = Gravity.CENTER
        lang.setPadding(0, dp(16), 0, dp(16))
        lang.setOnClickListener {
            prefs.edit().putString("lang", if (isAr) "en" else "ar").apply()
            recreate()
        }
        c.addView(lang, lp(top = 16))
        setContentView(screen(c))
    }

    private fun showScan(restore: Boolean) {
        state = "scan"
        val c = col()
        c.addView(text(getString(if (restore) R.string.btn_restore else R.string.btn_install), 34f, WHITE, true))

        val statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        val pb = ProgressBar(this)
        pb.isIndeterminate = true
        val status = text(getString(R.string.scanning), 16f, MUTED)
        statusRow.addView(pb, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        statusRow.addView(status)
        c.addView(statusRow, lp(top = 28))

        val list = col()
        c.addView(list, lp(top = 8))

        val card = col()
        card.background = rounded(0xFF1A0B14.toInt(), 24)
        card.setPadding(dp(20), dp(20), dp(20), dp(20))
        card.addView(text(getString(R.string.or_enter), 16f, MUTED))
        val input = EditText(this)
        input.hint = getString(R.string.ip_hint)
        input.setHintTextColor(0xFF8A7080.toInt())
        input.setTextColor(WHITE)
        input.textSize = 18f
        input.setSingleLine()
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.keyListener = DigitsKeyListener.getInstance("0123456789.:")
        input.textDirection = View.TEXT_DIRECTION_LTR
        input.background = rounded(0xFF2A1020.toInt(), 18)
        input.setPadding(dp(18), dp(16), dp(18), dp(16))
        card.addView(input, lp(top = 12))
        card.addView(pill(getString(R.string.connect), true) {
            val s = input.text.toString().trim()
            if (Regex("""^\d{1,3}(\.\d{1,3}){3}(:\d{1,5})?$""").matches(s)) {
                runJob(s, restore)
            } else {
                Toast.makeText(this, R.string.bad_ip, Toast.LENGTH_SHORT).show()
            }
        }, lp(top = 12))
        c.addView(card, lp(top = 24))
        c.addView(hintRow(), lp(top = 24))
        setContentView(screen(c))

        startScan(list, status, pb, restore)
    }

    private fun showProgress(restore: Boolean) {
        state = "progress"
        val c = col()
        c.addView(text(getString(if (restore) R.string.progress_restore else R.string.progress_install), 34f, WHITE, true))
        val pb = ProgressBar(this)
        pb.isIndeterminate = true
        progressBar = pb
        c.addView(pb, LinearLayout.LayoutParams(dp(36), dp(36)).apply { topMargin = dp(24) })
        val log = text("", 16f, 0xFFF2DCE8.toInt())
        log.setLineSpacing(dp(4).toFloat(), 1f)
        logView = log
        c.addView(log, lp(top = 20))
        val done = pill(getString(R.string.done_btn), true) { showHome() }
        done.visibility = View.GONE
        doneBtn = done
        c.addView(done, lp(top = 32))
        setContentView(screen(c))
    }

    // ---------- Scanning ----------

    private fun probe(prefix: String, own: String, i: Int, gen: Int, found: MutableList<String>,
                      onFound: (String) -> Unit) {
        val ip = prefix + i
        if (ip == own) return
        try {
            Socket().use { it.connect(InetSocketAddress(ip, 5555), 400) }
            ui.post {
                if (gen == scanGen && state == "scan" && !found.contains(ip)) {
                    found.add(ip)
                    onFound(ip)
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun startScan(list: LinearLayout, status: TextView, pb: ProgressBar, restore: Boolean) {
        val gen = ++scanGen
        val found = ArrayList<String>()
        Thread {
            val subnets = LanScanner.subnets()
            val pool = Executors.newFixedThreadPool(48)
            val tasks = ArrayList<Callable<Unit>>()
            for (s in subnets) {
                for (i in 1..254) {
                    tasks.add(Callable { probe(s.prefix, s.own, i, gen, found) { ip -> addDevice(list, ip, restore) } })
                }
            }
            try {
                pool.invokeAll(tasks)
            } catch (e: Exception) {
            }
            pool.shutdown()
            ui.post {
                if (gen == scanGen) {
                    pb.visibility = View.GONE
                    status.text = if (found.isEmpty()) getString(R.string.none_found)
                    else getString(R.string.found_n, found.size)
                }
            }
        }.start()
    }

    private fun addDevice(list: LinearLayout, ip: String, restore: Boolean) {
        val row = col()
        row.setPadding(dp(18), dp(16), dp(18), dp(16))
        row.background = rounded(0xFF2A1020.toInt(), 18)
        row.isClickable = true
        row.addView(text(getString(R.string.found_tv, ip), 18f, WHITE, true))
        row.setOnClickListener { runJob(ip, restore) }
        list.addView(row, lp(top = 10))
    }

    // ---------- Install / restore ----------

    private fun hasApk(): Boolean {
        if (File(filesDir, "syhome.apk").exists()) return true
        return try {
            assets.open("syhome.apk").close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun apkFile(): File {
        val out = File(cacheDir, "syhome.apk")
        try {
            assets.open("syhome.apk").use { ins -> out.outputStream().use { ins.copyTo(it) } }
            return out
        } catch (e: Exception) {
            return File(filesDir, "syhome.apk")
        }
    }

    private fun pickApk() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                reqApk
            )
        } catch (e: Exception) {
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqApk && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.openInputStream(uri)?.use { ins ->
                    File(filesDir, "syhome.apk").outputStream().use { ins.copyTo(it) }
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.no_apk, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun keyPair(): AdbKeyPair {
        val priv = File(filesDir, "adbkey")
        val pub = File(filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    private fun log(msg: String) {
        ui.post { logView?.append(msg + "\n") }
    }

    private fun finishJob() {
        ui.post {
            progressBar?.visibility = View.GONE
            doneBtn?.visibility = View.VISIBLE
        }
    }

    private fun runJob(target: String, restore: Boolean) {
        if (!restore && !hasApk()) {
            Toast.makeText(this, R.string.no_apk, Toast.LENGTH_LONG).show()
            pickApk()
            return
        }
        scanGen++
        showProgress(restore)
        Thread { doJob(target, restore) }.start()
    }

    private fun doJob(target: String, restore: Boolean) {
        val host = target.substringBefore(":")
        val port = target.substringAfter(":", "5555").toIntOrNull() ?: 5555
        var dadb: Dadb? = null
        try {
            log(getString(R.string.log_connect, target))
            log(getString(R.string.log_allow))
            val d = Dadb.create(host, port, keyPair())
            dadb = d
            val hello = d.shell("echo ok")
            if (!hello.output.contains("ok")) throw IOException("no response")
            log(getString(R.string.log_connected))
            if (restore) restoreOriginal(d, host) else install(d, host)
        } catch (e: Exception) {
            log(getString(R.string.log_fail, e.message ?: e.javaClass.simpleName))
        } finally {
            try {
                dadb?.close()
            } catch (e: Exception) {
            }
            finishJob()
        }
    }

    private fun currentHome(d: Dadb): String? {
        val out = d.shell(
            "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN"
        ).output
        return out.lines().map { it.trim() }.lastOrNull { it.contains("/") && !it.contains(" ") }
    }

    private fun install(d: Dadb, host: String) {
        val cur = currentHome(d)
        if (cur != null && !cur.startsWith(TV_PKG) && !cur.startsWith("android/")) {
            prefs.edit().putString("orig_$host", cur).apply()
        }
        log(getString(R.string.log_saved))

        log(getString(R.string.log_push))
        d.install(apkFile())
        log(getString(R.string.log_installed))

        log(getString(R.string.log_sethome))
        d.shell("cmd package set-home-activity --user 0 $TV_PKG/$TV_ACTIVITY")
        d.shell("am start -n $TV_PKG/$TV_ACTIVITY")
        log(getString(R.string.log_started))
    }

    private fun restoreOriginal(d: Dadb, host: String) {
        val orig = prefs.getString("orig_$host", null)
        if (orig != null) {
            d.shell("cmd package set-home-activity --user 0 $orig")
            log(getString(R.string.log_restore_ok))
        } else {
            d.shell("pm uninstall $TV_PKG")
            log(getString(R.string.log_restore_none))
        }
    }
}
