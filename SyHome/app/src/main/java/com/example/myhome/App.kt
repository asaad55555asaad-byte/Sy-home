package com.example.myhome

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/** Shows the error on screen instead of silently closing, so it can be reported and fixed. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isCrashProcess()) return
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val i = Intent(this, CrashActivity::class.java)
                    .putExtra("trace", sw.toString().take(8000))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(i)
                Thread.sleep(300)
            } catch (x: Throwable) {
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun isCrashProcess(): Boolean = try {
        Build.VERSION.SDK_INT >= 28 && (getProcessName() ?: "").endsWith(":crash")
    } catch (e: Throwable) {
        false
    }
}
