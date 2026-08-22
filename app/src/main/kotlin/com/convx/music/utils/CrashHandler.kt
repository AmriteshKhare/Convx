/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.convx.music.BuildConfig
import com.convx.music.ui.screens.CrashActivity
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // media3's MediaNotificationManager calls the platform's (final,
        // un-overridable) Service.startForeground() directly and
        // asynchronously — a posted Handler callback, not on a frame app
        // code controls — so this global handler is the only remaining
        // place to catch a promotion the OS refused (app backgrounded past
        // its foreground-service grace window; confirmed via a real device
        // crash log, Samsung/Android 16, app 1.5.1). The OS already refused
        // the promotion; there's nothing to recover except not killing the
        // process over it — the notification just stays unpromoted until
        // playback next starts in the foreground normally.
        if (Build.VERSION.SDK_INT >= 31 &&
            throwable is android.app.ForegroundServiceStartNotAllowedException
        ) {
            Timber.tag("CrashHandler").w(throwable, "Foreground service start refused by platform — ignoring, not crashing")
            return
        }
        // Backup restore closes the shared Room database, overwrites its file, then hard-exits
        // the process itself (BackupRestoreViewModel.restore(): database.close() -> exitProcess(0)).
        // In that window any other still-alive screen/ViewModel whose Flow collector happens to
        // fire a query against the now-closed connection throws exactly this -- "Error code: 21,
        // message: connection is closed" (confirmed via a real device crash log, Xiaomi/Android 16,
        // app 1.5.2). The process is already about to be killed intentionally right after; crashing
        // here only replaces that clean restart with a scary crash screen for a query result nothing
        // is even waiting on anymore.
        if (databaseCloseExpected && throwable is android.database.SQLException) {
            Timber.tag("CrashHandler").w(throwable, "SQLException during expected restore DB close — ignoring, process is already exiting")
            return
        }
        try {
            val crashLog = buildCrashLog(throwable)
            Timber.e(throwable, "App crashed")
            
            // Launch crash activity
            val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)
            
            // Kill the current process
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            // If we fail to handle the crash, fall back to default handler
            Timber.e(e, "Error handling crash")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("Convx Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"

        /** Set by [com.convx.music.viewmodels.BackupRestoreViewModel] right before it closes
         *  the shared database ahead of an intentional process exit. See the guard above. */
        @Volatile
        var databaseCloseExpected: Boolean = false

        fun install(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.d("CrashHandler installed")
        }
    }
}
