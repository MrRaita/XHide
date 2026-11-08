package org.lsposed.hidexposed

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import org.lsposed.hidexposed.core.AppDiagnostics
import org.lsposed.hidexposed.data.ConfigStore
import org.lsposed.hidexposed.ui.MainActivity
import java.io.File
import kotlin.system.exitProcess

class XHideApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.log("XHideApp", "Application started")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppDiagnostics.log("CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}")
                val crashReport = AppDiagnostics.collectDeviceInfoMarkdown(
                    isHookActive = false,
                    exception = throwable,
                    userNotes = "Auto-captured crash on thread ${thread.name}"
                )
                val crashFile = File(filesDir, "last_crash.txt")
                crashFile.writeText(crashReport)
                crashFile.setReadable(false, false)
                crashFile.setReadable(true, true)
                crashFile.setWritable(false, false)
                crashFile.setWritable(true, true)

                // Try to restart MainActivity with crash alert
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("has_crash_report", true)
                }
                startActivity(intent)
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
