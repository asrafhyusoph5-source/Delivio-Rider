package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.example.util.CrashReportStore
import com.google.firebase.FirebaseApp
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class DelivioApplication : Application() {
    override fun onCreate() {
        // Install global uncaught-exception handler as the very first thing in Application.onCreate()
        installCrashHandler(this)
        super.onCreate()
        appContextOrNull = applicationContext
        ensureFirebase(this)
    }

    companion object {
        @Volatile
        var appContextOrNull: Context? = null
            private set

        private val isHandlingCrash = AtomicBoolean(false)

        fun installCrashHandler(context: Context) {
            // Skip in isolated :crash process so the crash screen cannot loop
            if (isCrashProcess(context)) return

            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                if (isHandlingCrash.compareAndSet(false, true)) {
                    try {
                        val appCtx = context.applicationContext ?: context
                        val appVersion = CrashReportStore.getAppVersion(appCtx)
                        val entry = CrashReportStore.saveCrashReport(
                            context = appCtx,
                            threadName = thread.name,
                            throwable = throwable,
                            appVersion = appVersion
                        )
                        val intent = Intent(appCtx, CrashReportActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                            )
                            putExtra(CrashReportActivity.EXTRA_TIMESTAMP, entry.timestampMillis)
                            putExtra(CrashReportActivity.EXTRA_APP_VERSION, entry.appVersion)
                            putExtra(CrashReportActivity.EXTRA_THREAD_NAME, entry.threadName)
                            putExtra(CrashReportActivity.EXTRA_EXCEPTION_TYPE, entry.exceptionType)
                            putExtra(CrashReportActivity.EXTRA_EXCEPTION_MESSAGE, entry.exceptionMessage)
                            putExtra(CrashReportActivity.EXTRA_STACK_TRACE, entry.stackTrace.take(32000))
                        }
                        appCtx.startActivity(intent)
                    } catch (_: Throwable) {
                        previousHandler?.uncaughtException(thread, throwable)
                    } finally {
                        Process.killProcess(Process.myPid())
                        exitProcess(10)
                    }
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }

        private fun isCrashProcess(context: Context): Boolean {
            return try {
                val processName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    getProcessName()
                } else {
                    ""
                }
                processName.endsWith(":crash")
            } catch (_: Throwable) {
                false
            }
        }

        fun ensureFirebase(context: Context) {
            try {
                val appCtx = context.applicationContext ?: context
                if (FirebaseApp.getApps(appCtx).isEmpty()) {
                    val app = FirebaseApp.initializeApp(appCtx)
                    if (app == null && FirebaseApp.getApps(appCtx).isEmpty()) {
                        Log.e("DelivioApplication", "FirebaseApp.initializeApp returned null; check app/google-services.json configuration")
                    }
                }
            } catch (t: Throwable) {
                Log.e("DelivioApplication", "Firebase initialization failed", t)
            }
        }
    }
}
