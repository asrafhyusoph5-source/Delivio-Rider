package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.util.CrashReportEntry
import com.example.util.CrashReportStore

/**
 * Isolated crash report screen running in android:process=":crash".
 * Uses plain Android Views with zero dependency on DelivioApplication, Compose, Hilt, or Firebase.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestStored = CrashReportStore.getReports(this).firstOrNull()
        val entry = CrashReportEntry(
            timestampMillis = intent.getLongExtra(EXTRA_TIMESTAMP, latestStored?.timestampMillis ?: System.currentTimeMillis()),
            appVersion = intent.getStringExtra(EXTRA_APP_VERSION) ?: latestStored?.appVersion ?: CrashReportStore.getAppVersion(this),
            threadName = intent.getStringExtra(EXTRA_THREAD_NAME) ?: latestStored?.threadName ?: "main",
            exceptionType = intent.getStringExtra(EXTRA_EXCEPTION_TYPE) ?: latestStored?.exceptionType ?: "Unexpected Error",
            exceptionMessage = intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE) ?: latestStored?.exceptionMessage ?: "",
            stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: latestStored?.stackTrace ?: "No stack trace captured."
        )

        val fullReport = CrashReportStore.formatFullReport(entry)
        val previewStackTrace = CrashReportStore.firstLines(entry.stackTrace, CrashReportStore.MAX_PREVIEW_LINES)

        val pad16 = dp(16)
        val pad12 = dp(12)
        val pad8 = dp(8)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
            setPadding(pad16, dp(24), pad16, pad16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(this).apply {
            text = "The app closed unexpectedly"
            setTextColor(Color.parseColor("#111827"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(titleView)

        val metaView = TextView(this).apply {
            text = "Version ${entry.appVersion} • Thread: ${entry.threadName} • ${CrashReportStore.formatTimestamp(entry.timestampMillis)}"
            setTextColor(Color.parseColor("#4B5563"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, pad12)
        }
        root.addView(metaView)

        val exceptionHeader = if (entry.exceptionMessage.isNotBlank()) {
            "${entry.exceptionType}: ${entry.exceptionMessage}"
        } else {
            entry.exceptionType
        }
        val exceptionView = TextView(this).apply {
            text = exceptionHeader
            setTextColor(Color.parseColor("#B91C1C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, pad12)
        }
        root.addView(exceptionView)

        val traceBoxBg = GradientDrawable().apply {
            setColor(Color.parseColor("#F3F4F6"))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#D1D5DB"))
        }

        val traceVerticalScroll = ScrollView(this).apply {
            background = traceBoxBg
            setPadding(pad12, pad12, pad12, pad12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val traceHorizontalScroll = HorizontalScrollView(this)
        val traceTextView = TextView(this).apply {
            text = previewStackTrace
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#1F2937"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextIsSelectable(true)
        }
        traceHorizontalScroll.addView(traceTextView)
        traceVerticalScroll.addView(traceHorizontalScroll)
        root.addView(traceVerticalScroll)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, pad12, 0, pad8)
        }

        val copyBtn = Button(this).apply {
            text = "Copy report"
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            setTextColor(Color.parseColor("#111827"))
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("Crash Report", fullReport))
                Toast.makeText(this@CrashReportActivity, "Crash report copied", Toast.LENGTH_SHORT).show()
            }
        }

        val shareBtn = Button(this).apply {
            text = "Share report"
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            setTextColor(Color.parseColor("#111827"))
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Delivio Driver Crash Report")
                    putExtra(Intent.EXTRA_TEXT, fullReport)
                }
                startActivity(Intent.createChooser(shareIntent, "Share crash report"))
            }
        }

        actionsRow.addView(copyBtn)
        actionsRow.addView(shareBtn)
        root.addView(actionsRow)

        val restartBtn = Button(this).apply {
            text = "Restart app"
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setTextColor(Color.parseColor("#111827"))
            setTypeface(null, Typeface.BOLD)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
                finish()
                Process.killProcess(Process.myPid())
            }
        }
        root.addView(restartBtn)

        setContentView(root)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_APP_VERSION = "extra_app_version"
        const val EXTRA_THREAD_NAME = "extra_thread_name"
        const val EXTRA_EXCEPTION_TYPE = "extra_exception_type"
        const val EXTRA_EXCEPTION_MESSAGE = "extra_exception_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }
}
