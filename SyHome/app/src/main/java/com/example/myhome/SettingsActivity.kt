package com.example.myhome

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var list: LinearLayout
    private lateinit var rootList: LinearLayout
    private lateinit var scrollView: ScrollView
    private val ui = Handler(Looper.getMainLooper())
    private val reqImage = 11
    /** Preference key of the row that should regain focus after rebuild. */
    private var pendingFocusKey: String? = null
    /** Saved vertical scroll so rebuild does not jump to the top. */
    private var savedScrollY = 0
    /** Which expandable groups stay open across rebuilds. */
    private val expandedGroups = mutableSetOf<Int>()

    private val bgColors = intArrayOf(
        0xFF050104.toInt(), 0xFF000000.toInt(), 0xFF0B1020.toInt(), 0xFF102A43.toInt(), 0xFF0F1F1A.toInt(),
        0xFF1B1033.toInt(), 0xFF2B0F14.toInt(), 0xFF3A2A12.toInt(), 0xFF1A1A1A.toInt(), 0xFF122B2B.toInt()
    )
    private val accentColors = intArrayOf(
        0xFFE8A33D.toInt(), 0xFF4FC3F7.toInt(), 0xFF81C784.toInt(), 0xFFF06292.toInt(),
        0xFFBA68C8.toInt(), 0xFFFF7043.toInt(), 0xFFFFFFFF.toInt(), 0xFF26C6DA.toInt()
    )
    private val methodIds = listOf(3, 0, 4, 5, 2, 1, 13)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("home", MODE_PRIVATE)
        scrollView = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(80), dp(32), dp(80), dp(32))
        }
        rootList = list
        scrollView.addView(rootList)
        setContentView(scrollView)
        build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    // ---------- Screen ----------

    private fun build() {
        // Keep the user where they were: scroll position + the last toggled/edited row.
        if (::scrollView.isInitialized) savedScrollY = scrollView.scrollY

        window.setBackgroundDrawable(Style.background(this, prefs))
        list = rootList
        rootList.removeAllViews()
        rootList.addView(TextView(this).apply {
            setText(R.string.settings_title)
            textSize = 32f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(16))
        })

        section(R.string.section_language)
        row(R.string.s_language, languageName()) { chooseLanguage() }

        section(R.string.section_background)
        val imageMode = Style.isImageBackground(this, prefs)
        val bgValue = when {
            Style.isPreset(prefs) -> resources.getStringArray(R.array.nature_names)
                .getOrElse(prefs.getInt("bgPreset", 0)) { getString(R.string.bg_nature) }
            imageMode -> getString(R.string.bg_image)
            else -> getString(R.string.bg_color)
        }
        row(
            R.string.s_background,
            bgValue,
            if (imageMode) null else prefs.getInt("bgColor", Style.DEFAULT_BG)
        ) { chooseBackground() }

        if (imageMode) {
            row(R.string.s_dim, "${prefs.getInt("dim", 40)}%") { chooseDim() }
        }
        row(R.string.s_bg_auto, getString(if (prefs.getBoolean("bgAuto", false)) R.string.s_on else R.string.s_off)) {
            val enable = !prefs.getBoolean("bgAuto", false)
            val edit = prefs.edit().putBoolean("bgAuto", enable)
            if (enable && !Style.isPreset(prefs)) edit.putString("bgMode", "preset").putInt("bgPreset", 0)
            edit.apply(); build()
        }
        row(R.string.s_bg_interval, intervalLabel()) { chooseBgInterval() }
        toggle(R.string.s_bg_random, "bgRandom", false)

        section(R.string.section_layout)
        row(R.string.s_app_icon_size, "${prefs.getInt("appIconSize", 96)} dp") { chooseAppIconSize() }
        row(R.string.s_hidden_apps, "${hiddenAppCount()}" ) { manageHiddenApps() }
        row(R.string.s_accent, "", Style.accent(prefs)) {
            pickColor(R.string.s_accent, accentColors) { c ->
                prefs.edit().putInt("accent", c).apply()
                build()
            }
        }
        toggle(R.string.s_glass, "glass", true)
        row(R.string.s_glass_alpha, "${prefs.getInt("glassAlpha", 78)}%") { chooseGlassAlpha() }
        section(R.string.section_weather)
        toggle(R.string.s_weather, "showWeather")
        toggle(R.string.s_weather_forecast, "showWeatherForecast", true)
        section(R.string.section_prayer)
        toggle(R.string.s_prayers, "showPrayers")
        group(R.string.section_match_times) {
            toggle(R.string.s_matches, "showMatches")
            row(R.string.s_match_count, prefs.getInt("matchCount", 3).toString()) { chooseMatchCount() }
            toggle(R.string.s_notify_match, "notifyMatch", false)
            row(R.string.s_notify_before, "${prefs.getInt("notifyBefore", 15)} min") { chooseNotifyBefore() }
            row(R.string.s_leagues, leagueCount().toString()) { chooseLeagues() }
            row(R.string.s_team, (prefs.getString("team", "") ?: "").ifEmpty { "—" }) { chooseTeam() }
        }
        section(R.string.section_clock)
        row(
            R.string.s_clock,
            getString(if (prefs.getBoolean("clock24", true)) R.string.clock_24 else R.string.clock_12)
        ) { chooseClock() }
        toggle(R.string.s_clock_seconds, "clockSeconds", false)
        row(R.string.s_clock_style, clockStyleLabel()) { chooseClockStyle() }
        row(R.string.s_clock_size, listOf("Small", "Medium", "Large").getOrElse(prefs.getInt("clockSize", 1)) { "Medium" }) { chooseClockSize() }
        row(
            R.string.s_units,
            getString(if (prefs.getBoolean("unitF", false)) R.string.unit_f else R.string.unit_c)
        ) { chooseUnit() }
        row(R.string.s_city, Config.city(prefs)) { chooseCity() }
        row(R.string.s_mosque, (prefs.getString("mosqueName", "") ?: "").ifEmpty { getString(R.string.mosque_none) }) { chooseMosque() }
        row(R.string.s_hijri_adjust, "${prefs.getInt("hijriAdjust", 0)} day") { chooseHijriAdjust() }
        group(R.string.section_adhkar) {
            // Primary adhkar controls first, in the exact order requested.
            row(R.string.s_adhkar_size, "${prefs.getInt("adhkarSize", 24)} sp") { chooseAdhkarSize() }
            toggle(R.string.s_adhkar_source, "adhkarShowSource", true)
            row(R.string.s_adhkar_count, prefs.getInt("adhkarCount", 3).toString()) { chooseAdhkarCount() }
            row(R.string.s_adhkar_position, adhkarPositionLabel()) { chooseAdhkarPosition() }
            row(R.string.s_adhkar_opacity, "${prefs.getInt("adhkarOpacity", 100)}%") { chooseAdhkarOpacity() }
            row(R.string.s_adhkar_color, "", prefs.getInt("adhkarColor", Color.WHITE)) {
                pickColor(R.string.s_adhkar_color, accentColors + intArrayOf(Color.WHITE, Color.YELLOW)) { c -> prefs.edit().putInt("adhkarColor", c).apply(); build() }
            }
            row(R.string.s_adhkar_interval, "${prefs.getInt("adhkarInterval", 15)} ${getString(R.string.seconds_unit)}") { chooseAdhkarInterval() }
            // Additional adhkar controls follow the basics.
            toggle(R.string.s_adhkar_enable, "adhkarEnabled", true)
            toggle(R.string.s_adhkar_auto, "adhkarAuto", true)
            toggle(R.string.s_adhkar_sequential, "adhkarSequential", false)
            toggle(R.string.s_adhkar_morning, "adhkarMorning", true)
            toggle(R.string.s_adhkar_evening, "adhkarEvening", true)
            toggle(R.string.s_adhkar_general, "adhkarGeneral", true)
            toggle(R.string.s_adhkar_sleep, "adhkarSleep", true)
            toggle(R.string.s_adhkar_wake, "adhkarWake", true)
            toggle(R.string.s_adhkar_prayer, "adhkarPrayer", true)
            toggle(R.string.s_adhkar_quran, "adhkarQuran", true)
            toggle(R.string.s_adhkar_dua, "adhkarDua", true)
        }
        section(R.string.section_standby)
        row(R.string.s_idle, idleLabel()) { chooseIdle() }
        section(R.string.section_night)
        toggle(R.string.s_night_manual, "nightManual", false)
        toggle(R.string.s_night_auto, "nightAuto", false)
        row(R.string.s_night_start, prefs.getString("nightStart", "22:00") ?: "22:00") { chooseTime("nightStart") }
        row(R.string.s_night_end, prefs.getString("nightEnd", "06:00") ?: "06:00") { chooseTime("nightEnd") }
        row(R.string.s_method, methodName(Config.method(prefs))) { chooseMethod() }
        row(R.string.reset_favs, "") {
            prefs.edit().remove("favs").apply()
            toast(R.string.done_toast)
        }
        row(R.string.reset_all, "") {
            AlertDialog.Builder(this).setTitle(R.string.reset_all)
                .setMessage(if (prefs.contains("footballApiKey") || prefs.contains("mawaqitApiKey")) "This also removes saved API keys. Continue?" else "Restore all settings to their defaults?")
                .setPositiveButton(R.string.ok) { _, _ -> resetAll() }
                .setNegativeButton(R.string.cancel, null).show()
        }
        row(R.string.credit_by, "Mohamad Asaad") { }
        row(R.string.credit_ig, "Mo7m199") { openInstagram() }

        rootList.post {
            scrollView.scrollTo(0, savedScrollY)
            val key = pendingFocusKey
            pendingFocusKey = null
            var focused: View? = null
            if (key != null) {
                fun find(v: ViewGroup): View? {
                    for (i in 0 until v.childCount) {
                        val c = v.getChildAt(i)
                        if (c.tag == key && c.isFocusable) return c
                        if (c is ViewGroup) find(c)?.let { return it }
                    }
                    return null
                }
                focused = find(rootList)
            }
            focused?.requestFocus()
            scrollView.post { scrollView.scrollTo(0, savedScrollY) }
        }
    }

    /** A compact, remote-friendly expandable settings folder. */
    private fun group(titleRes: Int, content: () -> Unit) {
        val wasExpanded = titleRes in expandedGroups
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = Style.card(this@SettingsActivity, Style.accent(prefs))
            tag = "group:$titleRes"
        }
        header.addView(TextView(this).apply {
            setText(titleRes); textSize = 21f; setTextColor(0xFFE8A33D.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = TextView(this).apply {
            text = if (wasExpanded) "−" else "＋"
            textSize = 24f
            setTextColor(Color.WHITE)
        }
        header.addView(arrow)
        val children = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (wasExpanded) View.VISIBLE else View.GONE
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        val outer = list
        outer.addView(header, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(10), 0, dp(4)) })
        outer.addView(children, LinearLayout.LayoutParams(-1, -2))
        header.setOnClickListener {
            val expanded = children.visibility != View.VISIBLE
            children.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.text = if (expanded) "−" else "＋"
            if (expanded) expandedGroups.add(titleRes) else expandedGroups.remove(titleRes)
        }
        list = children
        content()
        list = outer
    }

    private fun section(titleRes: Int) {
        list.addView(TextView(this).apply {
            setText(titleRes)
            textSize = 15f
            setTextColor(0xFFE8A33D.toInt())
            setPadding(0, dp(18), 0, dp(4))
            isFocusable = false
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun row(titleRes: Int, value: String, swatch: Int? = null, focusKey: String? = null, onClick: () -> Unit) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = Style.card(this@SettingsActivity, Style.accent(prefs))
            if (focusKey != null) tag = focusKey
            setOnClickListener { onClick() }
        }
        r.addView(TextView(this).apply {
            setText(titleRes)
            textSize = 20f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (swatch != null) {
            val sw = View(this)
            sw.background = GradientDrawable().apply {
                setColor(swatch)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
            val slp = LinearLayout.LayoutParams(dp(28), dp(28))
            slp.marginEnd = dp(12)
            r.addView(sw, slp)
        }
        r.addView(TextView(this).apply {
            text = value
            textSize = 18f
            setTextColor(0xFFC9BFCB.toInt())
        })
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(6), 0, dp(6))
        list.addView(r, lp)
    }

    private fun toggle(titleRes: Int, key: String, default: Boolean = true) {
        val on = prefs.getBoolean(key, default)
        row(titleRes, getString(if (on) R.string.s_on else R.string.s_off), focusKey = key) {
            pendingFocusKey = key
            prefs.edit().putBoolean(key, !on).apply()
            if (key == "notifyMatch" && on) MatchAlerts.cancel(this, prefs)
            build()
        }
    }

    private fun choose(titleRes: Int, labels: List<String>, current: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this).setTitle(titleRes)
            .setSingleChoiceItems(labels.toTypedArray(), current) { d, which ->
                d.dismiss()
                onPick(which)
            }.show()
    }

    // ---------- Matches / credits ----------

    private fun leagueCount(): Int =
        (prefs.getString("leagues", Matches.DEFAULT) ?: Matches.DEFAULT).split(",").count { it.isNotEmpty() }

    private fun chooseLeagues() {
        val ids = Matches.leagueIds
        val names = resources.getStringArray(R.array.league_names)
        val cur = (prefs.getString("leagues", Matches.DEFAULT) ?: Matches.DEFAULT).split(",").toSet()
        val checked = BooleanArray(ids.size) { ids[it] in cur }
        AlertDialog.Builder(this).setTitle(R.string.s_leagues)
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(R.string.ok) { _, _ ->
                val sel = ids.filterIndexed { i, _ -> checked[i] }
                prefs.edit().putString("leagues", sel.joinToString(",")).putLong("matchesAt", 0).apply()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseTeam() {
        val input = EditText(this)
        input.hint = getString(R.string.team_hint)
        input.setSingleLine()
        input.setText(prefs.getString("team", "") ?: "")
        AlertDialog.Builder(this).setTitle(R.string.s_team).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.edit().putString("team", input.text.toString().trim()).apply()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openInstagram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/Mo7m199")))
        } catch (e: Exception) {
        }
    }

    // ---------- Language ----------

    private fun languageName(): String = when (prefs.getString("lang", "auto")) {
        "ar" -> "العربية"
        "en" -> "English"
        else -> getString(R.string.lang_auto)
    }

    private fun chooseLanguage() {
        val codes = listOf("auto", "ar", "en")
        val labels = listOf(getString(R.string.lang_auto), "العربية", "English")
        val cur = codes.indexOf(prefs.getString("lang", "auto")).coerceAtLeast(0)
        choose(R.string.s_language, labels, cur) { i ->
            prefs.edit().putString("lang", codes[i]).apply()
            recreate()
        }
    }

    // ---------- Background ----------

    private fun chooseBackground() {
        val items = arrayOf(
            getString(R.string.bg_pick_color), getString(R.string.bg_pick_nature),
            getString(R.string.bg_pick_image), getString(R.string.bg_reset)
        )
        AlertDialog.Builder(this).setTitle(R.string.s_background).setItems(items) { _, which ->
            when (which) {
                0 -> pickColor(R.string.bg_pick_color, bgColors) { c ->
                    prefs.edit().putString("bgMode", "color").putInt("bgColor", c).apply()
                    build()
                }
                1 -> chooseNature()
                2 -> pickImage()
                3 -> {
                    prefs.edit().putString("bgMode", "color").putInt("bgColor", Style.DEFAULT_BG).apply()
                    build()
                }
            }
        }.show()
    }

    private fun chooseNature() {
        val names = resources.getStringArray(R.array.nature_names).toList()
        val cur = if (Style.isPreset(prefs)) prefs.getInt("bgPreset", 0) else -1
        choose(R.string.bg_pick_nature, names, cur) { i ->
            prefs.edit().putString("bgMode", "preset").putInt("bgPreset", i).apply()
            build()
        }
    }

    // ---------- Mosque (MAWAQIT) ----------

    private fun chooseMosque() {
        val input = EditText(this)
        input.hint = getString(R.string.mosque_hint)
        input.setSingleLine()
        input.setText(Config.city(prefs))
        val b = AlertDialog.Builder(this).setTitle(R.string.s_mosque).setMessage(R.string.mosque_help).setView(input)
            .setPositiveButton(R.string.search) { _, _ -> searchMosque(input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
        if (!(prefs.getString("mosqueName", "") ?: "").isEmpty()) {
            b.setNeutralButton(R.string.mosque_clear) { _, _ ->
                prefs.edit().remove("mosqueName").remove("mosqueUuid").remove("mosqueUrl")
                    .putString("prayerDate", "").putLong("prayerTry", 0).apply()
                toast(R.string.done_toast)
                build()
            }
        }
        b.show()
    }

    private fun searchMosque(q: String) {
        if (q.isEmpty()) return
        Toast.makeText(this, R.string.mosque_searching, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val found = Mawaqit.search(q)
                if (found.isEmpty()) {
                    ui.post { toast(R.string.no_results) }
                    return@Thread
                }
                val names = found.map { m -> if (m.place.isEmpty()) m.name else m.name + "\n" + m.place }
                ui.post {
                    AlertDialog.Builder(this).setTitle(R.string.s_mosque)
                        .setItems(names.toTypedArray()) { _, which -> pickMosque(found[which]) }
                        .show()
                }
            } catch (e: Exception) {
                prefs.edit().putString("prayerErr", Net.reason(e)).apply()
                ui.post { toast(R.string.mosque_search_fail) }
            }
        }.start()
    }

    private fun pickMosque(m: Mawaqit.Mosque) {
        prefs.edit().putString("mosqueName", m.name).putString("mosqueUuid", m.uuid)
            .putString("mosqueUrl", m.url).putString("prayerDate", "").putLong("prayerTry", 0).apply()
        build()
        // try it right away so the user sees whether it works
        Thread {
            try {
                val t = Mawaqit.timesFor(m.url, m.uuid)
                prefs.edit().putString("prayers", t.joinToString(",")).putString("prayerDate", Refresh.today())
                    .putString("prayerSrc", "mawaqit").putString("prayerErr", "").apply()
                ui.post { Toast.makeText(this, getString(R.string.mosque_ok) + "  " + t.joinToString("  "), Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                prefs.edit().putString("prayerErr", Net.reason(e)).apply()
                ui.post { toast(R.string.mosque_fail) }
            }
        }.start()
    }

    private fun pickColor(titleRes: Int, colors: IntArray, onPick: (Int) -> Unit) {
        val grid = GridLayout(this)
        grid.columnCount = 5
        grid.setPadding(dp(16), dp(16), dp(16), dp(16))
        var dlg: AlertDialog? = null
        for (c in colors) {
            val v = View(this)
            v.isFocusable = true
            v.isClickable = true
            v.background = Style.swatch(this, c)
            v.setOnClickListener {
                dlg?.dismiss()
                onPick(c)
            }
            val lp = GridLayout.LayoutParams()
            lp.width = dp(56)
            lp.height = dp(56)
            lp.setMargins(dp(6), dp(6), dp(6), dp(6))
            grid.addView(v, lp)
        }
        dlg = AlertDialog.Builder(this).setTitle(titleRes).setView(grid).create()
        dlg.show()
    }

    private fun pickImage() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                reqImage
            )
        } catch (e: Exception) {
            try {
                startActivityForResult(
                    Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),
                    reqImage
                )
            } catch (e2: Exception) {
                toast(R.string.no_picker)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqImage && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) importImage(uri)
        }
    }

    // Decodes the picked image, crops it to the screen shape and keeps a private copy
    private fun importImage(uri: Uri) {
        Thread {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: throw Exception("decode failed")

                val dm = resources.displayMetrics
                val target = dm.widthPixels.toFloat() / dm.heightPixels
                val src = bmp.width.toFloat() / bmp.height
                val cw: Int
                val ch: Int
                if (src > target) {
                    ch = bmp.height
                    cw = (ch * target).toInt()
                } else {
                    cw = bmp.width
                    ch = (cw / target).toInt()
                }
                val cropped = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
                FileOutputStream(Style.imageFile(this)).use { cropped.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                prefs.edit().putString("bgMode", "image").apply()
                ui.post { build() }
            } catch (e: Exception) {
                ui.post { toast(R.string.img_error) }
            }
        }.start()
    }

    private fun chooseDim() {
        val values = listOf(0, 20, 40, 60, 80)
        val cur = values.indexOf(prefs.getInt("dim", 40)).coerceAtLeast(0)
        choose(R.string.s_dim, values.map { "$it%" }, cur) { i ->
            prefs.edit().putInt("dim", values[i]).apply()
            build()
        }
    }

    // ---------- Other options ----------

    private fun chooseColumns() {
        val values = listOf(4, 5, 6, 7)
        val cur = values.indexOf(prefs.getInt("cols", 6)).coerceAtLeast(0)
        choose(R.string.s_columns, values.map { it.toString() }, cur) { i ->
            prefs.edit().putInt("cols", values[i]).apply()
            build()
        }
    }

    private fun chooseClock() {
        val cur = if (prefs.getBoolean("clock24", true)) 0 else 1
        choose(R.string.s_clock, listOf(getString(R.string.clock_24), getString(R.string.clock_12)), cur) { i ->
            prefs.edit().putBoolean("clock24", i == 0).apply()
            build()
        }
    }

    private fun chooseClockSize() {
        val values = listOf(0, 1, 2)
        choose(R.string.s_clock_size, listOf("Small", "Medium", "Large"), prefs.getInt("clockSize", 1).coerceIn(0, 2)) { i ->
            prefs.edit().putInt("clockSize", values[i]).apply(); build()
        }
    }

    private fun hiddenAppCount(): Int = (prefs.getStringSet("hidden", emptySet()) ?: emptySet()).size

    private fun chooseAppIconSize() {
        val seek = android.widget.SeekBar(this).apply { max = 72; progress = (prefs.getInt("appIconSize", 96) - 64).coerceIn(0, 72) }
        val preview = TextView(this).apply { text = "${prefs.getInt("appIconSize", 96)} dp"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(8)) }
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, value: Int, fromUser: Boolean) { preview.text = "${value + 64} dp" }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0); addView(preview); addView(seek) }
        AlertDialog.Builder(this).setTitle(R.string.s_app_icon_size).setView(box)
            .setPositiveButton(R.string.ok) { _, _ -> prefs.edit().putInt("appIconSize", seek.progress + 64).apply(); build() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun manageHiddenApps() {
        val hidden = (prefs.getStringSet("hidden", emptySet()) ?: emptySet()).toMutableSet()
        val pm = packageManager
        val entries = hidden.mapNotNull { pkg -> try {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
            val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            pkg to label
        } catch (_: Exception) { null } }.sortedBy { it.second.lowercase() }
        if (entries.isEmpty()) { Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_LONG).show(); return }
        val checked = BooleanArray(entries.size)
        AlertDialog.Builder(this).setTitle(R.string.s_hidden_apps)
            .setMultiChoiceItems(entries.map { it.second }.toTypedArray(), checked) { _, which, value -> checked[which] = value }
            .setPositiveButton(R.string.show_selected) { _, _ ->
                val next = hidden.toMutableSet(); entries.forEachIndexed { i, pair -> if (checked[i]) next.remove(pair.first) }
                prefs.edit().putStringSet("hidden", next).apply(); Toast.makeText(this, R.string.done_toast, Toast.LENGTH_SHORT).show(); build()
            }
            .setNeutralButton(R.string.show_all) { _, _ -> prefs.edit().putStringSet("hidden", emptySet()).apply(); build() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun clockStyleLabel(): String {
        val ar = resources.configuration.locales[0].language == "ar"
        return ClockStyles.names(ar).getOrElse(prefs.getInt("clockStyle", 0)) { ClockStyles.names(ar)[0] }
    }
    private fun chooseClockStyle() {
        val ar = resources.configuration.locales[0].language == "ar"
        val names = ClockStyles.names(ar)
        val current = prefs.getInt("clockStyle", 0).coerceIn(0, ClockStyles.COUNT - 1)
        val accent = Style.accent(prefs)
        val preview = TextView(this).apply {
            text = "12:45"; textSize = 48f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(dp(32), dp(24), dp(32), dp(24)); tag = current
        }
        fun previewStyle(which: Int) {
            ClockStyles.apply(this@SettingsActivity, preview, which, accent, big = false)
            preview.tag = which
        }
        previewStyle(current)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.s_clock_style).setView(preview)
            .setSingleChoiceItems(names.toTypedArray(), current) { _, which -> previewStyle(which) }
            .setPositiveButton(R.string.ok) { _, _ ->
                pendingFocusKey = "clockStyle"
                prefs.edit().putInt("clockStyle", (preview.tag as? Int) ?: current).apply()
                build()
            }
            .setNegativeButton(R.string.cancel, null).create()
        dialog.show()
    }
    private fun chooseAdhkarInterval() {
        val values = listOf(5, 10, 15, 30)
        val labels = values.map { "$it ${getString(R.string.seconds_unit)}" } + getString(R.string.custom_value)
        AlertDialog.Builder(this).setTitle(R.string.s_adhkar_interval)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < values.size) prefs.edit().putInt("adhkarInterval", values[which]).apply().also { build() }
                else {
                    val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("adhkarInterval", 15).toString()) }
                    AlertDialog.Builder(this).setTitle(R.string.s_adhkar_interval).setView(input)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val seconds = input.text.toString().toIntOrNull()?.coerceIn(5, 3600) ?: 15
                            prefs.edit().putInt("adhkarInterval", seconds).apply(); build()
                        }.setNegativeButton(R.string.cancel, null).show()
                }
            }.show()
    }
    private fun chooseAdhkarCount() {
        val values = listOf(1, 2, 3, 4, 5, 6, 8, 10)
        val labels = values.map { it.toString() } + getString(R.string.custom_value)
        AlertDialog.Builder(this).setTitle(R.string.s_adhkar_count)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < values.size) { prefs.edit().putInt("adhkarCount", values[which]).apply(); build() }
                else {
                    val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("adhkarCount", 3).toString()) }
                    AlertDialog.Builder(this).setTitle(R.string.s_adhkar_count).setView(input)
                        .setPositiveButton(R.string.ok) { _, _ -> prefs.edit().putInt("adhkarCount", input.text.toString().toIntOrNull()?.coerceIn(1, 10) ?: 3).apply(); build() }
                        .setNegativeButton(R.string.cancel, null).show()
                }
            }.show()
    }
    private fun adhkarPositionLabel() = when (prefs.getInt("adhkarPosition", 0)) { 1 -> getString(R.string.position_center); 2 -> getString(R.string.position_top); else -> getString(R.string.position_bottom) }
    private fun chooseAdhkarPosition() = choose(R.string.s_adhkar_position, listOf(getString(R.string.position_bottom), getString(R.string.position_center), getString(R.string.position_top)), prefs.getInt("adhkarPosition", 0).coerceIn(0, 2)) { i -> prefs.edit().putInt("adhkarPosition", i).apply(); build() }
    private fun chooseAdhkarOpacity() = choose(R.string.s_adhkar_opacity, listOf("40%", "60%", "80%", "100%"), listOf(40,60,80,100).indexOf(prefs.getInt("adhkarOpacity", 100)).coerceAtLeast(0)) { i -> prefs.edit().putInt("adhkarOpacity", listOf(40,60,80,100)[i]).apply(); build() }
    private fun chooseAdhkarSize() = choose(R.string.s_adhkar_size, listOf("18", "22", "26", "30", "34", "38"), listOf(18,22,26,30,34,38).indexOf(prefs.getInt("adhkarSize",24)).let { if (it < 0) 1 else it }) { i -> prefs.edit().putInt("adhkarSize", listOf(18,22,26,30,34,38)[i]).apply(); build() }

    private fun chooseUnit() {
        val cur = if (prefs.getBoolean("unitF", false)) 1 else 0
        choose(R.string.s_units, listOf(getString(R.string.unit_c), getString(R.string.unit_f)), cur) { i ->
            prefs.edit().putBoolean("unitF", i == 1).putLong("weatherAt", 0).apply()
            build()
        }
    }

    private fun methodName(id: Int): String {
        val names = resources.getStringArray(R.array.method_names)
        val i = methodIds.indexOf(id)
        return if (i in names.indices) names[i] else id.toString()
    }

    private fun chooseMethod() {
        val names = resources.getStringArray(R.array.method_names).toList()
        val cur = methodIds.indexOf(Config.method(prefs)).coerceAtLeast(0)
        choose(R.string.s_method, names, cur) { i ->
            prefs.edit().putInt("method", methodIds[i]).putString("prayerDate", "").apply()
            build()
        }
    }

    private fun chooseCity() {
        val input = EditText(this)
        input.hint = getString(R.string.city_hint)
        input.setSingleLine()
        AlertDialog.Builder(this).setTitle(R.string.s_city).setView(input)
            .setPositiveButton(R.string.search) { _, _ -> searchCity(input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun searchCity(q: String) {
        if (q.isEmpty()) return
        Thread {
            try {
                val body = Net.get(
                    "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(q, "UTF-8") +
                        "&count=8&language=" + Locale.getDefault().language
                )
                val arr = JSONObject(body).optJSONArray("results")
                if (arr == null || arr.length() == 0) {
                    ui.post { toast(R.string.no_results) }
                    return@Thread
                }
                val names = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    names.add(
                        listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                            .filter { it.isNotEmpty() }.joinToString(", ")
                    )
                }
                ui.post {
                    AlertDialog.Builder(this).setTitle(R.string.s_city)
                        .setItems(names.toTypedArray()) { _, which ->
                            val o = arr.getJSONObject(which)
                            prefs.edit()
                                .putString("lat", o.getDouble("latitude").toString())
                                .putString("lon", o.getDouble("longitude").toString())
                                .putString("city", o.optString("name"))
                                .putLong("weatherAt", 0)
                                .putString("prayerDate", "")
                                .apply()
                            build()
                        }.show()
                }
            } catch (e: Exception) {
                ui.post { toast(R.string.net_error) }
            }
        }.start()
    }

    private fun intervalLabel(): String = when (prefs.getInt("bgInterval", 300)) {
        30 -> "30 sec"; 60 -> "1 min"; 900 -> "15 min"; 3600 -> "1 hour"; else -> "5 min"
    }
    private fun chooseBgInterval() {
        val values = listOf(30, 60, 300, 900, 3600)
        val labels = listOf("30 sec", "1 min", "5 min", "15 min", "1 hour")
        choose(R.string.s_bg_interval, labels, values.indexOf(prefs.getInt("bgInterval", 300)).coerceAtLeast(0)) { i ->
            prefs.edit().putInt("bgInterval", values[i]).apply(); build()
        }
    }
    private fun chooseMatchCount() {
        val values = listOf(1, 2, 3, 4)
        choose(R.string.s_match_count, values.map { it.toString() }, values.indexOf(prefs.getInt("matchCount", 3)).coerceAtLeast(0)) { i ->
            prefs.edit().putInt("matchCount", values[i]).apply(); build()
        }
    }
    private fun chooseNotifyBefore() {
        val values = listOf(15, 30, 60)
        choose(R.string.s_notify_before, values.map { "$it min" }, values.indexOf(prefs.getInt("notifyBefore", 15)).coerceAtLeast(0)) { i ->
            prefs.edit().putInt("notifyBefore", values[i]).apply(); build()
        }
    }
    private fun chooseHijriAdjust() {
        val values = listOf(-1, 0, 1)
        choose(R.string.s_hijri_adjust, listOf("-1 day", "0", "+1 day"), values.indexOf(prefs.getInt("hijriAdjust", 0)).coerceAtLeast(0)) { i ->
            prefs.edit().putInt("hijriAdjust", values[i]).apply(); build()
        }
    }
    private fun idleLabel(): String = when (prefs.getInt("idleMinutes", 5)) {
        0 -> getString(R.string.s_off); 1 -> "1 min"; 10 -> "10 min"; 30 -> "30 min"; else -> "5 min"
    }
    private fun chooseIdle() {
        val values = listOf(0, 1, 5, 10, 30)
        val labels = listOf(getString(R.string.s_off), "1 min", "5 min", "10 min", "30 min")
        choose(R.string.s_idle, labels, values.indexOf(prefs.getInt("idleMinutes", 5)).coerceAtLeast(0)) { i ->
            prefs.edit().putInt("idleMinutes", values[i]).apply(); build()
        }
    }
    private fun chooseGlassAlpha() {
        val values = listOf(35, 50, 65, 78, 90, 100)
        choose(R.string.s_glass_alpha, values.map { "$it%" }, values.indexOf(prefs.getInt("glassAlpha", 78)).coerceAtLeast(0)) { i ->
            prefs.edit().putInt("glassAlpha", values[i]).apply(); build()
        }
    }
    private fun chooseTime(key: String) {
        val input = EditText(this).apply { setSingleLine(); hint = "HH:mm"; setText(prefs.getString(key, if (key == "nightStart") "22:00" else "06:00")) }
        AlertDialog.Builder(this).setTitle(if (key == "nightStart") R.string.s_night_start else R.string.s_night_end).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val value = input.text.toString().trim()
                if (value.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) { prefs.edit().putString(key, value).apply(); build() }
                else Toast.makeText(this, "Use HH:mm", Toast.LENGTH_SHORT).show()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun resetAll() {
        MatchAlerts.cancel(this, prefs)
        val keys = listOf(
            "lang", "team", "favs", "hidden", "showMatches", "leagues", "matches", "matchesAt", "bgMode", "bgColor", "dim", "accent", "cols", "labels", "showWeather", "showPrayers",
            "clock24", "unitF", "lat", "lon", "city", "method", "weather", "weatherAt", "weatherTry", "wtemp", "wcode", "wmax", "wmin", "prayerDate",
            "bgPreset", "mosqueName", "mosqueUuid", "mosqueUrl", "prayers", "prayerErr", "prayerTry", "matchesErr", "matchesTry",
            "bgAuto", "bgInterval", "bgStatic", "glass", "nightAuto", "nightStart", "nightEnd", "idleMinutes",
            "matchCount", "notifyMatch", "notifyBefore", "weatherForecast", "hijriAdjust", "bgRandom", "glassAlpha", "nightDim", "whumidity", "wwind", "clockSeconds", "clockSize", "showWeatherForecast", "footballApiKey", "mawaqitApiKey", "scheduledMatchIds", "nightManual", "appIconSize", "clockStyle", "adhkarEnabled", "adhkarMorning", "adhkarEvening", "adhkarGeneral", "adhkarInterval", "adhkarSize", "adhkarShowSource"
        )
        val e = prefs.edit()
        for (k in keys) e.remove(k)
        e.apply()
        Style.imageFile(this).delete()
        build()
    }
}
