package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.CrashReportStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashReportStoreRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        CrashReportStore.clearReports(context)
    }

    @Test
    fun `crash report store caps at last 5 reports newest first`() {
        repeat(7) { index ->
            CrashReportStore.saveCrashReport(
                context = context,
                threadName = "main",
                throwable = IllegalStateException("Crash #$index")
            )
        }

        val reports = CrashReportStore.getReports(context)
        assertEquals(5, reports.size)
        assertEquals("Crash #6", reports.first().exceptionMessage)
        assertEquals("Crash #2", reports.last().exceptionMessage)
        assertFalse(reports.first().isNonFatal)
    }

    @Test
    fun `non-fatal parse errors record document path in same report list`() {
        CrashReportStore.logNonFatalParseError(
            context = context,
            documentPath = "orders/ord_123",
            throwable = RuntimeException("Missing field")
        )

        val reports = CrashReportStore.getReports(context)
        assertEquals(1, reports.size)
        val entry = reports.first()
        assertTrue(entry.isNonFatal)
        assertEquals("orders/ord_123", entry.documentPath)
        assertEquals("RuntimeException", entry.exceptionType)
        assertEquals("Missing field", entry.exceptionMessage)
    }

    @Test
    fun `firstLines truncates stack trace preview cleanly after 40 lines`() {
        val longTrace = (1..60).joinToString("\n") { "at com.example.Class.method(File.kt:$it)" }
        val preview = CrashReportStore.firstLines(longTrace, 40)
        val lines = preview.lines()
        assertEquals(41, lines.size) // 40 trace lines + 1 summary line
        assertTrue(lines.last().contains("20 more lines"))
    }
}
