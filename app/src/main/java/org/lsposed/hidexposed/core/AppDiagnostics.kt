package org.lsposed.hidexposed.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import org.lsposed.hidexposed.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLEncoder

object AppDiagnostics {
    const val GITHUB_REPO_URL = "https://github.com/everyoneexe/XHide"
    const val GITHUB_ISSUES_URL = "$GITHUB_REPO_URL/issues/new"

    fun log(@Suppress("UNUSED_PARAMETER") tag: String, @Suppress("UNUSED_PARAMETER") message: String) {
        // No-op in production to avoid buffering internal runtime logs in memory
    }

    fun collectDeviceInfoMarkdown(
        isHookActive: Boolean,
        exception: Throwable? = null,
        userNotes: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("### Environment Information\n")
        sb.append("- **Device:** ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("- **Android Version:** ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("- **XHide Version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        sb.append("- **Hook Status:** ${if (isHookActive) "ACTIVE (LauncherAppsService)" else "INACTIVE / NOT FOUND"}\n\n")

        if (!userNotes.isNullOrBlank()) {
            sb.append("### Description\n")
            sb.append("$userNotes\n\n")
        }

        if (exception != null) {
            sb.append("### Stacktrace\n")
            sb.append("```java\n")
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString())
            sb.append("```\n\n")
        }

        return sb.toString()
    }

    fun openGitHubIssue(
        context: Context,
        isHookActive: Boolean,
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

        val fullReport = collectDeviceInfoMarkdown(
            isHookActive = isHookActive,
            exception = exception,
            userNotes = userNotes
        )

        // Only place non-sensitive 4-line environment header in URL query parameters;
        // full crash stacktraces stay strictly in clipboard for user review before pasting.
        val urlSafeBody = collectDeviceInfoMarkdown(
            isHookActive = isHookActive,
            exception = null,
            userNotes = if (exception != null || !userNotes.isNullOrBlank()) {
                "*(Full report copied to clipboard — please review and paste here)*"
            } else {
                null
            }
        )

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = ClipData.newPlainText("xhide_issue_report", fullReport).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = android.os.PersistableBundle().apply {
                        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
            }
            clipboard?.setPrimaryClip(clipData)
            val toastMsg = org.lsposed.hidexposed.ui.Localization.resolveStrings(
                org.lsposed.hidexposed.data.ConfigStore.appLanguage
            ).diagnosticReportCopiedToast
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }

        try {
            val encodedTitle = URLEncoder.encode(issueTitle, "UTF-8")
            val encodedBody = URLEncoder.encode(urlSafeBody, "UTF-8")
            val url = "$GITHUB_ISSUES_URL?title=$encodedTitle&body=$encodedBody"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
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
