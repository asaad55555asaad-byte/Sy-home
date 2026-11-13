package com.example.myhome

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextClock
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Calm full-screen view: big clock, weather, next match, Ramadan countdown, next prayer. */
class StandbyActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
    private val ui = Handler(Looper.getMainLooper())
    private var lastBgChange = 0L
    private var lastAdhkarAt = 0L
    private var adhkarSequentialIndex = 0
    private var lastAdhkarSignature = ""
    private lateinit var adhkarContainer: android.widget.LinearLayout
    private val adhkarTask = object : Runnable {
        override fun run() {
            renderAdhkar(force = false)
            val enabled = prefs.getBoolean("adhkarEnabled", true) && prefs.getBoolean("adhkarAuto", true)
            if (enabled) ui.postDelayed(this, 1000L)
        }
    }
    private val bgTask = object : Runnable {
        override fun run() {
            val interval = prefs.getInt("bgInterval", 300) * 1000L
            if (prefs.getBoolean("bgAuto", false) && Style.isPreset(prefs) && System.currentTimeMillis() - lastBgChange >= interval) {
                Style.advanceNature(prefs)
                val root = findViewById<View>(R.id.sbRoot)
                val currentBackground = root.background ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
                val oldBackground = currentBackground.constantState?.newDrawable()?.mutate() ?: currentBackground
                val transition = android.graphics.drawable.TransitionDrawable(arrayOf<android.graphics.drawable.Drawable>(oldBackground, Style.background(this@StandbyActivity, prefs)))
                root.background = transition
                transition.startTransition(1200)
                lastBgChange = System.currentTimeMillis()
            }
            ui.postDelayed(this, 15_000L)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            render()
            // standby also keeps matches / prayers / weather fresh by itself
            Refresh.ensure(prefs) { ui.post { render(); MatchAlerts.schedule(this@StandbyActivity, prefs) } }
            ui.postDelayed(this, 20_000)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_standby)
        prefs = getSharedPreferences("home", MODE_PRIVATE)
        val root = findViewById<android.widget.FrameLayout>(R.id.sbRoot)
        root.background = Style.background(this, prefs)
        adhkarContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val position = prefs.getInt("adhkarPosition", 0).coerceIn(0, 2)
            val gravity = when (position) { 1 -> android.view.Gravity.CENTER; 2 -> android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL; else -> android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL }
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -2, gravity).apply {
                leftMargin = dp(48); rightMargin = dp(48)
                topMargin = if (position == 2) dp(130) else 0
                bottomMargin = if (position == 0) dp(150) else 0
            }
            alpha = prefs.getInt("adhkarOpacity", 100).coerceIn(20, 100) / 100f
        }
        root.addView(adhkarContainer)
        val f24 = prefs.getBoolean("clock24", true)
        findViewById<TextClock>(R.id.sbAmPm).visibility = if (f24) View.GONE else View.VISIBLE
        lastBgChange = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(tick)
        tick.run()
        ui.removeCallbacks(bgTask)
        ui.postDelayed(bgTask, 15_000L)
        ui.removeCallbacks(adhkarTask)
        renderAdhkar(force = true)
        ui.postDelayed(adhkarTask, 1000L)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
        ui.removeCallbacks(bgTask)
        ui.removeCallbacks(adhkarTask)
    }

    // Any key or touch leaves the standby screen
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) finish()
        return true
    }

    private fun tv(id: Int) = findViewById<TextView>(id)

    private fun conditionText(code: Int): String = getString(
        when (code) {
            0 -> R.string.wc_clear
            1, 2 -> R.string.wc_partly
            3 -> R.string.wc_cloudy
            45, 48 -> R.string.wc_fog
            in 51..67, in 80..82 -> R.string.wc_rain
            in 71..77, 85, 86 -> R.string.wc_snow
            in 95..99 -> R.string.wc_storm
            else -> R.string.wc_cloudy
        }
    )

    private fun fmtTime(ms: Long): String {
        val p = if (prefs.getBoolean("clock24", true)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(p, Locale.getDefault()).format(Date(ms))
    }

    private fun duration(mins: Int): String =
        if (mins >= 60) getString(R.string.dur_hm, mins / 60, mins % 60) else getString(R.string.dur_min, maxOf(mins, 1))

    private fun render() {
        val clock = findViewById<TextClock>(R.id.sbClock)
        ClockStyles.applyTextClockFormats(clock, prefs)
        ClockStyles.apply(this, clock, prefs.getInt("clockStyle", 0), Style.accent(prefs), big = true)
        val now = Date()
        val loc = Locale.getDefault()
        tv(R.id.sbDay).text = SimpleDateFormat("EEEE", loc).format(now)
        tv(R.id.sbDate).text = SimpleDateFormat("d MMMM yyyy", loc).format(now)
        tv(R.id.sbHijri).text = hijriText() ?: ""

        // weather
        val showW = prefs.getBoolean("showWeather", true) && prefs.contains("wtemp")
        val vis = if (showW) View.VISIBLE else View.GONE
        tv(R.id.sbCity).visibility = vis
        tv(R.id.sbTemp).visibility = vis
        tv(R.id.sbHiLo).visibility = vis
        tv(R.id.sbWeatherDetails).visibility = vis
        tv(R.id.sbForecast).visibility = if (showW && prefs.getBoolean("showWeatherForecast", true)) View.VISIBLE else View.GONE
        if (showW) {
            tv(R.id.sbCity).text = Config.city(prefs)
            tv(R.id.sbTemp).text = "${prefs.getInt("wtemp", 0)}°  " + conditionText(prefs.getInt("wcode", 3))
            tv(R.id.sbHiLo).text = if (prefs.contains("wmax") && prefs.contains("wmin"))
                getString(R.string.sb_hilo, prefs.getInt("wmax", 0), prefs.getInt("wmin", 0)) else ""
            val humidity = prefs.getInt("whumidity", -1)
            val wind = prefs.getInt("wwind", -1)
            tv(R.id.sbWeatherDetails).text = buildString {
                if (humidity >= 0) append("Humidity ").append(humidity).append("%")
                if (wind >= 0) { if (isNotEmpty()) append("  ·  "); append("Wind ").append(wind).append(" km/h") }
            }
            tv(R.id.sbForecast).text = try {
                val arr = org.json.JSONArray(prefs.getString("weatherForecast", "[]") ?: "[]")
                (1 until minOf(arr.length(), 4)).joinToString("   ") { i ->
                    val o = arr.getJSONObject(i)
                    val date = o.optString("date").takeLast(5)
                    "$date  ${o.optInt("high")}°/${o.optInt("low")}°"
                }
            } catch (_: Exception) { "" }
        }

        renderMatch()
        renderRamadan()
        renderPrayer()
        renderAdhkar()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private data class Dhikr(val text: String, val group: String, val source: String)

    private val dhikrLibrary = listOf(
        Dhikr("أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "morning", "صحيح مسلم 2723"),
        Dhikr("اللهم بك أصبحنا وبك أمسينا وبك نحيا وبك نموت وإليك النشور", "morning", "سنن الترمذي 3391"),
        Dhikr("رضيت بالله رباً، وبالإسلام ديناً، وبمحمد ﷺ نبياً", "morning", "سنن أبي داود 5072"),
        Dhikr("اللهم إني أسألك العفو والعافية في الدنيا والآخرة", "morning", "سنن أبي داود 5074"),
        Dhikr("اللهم إني أصبحت أشهدك وأشهد حملة عرشك وملائكتك وجميع خلقك أنك أنت الله لا إله إلا أنت", "morning", "سنن أبي داود 5069"),
        Dhikr("أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "evening", "صحيح مسلم 2723"),
        Dhikr("اللهم بك أمسينا وبك أصبحنا وبك نحيا وبك نموت وإليك المصير", "evening", "سنن الترمذي 3391"),
        Dhikr("أعوذ بكلمات الله التامات من شر ما خلق", "evening", "صحيح مسلم 2708"),
        Dhikr("بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", "evening", "سنن أبي داود 5088"),
        Dhikr("اللهم إني أسألك العفو والعافية في الدنيا والآخرة", "evening", "سنن أبي داود 5074"),
        Dhikr("باسمك اللهم أموت وأحيا", "sleep", "صحيح البخاري 6324"),
        Dhikr("الحمد لله الذي أحيانا بعدما أماتنا وإليه النشور", "wake", "صحيح البخاري 6312"),
        Dhikr("سبحان الله", "prayer", "صحيح مسلم 597 – من أذكار دبر الصلاة"),
        Dhikr("الحمد لله", "prayer", "صحيح مسلم 597 – من أذكار دبر الصلاة"),
        Dhikr("الله أكبر", "prayer", "صحيح مسلم 597 – من أذكار دبر الصلاة"),
        Dhikr("أستغفر الله، أستغفر الله، أستغفر الله", "prayer", "صحيح مسلم 591"),
        Dhikr("اللهم أنت السلام ومنك السلام تباركت يا ذا الجلال والإكرام", "prayer", "صحيح مسلم 591"),
        Dhikr("سبحان الله وبحمده", "general", "صحيح البخاري 6405"),
        Dhikr("سبحان الله وبحمده، سبحان الله العظيم", "general", "صحيح البخاري 6406"),
        Dhikr("لا حول ولا قوة إلا بالله", "general", "صحيح البخاري 6384"),
        Dhikr("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "general", "صحيح البخاري 6403"),
        Dhikr("أستغفر الله وأتوب إليه", "general", "صحيح البخاري 6307"),
        Dhikr("اللهم صل وسلم على نبينا محمد", "general", "ذكر عام – الصلاة على النبي"),
        Dhikr("اللهم أعني على ذكرك وشكرك وحسن عبادتك", "general", "سنن أبي داود 1522"),
        Dhikr("ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار", "quran", "سورة البقرة 201"),
        Dhikr("رب اشرح لي صدري ويسر لي أمري", "quran", "سورة طه 25–26"),
        Dhikr("ربنا لا تزغ قلوبنا بعد إذ هديتنا وهب لنا من لدنك رحمة إنك أنت الوهاب", "quran", "سورة آل عمران 8"),
        Dhikr("رب إني لما أنزلت إلي من خير فقير", "quran", "سورة القصص 24"),
        Dhikr("لا إله إلا أنت سبحانك إني كنت من الظالمين", "quran", "سورة الأنبياء 87"),
        Dhikr("رب اغفر لي ولوالدي وللمؤمنين يوم يقوم الحساب", "quran", "سورة إبراهيم 41"),
        Dhikr("اللهم إني أسألك الهدى والتقى والعفاف والغنى", "dua", "صحيح مسلم 2721"),
        Dhikr("يا مقلب القلوب ثبت قلبي على دينك", "dua", "سنن الترمذي 2140"),
        Dhikr("اللهم إني أعوذ بك من الهم والحزن والعجز والكسل", "dua", "صحيح البخاري 6369"),
        Dhikr("اللهم اغفر لي وارحمني واهدني وعافني وارزقني", "dua", "صحيح مسلم 2697"),
        Dhikr("ربنا ظلمنا أنفسنا وإن لم تغفر لنا وترحمنا لنكونن من الخاسرين", "quran", "سورة الأعراف 23"),
        Dhikr("حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم", "quran", "سورة التوبة 129")
    )

    private fun renderAdhkar(force: Boolean = false) {
        if (!::adhkarContainer.isInitialized) return
        if (!prefs.getBoolean("adhkarEnabled", true)) { adhkarContainer.visibility = View.GONE; return }
        val groups = listOf("morning", "evening", "sleep", "wake", "prayer", "general", "quran", "dua")
        val keys = mapOf("morning" to "adhkarMorning", "evening" to "adhkarEvening", "sleep" to "adhkarSleep", "wake" to "adhkarWake", "prayer" to "adhkarPrayer", "general" to "adhkarGeneral", "quran" to "adhkarQuran", "dua" to "adhkarDua")
        val selected = groups.filter { prefs.getBoolean(keys[it], it == "morning" || it == "evening" || it == "general") }
        val pool = dhikrLibrary.filter { it.group in selected }
        if (pool.isEmpty()) { adhkarContainer.visibility = View.GONE; return }
        val auto = prefs.getBoolean("adhkarAuto", true)
        val interval = prefs.getInt("adhkarInterval", 15).coerceIn(5, 3600) * 1000L
        val count = prefs.getInt("adhkarCount", 3).coerceIn(1, 10).coerceAtMost(pool.size)
        val shouldAdvance = force || lastAdhkarAt == 0L || (auto && System.currentTimeMillis() - lastAdhkarAt >= interval)
        if (shouldAdvance) {
            val chosen = if (prefs.getBoolean("adhkarSequential", false)) {
                val result = (0 until count).map { pool[(adhkarSequentialIndex + it) % pool.size] }
                adhkarSequentialIndex = (adhkarSequentialIndex + count) % pool.size
                result
            } else pool.shuffled().take(count)
            val signature = chosen.joinToString("|") { it.text }
            if (signature != lastAdhkarSignature || lastAdhkarAt == 0L) {
                lastAdhkarSignature = signature
                adhkarContainer.removeAllViews()
                chosen.forEachIndexed { index, item ->
                    val card = TextView(this).apply {
                        gravity = android.view.Gravity.CENTER
                        textDirection = View.TEXT_DIRECTION_RTL
                        setTextColor(prefs.getInt("adhkarColor", android.graphics.Color.WHITE))
                        textSize = prefs.getInt("adhkarSize", 24).toFloat().coerceIn(14f, 48f)
                        setPadding(dp(24), dp(8), dp(24), dp(8))
                        setShadowLayer(5f, 0f, 2f, 0xCC000000.toInt())
                        text = if (prefs.getBoolean("adhkarShowSource", true)) "${item.text}\n— ${item.source}" else item.text
                        alpha = 0f
                    }
                    adhkarContainer.addView(card, android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
                    card.animate().alpha(1f).setDuration(650L).setStartDelay(index * 80L).start()
                }
            }
            lastAdhkarAt = System.currentTimeMillis()
        }
        adhkarContainer.visibility = View.VISIBLE
        if (!auto) ui.removeCallbacks(adhkarTask)
    }

    // ---------- next match ----------

    private fun pickMatch(): Match? {
        val s = prefs.getString("matches", "") ?: ""
        val list = try {
            if (s.isEmpty()) emptyList() else Matches.fromJsonString(s)
        } catch (e: Exception) {
            emptyList()
        }
        val upcoming = list.filter { it.state != "post" }
        val team = (prefs.getString("team", "") ?: "").trim().lowercase()
        if (team.isNotEmpty()) {
            val mine = upcoming.firstOrNull { m ->
                listOf(m.homeName, m.awayName, m.homeAbbr, m.awayAbbr).any { it.lowercase().contains(team) }
            }
            if (mine != null) return mine
        }
        return upcoming.firstOrNull()
    }

    private fun renderMatch() {
        val visible = prefs.getBoolean("showMatches", true)
        tv(R.id.sbMatchLabel).visibility = if (visible) View.VISIBLE else View.GONE
        tv(R.id.sbMatch).visibility = if (visible) View.VISIBLE else View.GONE
        tv(R.id.sbMatchSub).visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        tv(R.id.sbMatchLabel).text = getString(R.string.sb_next_match)
        val all = try { Matches.fromJsonString(prefs.getString("matches", "") ?: "").filter { it.state != "post" } } catch (_: Exception) { emptyList() }
        val team = (prefs.getString("team", "") ?: "").trim().lowercase()
        val ordered = if (team.isEmpty()) all else all.sortedByDescending { m -> listOf(m.homeName, m.awayName, m.homeAbbr, m.awayAbbr).any { it.lowercase().contains(team) } }
        val count = prefs.getInt("matchCount", 3).coerceIn(1, 4)
        val selected = ordered.take(count)
        val m = selected.firstOrNull() ?: pickMatch()
        if (m == null) {
            // Technical HTTP/slug diagnostics stay in preferences/logs, never on the TV screen.
            tv(R.id.sbMatch).text = getString(R.string.sb_no_match)
            tv(R.id.sbMatchSub).text = ""
            return
        }
        val home = m.homeName.ifEmpty { m.homeAbbr }
        val away = m.awayName.ifEmpty { m.awayAbbr }
        tv(R.id.sbMatch).text = selected.joinToString("\n") { item ->
            val h = item.homeName.ifEmpty { item.homeAbbr }; val a = item.awayName.ifEmpty { item.awayAbbr }
            if (item.state == "in") "$h ${item.homeScore} - ${item.awayScore} $a  • LIVE" else "$h × $a"
        }
        val day = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(m.startMs))
        tv(R.id.sbMatchSub).text = if (m.state == "in") getString(R.string.m_live) + " · " + m.league
        else m.league + " · " + day + ", " + fmtTime(m.startMs)
    }

    // ---------- Hijri date and Ramadan (needs Android 7+) ----------

    private fun hijriText(): String? = try {
        if (Build.VERSION.SDK_INT < 24) null else {
            val loc = android.icu.util.ULocale("${Locale.getDefault().language}@calendar=islamic-umalqura;numbers=latn")
            val adjusted = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, prefs.getInt("hijriAdjust", 0).coerceIn(-1, 1)) }.time
            android.icu.text.SimpleDateFormat("d MMMM y G", loc).format(adjusted)
        }
    } catch (e: Throwable) {
        null
    }

    private fun renderRamadan() {
        tv(R.id.sbRamLabel).text = getString(R.string.sb_ramadan)
        try {
            if (Build.VERSION.SDK_INT < 24) throw IllegalStateException()
            val loc = android.icu.util.ULocale("en@calendar=islamic-umalqura")
            val nowCal = android.icu.util.Calendar.getInstance(loc)
            val month = nowCal.get(android.icu.util.Calendar.MONTH) // 8 = Ramadan
            if (month == 8) {
                tv(R.id.sbRam).text = getString(R.string.sb_ramadan_now)
                tv(R.id.sbRamSub).text = ""
                return
            }
            val year = nowCal.get(android.icu.util.Calendar.YEAR)
            val target = android.icu.util.Calendar.getInstance(loc)
            target.timeInMillis = nowCal.timeInMillis
            target.set(android.icu.util.Calendar.YEAR, if (month < 8) year else year + 1)
            target.set(android.icu.util.Calendar.MONTH, 8)
            target.set(android.icu.util.Calendar.DAY_OF_MONTH, 1)
            target.set(android.icu.util.Calendar.HOUR_OF_DAY, 0)
            target.set(android.icu.util.Calendar.MINUTE, 0)
            val days = Math.ceil((target.timeInMillis - nowCal.timeInMillis) / 86_400_000.0).toInt()
            val startDay = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(target.timeInMillis))
            tv(R.id.sbRam).text = getString(R.string.sb_days, days)
            tv(R.id.sbRamSub).text = getString(R.string.sb_starts, startDay)
        } catch (e: Throwable) {
            tv(R.id.sbRam).text = ""
            tv(R.id.sbRamSub).text = ""
        }
    }

    // ---------- next prayer ----------

    private fun renderPrayer() {
        tv(R.id.sbPrayLabel).text = getString(R.string.sb_next_prayer)
        val names = listOf(
            R.string.p_fajr, R.string.p_sunrise, R.string.p_dhuhr,
            R.string.p_asr, R.string.p_maghrib, R.string.p_isha
        )
        val times = (prefs.getString("prayers", "") ?: "").split(",")
        if (!prefs.getBoolean("showPrayers", true)) {
            tv(R.id.sbPray).text = ""
            tv(R.id.sbPraySub).text = ""
            tv(R.id.sbPrayerTable).text = ""
            return
        }
        if (times.size != 6) {
            val err = prefs.getString("prayerErr", "") ?: ""
            tv(R.id.sbPray).text = if (err.isNotEmpty()) getString(R.string.sb_prayer_error) else ""
            tv(R.id.sbPraySub).text = err
            tv(R.id.sbPrayerTable).text = ""
            return
        }
        try {
            val mins = times.map { val p = it.split(":"); p[0].toInt() * 60 + p[1].toInt() }
            val c = Calendar.getInstance()
            val nowM = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            var next = mins.indexOfFirst { it > nowM }
            if (next < 0) next = 0
            if (next == 1) next = 2
            val d = if (mins[next] > nowM) mins[next] - nowM else mins[next] + 24 * 60 - nowM
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, mins[next] / 60)
            cal.set(Calendar.MINUTE, mins[next] % 60)
            tv(R.id.sbPray).text = getString(names[next]) + "  " + fmtTime(cal.timeInMillis)
            tv(R.id.sbPraySub).text = getString(R.string.sb_after, duration(d))
            val prayerIndices = listOf(0, 2, 3, 4, 5)
            tv(R.id.sbPrayerTable).text = prayerIndices.joinToString("   ") { i -> getString(names[i]) + " " + fmtTime(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, mins[i] / 60); set(Calendar.MINUTE, mins[i] % 60) }.timeInMillis) }
        } catch (e: Exception) {
            tv(R.id.sbPray).text = ""
            tv(R.id.sbPraySub).text = ""
            tv(R.id.sbPrayerTable).text = ""
        }
    }
}
