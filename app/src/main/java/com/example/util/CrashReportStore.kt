package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CrashReportEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val threadName: String = "main",
    val exceptionType: String = "",
    val exceptionMessage: String = "",
    val stackTrace: String = "",
    val documentPath: String = "",
    val isNonFatal: Boolean = false
)

object CrashReportStore {
    private const val FILE_NAME = "crash_reports.json"
    const val MAX_REPORTS = 5
    const val MAX_PREVIEW_LINES = 40

    private val lock = Any()

    fun getAppVersion(context: Context?): String {
        if (context == null) return "1.0"
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val name = info.versionName ?: "1.0"
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "$name ($code)"
        } catch (_: Throwable) {
            "1.0"
        }
    }

    fun throwableToStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    fun firstLines(stackTrace: String, maxLines: Int = MAX_PREVIEW_LINES): String {
        val lines = stackTrace.lines()
        return if (lines.size <= maxLines) {
            stackTrace
        } else {
            lines.take(maxLines).joinToString("\n") + "\n... (${lines.size - maxLines} more lines)"
        }
    }

    fun saveCrashReport(
        context: Context,
        threadName: String,
        throwable: Throwable,
        appVersion: String = getAppVersion(context)
    ): CrashReportEntry {
        val entry = CrashReportEntry(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            appVersion = appVersion,
            threadName = threadName,
            exceptionType = throwable::class.java.simpleName.ifBlank { throwable::class.java.name },
            exceptionMessage = throwable.message ?: "",
            stackTrace = throwableToStackTrace(throwable),
            documentPath = "",
            isNonFatal = false
        )
        appendEntry(context, entry)
        return entry
    }

    fun logNonFatalParseError(
        context: Context?,
        documentPath: String,
        throwable: Throwable
    ) {
        if (context == null) return
        try {
            val entry = CrashReportEntry(
                id = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                appVersion = getAppVersion(context),
                threadName = Thread.currentThread().name,
                exceptionType = throwable::class.java.simpleName.ifBlank { throwable::class.java.name },
                exceptionMessage = throwable.message ?: "Failed to parse Firestore document",
                stackTrace = throwableToStackTrace(throwable),
                documentPath = documentPath,
                isNonFatal = true
            )
            appendEntry(context, entry)
        } catch (_: Throwable) {
            // Never throw while logging non-fatal errors
        }
    }

    fun appendEntry(context: Context, entry: CrashReportEntry) {
        synchronized(lock) {
            try {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                val existing = readEntriesInternal(file).toMutableList()
                existing.add(0, entry)
                val trimmed = existing.take(MAX_REPORTS)
                writeEntriesInternal(file, trimmed)
            } catch (_: Throwable) {
                // Best-effort persistence
            }
        }
    }

    fun getReports(context: Context): List<CrashReportEntry> {
        synchronized(lock) {
            return try {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                readEntriesInternal(file)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun clearReports(context: Context) {
        synchronized(lock) {
            try {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun formatTimestamp(timestampMillis: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM d, yyyy h:mm:ss a", Locale.US)
            sdf.format(Date(timestampMillis))
        } catch (_: Throwable) {
            timestampMillis.toString()
        }
    }

    fun formatFullReport(entry: CrashReportEntry): String {
        return buildString {
            appendLine(if (entry.isNonFatal) "Delivio Driver Non-Fatal Problem Report" else "Delivio Driver Crash Report")
            appendLine("Time: ${formatTimestamp(entry.timestampMillis)}")
            appendLine("App Version: ${entry.appVersion}")
            appendLine("Thread: ${entry.threadName}")
            if (entry.documentPath.isNotBlank()) {
                appendLine("Document Path: ${entry.documentPath}")
            }
            val header = if (entry.exceptionMessage.isNotBlank()) {
                "${entry.exceptionType}: ${entry.exceptionMessage}"
            } else {
                entry.exceptionType
            }
            appendLine("Exception: $header")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(entry.stackTrace)
        }
    }

    private fun readEntriesInternal(file: File): List<CrashReportEntry> {
        if (!file.exists()) return emptyList()
        val raw = file.readText(Charsets.UTF_8)
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        val result = mutableListOf<CrashReportEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(
                CrashReportEntry(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    timestampMillis = obj.optLong("timestampMillis", 0L),
                    appVersion = obj.optString("appVersion", "1.0"),
                    threadName = obj.optString("threadName", "main"),
                    exceptionType = obj.optString("exceptionType", "Exception"),
                    exceptionMessage = obj.optString("exceptionMessage", ""),
                    stackTrace = obj.optString("stackTrace", ""),
                    documentPath = obj.optString("documentPath", ""),
                    isNonFatal = obj.optBoolean("isNonFatal", false)
                )
            )
        }
        return result.take(MAX_REPORTS)
    }

    private fun writeEntriesInternal(file: File, entries: List<CrashReportEntry>) {
        val array = JSONArray()
        for (entry in entries.take(MAX_REPORTS)) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("timestampMillis", entry.timestampMillis)
            obj.put("appVersion", entry.appVersion)
            obj.put("threadName", entry.threadName)
            obj.put("exceptionType", entry.exceptionType)
            obj.put("exceptionMessage", entry.exceptionMessage)
            obj.put("stackTrace", entry.stackTrace)
            obj.put("documentPath", entry.documentPath)
            obj.put("isNonFatal", entry.isNonFatal)
            array.put(obj)
        }
        val bytes = array.toString(2).toByteArray(Charsets.UTF_8)
        FileOutputStream(file, false).use { fos ->
            fos.write(bytes)
            fos.flush()
            try {
                fos.fd.sync()
            } catch (_: Throwable) {
            }
        }
    }
}
