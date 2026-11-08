package org.lsposed.hidexposed.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import org.lsposed.hidexposed.BuildConfig
import org.lsposed.hidexposed.data.ConfigStore
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object AppDiagnostics {
    const val GITHUB_REPO_URL = "https://github.com/everyoneexe/XHide"
    const val GITHUB_ISSUES_URL = "$GITHUB_REPO_URL/issues/new"

    private const val MAX_LOG_ENTRIES = 60
    private val logBuffer = ConcurrentLinkedQueue<String>()

    fun log(tag: String, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$time][$tag] $message"
        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.poll()
        }
    }

    fun getRecentLogs(): String {
        return logBuffer.joinToString("\n")
    }

    fun collectDeviceInfoMarkdown(
        isHookActive: Boolean,
        activeUserIds: List<Int> = emptyList(),
        exception: Throwable? = null,
        userNotes: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("### Device & System Information\n")
        sb.append("- **Device:** ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("- **Android Version:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("- **ROM Build:** ${Build.DISPLAY}\n")
        sb.append("- **Fingerprint:** `${Build.FINGERPRINT}`\n")
        sb.append("- **XHide Version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        sb.append("- **Hook Status:** ${if (isHookActive) "ACTIVE (LauncherAppsService)" else "INACTIVE / NOT FOUND"}\n")
        if (activeUserIds.isNotEmpty()) {
            sb.append("- **Active User Profiles:** ${activeUserIds.joinToString(", ") { "User $it" }}\n")
        }
        sb.append("\n")

        if (!userNotes.isNullOrBlank()) {
            sb.append("### User Description\n")
            sb.append("$userNotes\n\n")
        }

        if (exception != null) {
            sb.append("### Crash / Exception Stacktrace\n")
            sb.append("```java\n")
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
            sb.append("```\n\n")
        }

        val logs = getRecentLogs()
        if (logs.isNotBlank()) {
            sb.append("### Diagnostic Logs\n")
            sb.append("```text\n")
            sb.append(logs)
            sb.append("\n```\n")
        }

        return sb.toString()
    }

    fun openGitHubIssue(
        context: Context,
        isHookActive: Boolean,
        activeUserIds: List<Int> = emptyList(),
        exception: Throwable? = null,
        title: String? = null,
        userNotes: String? = null
    ) {
        val issueTitle = title ?: if (!isHookActive) {
            "[Bug] Hook not detected on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        } else if (exception != null) {
            "[Crash] ${exception.javaClass.simpleName}: ${exception.message ?: "Unknown"}"
        } else {
            "[Report] Issue on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        }

        val markdownBody = collectDeviceInfoMarkdown(
            isHookActive = isHookActive,
            activeUserIds = activeUserIds,
            exception = exception,
            userNotes = userNotes
        )

        // Copy markdown body to clipboard so user can easily paste if browser URL query is truncated
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("xhide_issue_report", markdownBody))
            Toast.makeText(context, "Teşhis raporu panoya kopyalandı", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }

        try {
            val encodedTitle = URLEncoder.encode(issueTitle, "UTF-8")
            val encodedBody = URLEncoder.encode(markdownBody, "UTF-8")
            val url = "$GITHUB_ISSUES_URL?title=$encodedTitle&body=$encodedBody"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            // Fallback to plain repository issues page
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {
            }
        }
    }

    fun openGitHubRepo(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            Toast.makeText(context, GITHUB_REPO_URL, Toast.LENGTH_LONG).show()
        }
    }
}
