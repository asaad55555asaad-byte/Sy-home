package com.example.myhome

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Local match reminders. The cached ESPN schedule is the source of truth for rescheduling. */
object MatchAlerts {
    const val CHANNEL = "match_reminders"
    const val ACTION = "com.example.myhome.MATCH_REMINDER"

    fun schedule(context: Context, prefs: android.content.SharedPreferences) {
        if (!prefs.getBoolean("notifyMatch", false)) return
        val raw = prefs.getString("matches", "") ?: return
        val list = try { Matches.fromJsonString(raw) } catch (_: Exception) { return }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val before = prefs.getInt("notifyBefore", 15).coerceIn(15, 60)
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        // Cancel the previous schedule first so changed fixtures cannot leave stale alarms behind.
        val previous = prefs.getStringSet("scheduledMatchIds", emptySet())?.toSet() ?: emptySet()
        previous.forEach { rawId ->
            val id = rawId.toIntOrNull() ?: return@forEach
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, MatchReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_NO_CREATE or immutable)
            if (pi != null) { alarm.cancel(pi); pi.cancel() }
        }
        val now = System.currentTimeMillis()
        val scheduled = HashSet<String>()
        list.filter { it.state == "pre" && it.startMs - before * 60_000L > now }
            .forEach { match ->
                val id = matchId(match)
                val intent = Intent(context, MatchReminderReceiver::class.java).setAction(ACTION)
                    .putExtra("id", id).putExtra("home", match.homeName).putExtra("away", match.awayName)
                    .putExtra("league", match.league)
                val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)
                val triggerAt = match.startMs - before * 60_000L
                if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                else alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                scheduled.add(id.toString())
            }
        prefs.edit().putStringSet("scheduledMatchIds", scheduled).apply()
    }

    fun cancel(context: Context, prefs: android.content.SharedPreferences) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val ids = prefs.getStringSet("scheduledMatchIds", emptySet())?.toSet() ?: emptySet()
        ids.forEach { rawId ->
            val id = rawId.toIntOrNull() ?: return@forEach
            val intent = Intent(context, MatchReminderReceiver::class.java).setAction(ACTION)
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_NO_CREATE or immutable)
            if (pi != null) { alarm.cancel(pi); pi.cancel() }
        }
        prefs.edit().remove("scheduledMatchIds").apply()
    }

    private fun matchId(m: Match): Int = ("${m.startMs}:${m.homeAbbr}:${m.awayAbbr}").hashCode() and 0x7fffffff
}

class MatchReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(MatchAlerts.CHANNEL, "Match reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val home = intent.getStringExtra("home").orEmpty()
        val away = intent.getStringExtra("away").orEmpty()
        val league = intent.getStringExtra("league").orEmpty()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val content = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or immutable) }
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, MatchAlerts.CHANNEL)
            else android.app.Notification.Builder(context)
        builder.setSmallIcon(R.drawable.ic_banner).setContentTitle("$home × $away")
            .setContentText(league).setAutoCancel(true)
        if (content != null) builder.setContentIntent(content)
        try { manager.notify(intent.getIntExtra("id", System.currentTimeMillis().toInt()), builder.build()) }
        catch (_: SecurityException) { /* Notification permission may have been denied by the user. */ }
    }
}

class MatchBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
            MatchAlerts.schedule(context, prefs)
        }
    }
}
