package com.example.myhome

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var root: View
    private lateinit var favRv: RecyclerView
    private lateinit var allRv: RecyclerView
    private lateinit var favAdapter: AppAdapter
    /** Long-press reorder mode: hold focus and use left/right to move. */
    private var reorderPkg: String? = null

    private lateinit var allAdapter: AppAdapter
    private lateinit var clockView: TextClock
    private lateinit var weatherView: TextView
    private lateinit var prayerView: TextView
    private lateinit var hijriView: TextView
    private lateinit var focusTitle: TextView
    private lateinit var focusSub: TextView
    private lateinit var favTitle: TextView
    private lateinit var allTitle: TextView
    private lateinit var inputsTitle: TextView
    private lateinit var inputsBox: View
    private lateinit var inputsRow: LinearLayout
    private lateinit var pillsRow: LinearLayout
    private val pills = ArrayList<TextView>()

    private val ui = Handler(Looper.getMainLooper())
    private var allApps = listOf<AppItem>()
    private var langApplied = "auto"
    private var appsSig = ""
    private var firstFocusDone = false
    private var lastAccent = 0
    private var lastBgChange = 0L
    private var lastNightMode = false
    private val idleTask = Runnable {
        if (!isFinishing && prefs.getInt("idleMinutes", 5) > 0 && !isFinishing) {
            startActivity(Intent(this, StandbyActivity::class.java))
        }
    }
    private val bgTask = object : Runnable {
        override fun run() {
            val interval = prefs.getInt("bgInterval", 300) * 1000L
            if (prefs.getBoolean("bgAuto", false) && Style.isPreset(prefs) &&
                System.currentTimeMillis() - lastBgChange >= interval) {
                Style.advanceNature(prefs)
                lastBgChange = System.currentTimeMillis()
                applyStyle(true)
            }
            ui.postDelayed(this, 15_000L)
        }
    }

    private val prayerKeys = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    private val prayerNames = listOf(
        R.string.p_fajr, R.string.p_sunrise, R.string.p_dhuhr,
        R.string.p_asr, R.string.p_maghrib, R.string.p_isha
    )

    // Streaming apps that are put in Favorites the first time, if they are installed
    private val defaultFavs = listOf(
        "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.apple.atve.androidtv.appletv", "com.google.android.youtube.tvmusic",
        "com.spotify.tv.android", "com.android.vending"
    )

    private val ticker = object : Runnable {
        override fun run() {
            renderNextPrayer()
            renderHijriDate()
            val nightNow = Style.isNight(prefs)
            if (nightNow != lastNightMode) { lastNightMode = nightNow; applyStyle(true) }
            Refresh.ensure(prefs) {
                ui.post {
                    weatherView.text = weatherSummary()
                    renderNextPrayer()
                    MatchAlerts.schedule(this@MainActivity, prefs)
                }
            }
            ui.postDelayed(this, 60_000)
        }
    }

    override fun attachBaseContext(base: Context) {
        langApplied = Lang.saved(base)
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("home", MODE_PRIVATE)
        root = findViewById(R.id.root)
        clockView = findViewById(R.id.clock)
        weatherView = findViewById(R.id.weather)
        prayerView = findViewById(R.id.nextPrayer)
        hijriView = findViewById(R.id.hijriDate)
        focusTitle = findViewById(R.id.focusTitle)
        focusSub = findViewById(R.id.focusSub)
        favTitle = findViewById(R.id.favTitle)
        allTitle = findViewById(R.id.allTitle)
        inputsTitle = findViewById(R.id.inputsTitle)
        inputsBox = findViewById(R.id.inputsBox)
        inputsRow = findViewById(R.id.inputsRow)
        pillsRow = findViewById(R.id.pillsRow)
        favRv = findViewById(R.id.favRv)
        allRv = findViewById(R.id.allRv)

        favAdapter = AppAdapter({ launch(it) }, { enterReorder(it) })
        favAdapter.tileDp = prefs.getInt("appIconSize", 96).coerceIn(64, 136)
        favAdapter.onFocus = { item, i, n -> showFocus(item.label, R.string.shelf_favs, i, n) }
        allAdapter = AppAdapter({ launch(it) }, { showAppMenu(it) })
        allAdapter.tileDp = (prefs.getInt("appIconSize", 96) - 16).coerceIn(56, 120)
        allAdapter.onFocus = { item, i, n -> showFocus(item.label, R.string.shelf_apps, i, n) }
        setupShelf(favRv, favAdapter)
        setupShelf(allRv, allAdapter)

        addPill(getString(R.string.p_screen_settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }
        addPill(getString(R.string.p_arrange)) { chooseFavorites() }
        addPill(getString(R.string.p_standby)) { startActivity(Intent(this, StandbyActivity::class.java)) }
        addPill("📺  " + getString(R.string.shelf_inputs)) { showInputsDialog() }
        addPill("📶  " + getString(R.string.q_wifi)) { openSystem(Settings.ACTION_WIFI_SETTINGS) }
        addPill("⚙️  " + getString(R.string.q_tvsettings)) { openSystem(Settings.ACTION_SETTINGS) }

        focusTitle.text = getString(R.string.app_name)
    }

    private fun setupShelf(rv: RecyclerView, ad: AppAdapter) {
        rv.layoutManager = CenterLayoutManager(this)
        rv.adapter = ad
        rv.itemAnimator = null
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(10)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getString("lang", "auto") != langApplied) {
            recreate()
            return
        }
        lastNightMode = Style.isNight(prefs)
        applyStyle()
        loadApps()
        buildInputs()
        weatherView.text = weatherSummary()
        val showW = prefs.getBoolean("showWeather", true)
        val showP = prefs.getBoolean("showPrayers", true)
        if (showW && System.currentTimeMillis() - prefs.getLong("weatherAt", 0) > 30 * 60 * 1000) refreshWeather()
        if (showP && prefs.getString("prayerDate", "") != today()) refreshPrayers()
        maybeRefreshMatches()
        if (prefs.getBoolean("notifyMatch", false) && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 220)
        }
        MatchAlerts.schedule(this, prefs)
        ui.removeCallbacks(ticker)
        ticker.run()
        lastBgChange = System.currentTimeMillis()
        ui.removeCallbacks(bgTask)
        ui.postDelayed(bgTask, 15_000L)
        resetIdleTimer()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
        ui.removeCallbacks(bgTask)
        ui.removeCallbacks(idleTask)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) resetIdleTimer()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) resetIdleTimer()
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) resetIdleTimer()
    }

    private fun resetIdleTimer() {
        if (!::prefs.isInitialized || isFinishing) return
        ui.removeCallbacks(idleTask)
        val minutes = prefs.getInt("idleMinutes", 5)
        if (minutes > 0 && hasWindowFocus()) ui.postDelayed(idleTask, minutes * 60_000L)
    }

    // Home pressed again: back to the first favourite
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        focusFirst(100)
    }

    override fun onBackPressed() {
        if (!Lang.isTv(this)) super.onBackPressed() // phone preview: Back leaves the app
    }

    private fun focusFirst(delay: Long = 80) {
        if (favAdapter.size() > 0) focusPosition(favRv, favAdapter.startPosition(0), delay)
        else if (allAdapter.size() > 0) focusPosition(allRv, allAdapter.startPosition(0), delay)
    }

    private fun focusPosition(rv: RecyclerView, pos: Int, delay: Long = 80) {
        rv.scrollToPosition(pos)
        rv.postDelayed({ rv.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus() }, delay)
    }

    // ---------- Look and feel ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun applyStyle(crossfade: Boolean = false) {
        val accent = Style.accent(prefs)
        val nextBackground = Style.background(this, prefs)
        val currentBackground = root.background
        if (crossfade && currentBackground != null) {
            val oldBackground = currentBackground.constantState?.newDrawable()?.mutate() ?: currentBackground
            val transition = android.graphics.drawable.TransitionDrawable(arrayOf<android.graphics.drawable.Drawable>(oldBackground, nextBackground))
            root.background = transition
            transition.startTransition(900)
        } else root.background = nextBackground
        for (p in pills) p.background = Style.card(this, accent)
        favAdapter.accent = accent
        allAdapter.accent = accent
        if (accent != lastAccent) {
            lastAccent = accent
            favAdapter.refresh()
            allAdapter.refresh()
        }
        weatherView.visibility = if (prefs.getBoolean("showWeather", true)) View.VISIBLE else View.GONE
        prayerView.visibility = if (prefs.getBoolean("showPrayers", true)) View.VISIBLE else View.GONE
        ClockStyles.applyTextClockFormats(clockView, prefs)
        clockView.textSize = when (prefs.getInt("clockSize", 1)) { 0 -> 26f; 2 -> 44f; else -> 34f }
        ClockStyles.apply(this, clockView, prefs.getInt("clockStyle", 0), accent, big = false)
        favAdapter.tileDp = prefs.getInt("appIconSize", 96).coerceIn(64, 136)
        allAdapter.tileDp = (prefs.getInt("appIconSize", 96) - 16).coerceIn(56, 120)
        favAdapter.refresh(); allAdapter.refresh()
        renderHijriDate()
    }

    private fun renderHijriDate() {
        hijriView.text = try {
            if (android.os.Build.VERSION.SDK_INT < 24) "" else {
                val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, prefs.getInt("hijriAdjust", 0).coerceIn(-1, 1)) }.time
                val loc = android.icu.util.ULocale("${Locale.getDefault().language}@calendar=islamic-umalqura")
                android.icu.text.SimpleDateFormat("d MMMM y G", loc).format(date)
            }
        } catch (_: Throwable) { "" }
    }

    private fun showFocus(title: String, shelfRes: Int, idx: Int, n: Int) {
        focusTitle.text = title
        focusSub.text = getString(R.string.of_fmt, getString(shelfRes), idx + 1, n)
    }

    private fun addPill(label: String, onClick: () -> Unit) {
        val t = TextView(this)
        t.text = label
        t.textSize = 14f
        t.setTextColor(Color.WHITE)
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        t.isFocusable = true
        t.isClickable = true
        t.setOnClickListener { onClick() }
        t.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.06f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(150).start()
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(8)
        pillsRow.addView(t, lp)
        pills.add(t)
    }

    private fun openSystem(action: String) {
        for (a in listOf(action, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                startActivity(Intent(a))
                return
            } catch (e: Exception) {
            }
        }
    }

    // ---------- Inputs (HDMI etc.) ----------

    // HDMI/AV inputs are exposed from a dedicated top-bar action, not as a home shelf.
    private fun showInputsDialog() {
        val list = try {
            (getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager).tvInputList.filter { it.isPassthroughInput }
        } catch (_: Exception) { emptyList() }
        if (list.isEmpty()) {
            AlertDialog.Builder(this).setTitle(R.string.shelf_inputs)
                .setMessage(R.string.inputs_unavailable).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val labels = list.map { info -> (info.loadCustomLabel(this) ?: info.loadLabel(this)).toString() }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.shelf_inputs).setItems(labels) { _, which ->
            val info = list[which]
            try { startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))) }
            catch (_: Exception) { openSystem(Settings.ACTION_SETTINGS) }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun buildInputs() {
        inputsRow.removeAllViews()
        // Inputs are intentionally moved to the top-bar dialog to keep the home layout clean.
        val list = try {
            (getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager).tvInputList.filter { it.isPassthroughInput }
        } catch (e: Exception) {
            emptyList()
        }
        inputsBox.visibility = View.GONE
        inputsTitle.text = getString(R.string.shelf_inputs) + "  " + list.size
        val accent = Style.accent(prefs)
        val textColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(0xFF111111.toInt(), Color.WHITE)
        )
        list.forEachIndexed { i, info ->
            val label = (info.loadCustomLabel(this) ?: info.loadLabel(this)).toString()
            val t = TextView(this)
            t.text = label
            t.textSize = 15f
            t.setTextColor(textColors)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(24), dp(12), dp(24), dp(12))
            t.isFocusable = true
            t.isClickable = true
            t.background = Style.tile(this, accent)
            t.setOnClickListener {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.id))
                    )
                } catch (e: Exception) {
                }
            }
            t.setOnFocusChangeListener { v, focused ->
                val s = if (focused) 1.08f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(150).start()
                if (focused) showFocus(label, R.string.shelf_inputs, i, list.size)
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(10)
            inputsRow.addView(t, lp)
        }
    }

    // ---------- Apps and favourites ----------

    // Icons are read on a background thread; the shelves are only rebuilt when the app list changed
    private fun loadApps() {
        Thread {
            try {
                val pm = packageManager
                val found = LinkedHashMap<String, AppItem>()
                for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                    val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    for (ri in pm.queryIntentActivities(i, 0)) {
                        val ai = ri.activityInfo
                        if (ai.packageName == packageName || found.containsKey(ai.packageName)) continue
                        val appInfo = ai.applicationInfo
                        val systemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        val updatedSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                        val allowedPreloads = setOf(
                            "com.google.android.youtube.tv", "com.google.android.youtube",
                            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
                            "com.disney.disneyplus", "com.android.vending",
                            "com.spotify.tv.android", "com.apple.atve.androidtv.appletv"
                        )
                        // Launcher activities only; hide internal system tools/services. Keep popular TV apps even when preinstalled.
                        if (systemApp && !updatedSystem && ai.packageName !in allowedPreloads) continue
                        val hasIcon = ai.icon != 0 || appInfo.icon != 0
                        val icon = if (hasIcon) ai.loadIcon(pm) else (ai.loadBanner(pm) ?: ai.loadIcon(pm))
                        found[ai.packageName] = AppItem(
                            ri.loadLabel(pm).toString(), ai.packageName,
                            ComponentName(ai.packageName, ai.name), icon
                        )
                    }
                }
                val list = found.values.toList()
                val sig = list.joinToString(",") { it.pkg }
                ui.post {
                    if (sig != appsSig) {
                        appsSig = sig
                        allApps = list
                        applyShelves()
                    }
                    if (!firstFocusDone && list.isNotEmpty()) {
                        firstFocusDone = true
                        focusFirst()
                    }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun launch(a: AppItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).setComponent(a.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
        }
    }

    private fun hiddenSet(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()

    private fun favPkgs(): List<String> {
        val saved = prefs.getString("favs", null)
        if (saved != null) return saved.split(",").filter { it.isNotEmpty() }
        if (allApps.isEmpty()) return emptyList()
        val installed = allApps.map { it.pkg }.toSet()
        val picks = ArrayList<String>()
        for (p in defaultFavs) if (p in installed && picks.size < 8) picks.add(p)
        if (picks.size < 4) {
            for (a in allApps.sortedBy { it.label.lowercase() }) {
                if (a.pkg !in picks && picks.size < 8) picks.add(a.pkg)
            }
        }
        prefs.edit().putString("favs", picks.joinToString(",")).apply()
        return picks
    }

    private fun applyShelves() {
        val byPkg = allApps.associateBy { it.pkg }
        val hidden = hiddenSet()
        val visible = allApps.filter { it.pkg !in hidden }.sortedBy { it.label.lowercase() }
        val favItems = favPkgs().filter { it !in hidden }.mapNotNull { byPkg[it] }
        favAdapter.submit(favItems)
        favRv.scrollToPosition(favAdapter.startPosition(0))
        allAdapter.submit(visible)
        allRv.scrollToPosition(allAdapter.startPosition(0))
        favTitle.text = getString(R.string.shelf_favs) + "  " + favItems.size
        favTitle.visibility = if (favItems.isEmpty()) View.GONE else View.VISIBLE
        favRv.visibility = if (favItems.isEmpty()) View.GONE else View.VISIBLE
        allTitle.text = getString(R.string.shelf_apps) + "  " + visible.size
    }

    private fun setFavs(list: List<String>, focusPkg: String? = null) {
        prefs.edit().putString("favs", list.joinToString(",")).apply()
        applyShelves()
        if (focusPkg != null) {
            val pos = favAdapter.positionOf(focusPkg)
            if (pos >= 0) focusPosition(favRv, pos)
        }
    }

    private fun moveFav(pkg: String, delta: Int) {
        val favs = favPkgs().toMutableList()
        val i = favs.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j < 0 || j >= favs.size) return
        val tmp = favs[i]
        favs[i] = favs[j]
        favs[j] = tmp
        setFavs(favs, pkg)
    }

    private fun hide(pkg: String) {
        val s = HashSet(hiddenSet())
        s.add(pkg)
        prefs.edit().putStringSet("hidden", s).apply()
        applyShelves()
    }


    private fun enterReorder(a: AppItem) {
        val favs = favPkgs()
        if (a.pkg !in favs) {
            // Not a favourite: fall back to the regular menu (add / hide).
            showAppMenu(a)
            return
        }
        reorderPkg = a.pkg
        Toast.makeText(this, getString(R.string.reorder_hint), Toast.LENGTH_SHORT).show()
        // Visual: scale up the focused tile slightly is enough; focus stays on item.
        val pos = favAdapter.positionOf(a.pkg)
        if (pos >= 0) focusPosition(favRv, pos)
    }

    private fun exitReorder() {
        reorderPkg = null
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val pkg = reorderPkg
        if (pkg != null) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    moveFav(pkg, if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_ESCAPE -> {
                    exitReorder()
                    Toast.makeText(this, getString(R.string.reorder_done), Toast.LENGTH_SHORT).show()
                    return true
                }
            }
            // Block other navigation while reordering so focus doesn't leave the shelf accidentally.
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showAppMenu(a: AppItem) {

        val favs = favPkgs()
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (a.pkg in favs) {
            labels.add(getString(R.string.m_fav_remove))
            actions.add { setFavs(favs - a.pkg) }
            labels.add(getString(R.string.m_earlier))
            actions.add { moveFav(a.pkg, -1) }
            labels.add(getString(R.string.m_later))
            actions.add { moveFav(a.pkg, 1) }
        } else {
            labels.add(getString(R.string.m_fav_add))
            actions.add { setFavs(favs + a.pkg, a.pkg) }
        }
        labels.add(getString(R.string.m_hide))
        actions.add { hide(a.pkg) }
        AlertDialog.Builder(this).setTitle(a.label)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    // "Arrange favourites": tick the apps that should appear in the Favorites shelf
    private fun chooseFavorites() {
        val hidden = hiddenSet()
        val apps = allApps.filter { it.pkg !in hidden }.sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) return
        val favs = favPkgs()
        val checked = BooleanArray(apps.size) { apps[it].pkg in favs }
        AlertDialog.Builder(this).setTitle(R.string.p_arrange)
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(R.string.ok) { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.pkg }
                val kept = favs.filter { it in chosen }
                setFavs(kept + chosen.filter { it !in kept })
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- Network ----------

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌦️"
        85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    private fun weatherSummary(): String {
        val cached = prefs.getString("weather", "") ?: ""
        if (cached.isNotBlank()) {
            val max = prefs.getInt("wmax", Int.MIN_VALUE)
            val min = prefs.getInt("wmin", Int.MIN_VALUE)
            val range = if (max != Int.MIN_VALUE && min != Int.MIN_VALUE) "  ↑$max° ↓$min°" else ""
            return cached + range
        }
        val err = prefs.getString("weatherErr", "") ?: ""
        return if (err.isNotBlank()) getString(R.string.weather_unavailable) else getString(R.string.weather_loading)
    }

    private fun refreshWeather() {
        Refresh.weather(prefs) { ui.post { weatherView.text = weatherSummary() } }
    }

    private fun refreshPrayers() {
        Refresh.prayers(prefs) { ui.post { renderNextPrayer() } }
    }

    // The standby screen reads the cached match list, so keep it fresh
    private fun maybeRefreshMatches() {
        Refresh.matchesIfStale(prefs) { }
    }

    // ---------- Next prayer (header) ----------

    private fun fmtTime(hhmm: String): String {
        if (prefs.getBoolean("clock24", true)) return hhmm
        return try {
            val p = hhmm.split(":")
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, p[0].toInt())
            c.set(Calendar.MINUTE, p[1].toInt())
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(c.time)
        } catch (e: Exception) {
            hhmm
        }
    }

    private fun renderNextPrayer() {
        val times = (prefs.getString("prayers", "") ?: "").split(",")
        if (times.size != 6) return
        val mins: List<Int>
        try {
            mins = times.map { val p = it.split(":"); p[0].toInt() * 60 + p[1].toInt() }
        } catch (e: Exception) {
            return
        }
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        var next = mins.indexOfFirst { it > now }
        if (next < 0) next = 0
        if (next == 1) next = 2 // skip sunrise as "next prayer"
        prayerView.text = getString(prayerNames[next]) + "  " + fmtTime(times[next])
    }
}
