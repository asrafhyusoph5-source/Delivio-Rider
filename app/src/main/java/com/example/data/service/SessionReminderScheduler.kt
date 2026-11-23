package com.example.data.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.models.BookedSessionItem
import com.example.data.models.ScheduleRules
import com.example.data.models.ScheduleSession
import com.example.data.repository.UserPreferencesRepository

class SessionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(SessionReminderScheduler.EXTRA_SESSION_ID) ?: return
        val sessionTitle = intent.getStringExtra(SessionReminderScheduler.EXTRA_SESSION_TITLE).orEmpty()
        SessionReminderScheduler.showReminderNotification(
            context = context,
            sessionId = sessionId,
            sessionTitle = sessionTitle
        )
        try {
            UserPreferencesRepository(context.applicationContext).markReminderNotified(sessionId)
        } catch (_: Throwable) {
        }
    }
}

object SessionReminderScheduler {
    const val CHANNEL_ID = "delivio_session_reminders"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_SESSION_TITLE = "extra_session_title"
    private const val ACTION_SESSION_REMINDER = "com.example.ACTION_SESSION_REMINDER"

    private fun notificationId(sessionId: String): Int = 4000 + (sessionId.hashCode() and 0x0FFF)

    private fun createPendingIntent(
        context: Context,
        sessionId: String,
        sessionTitle: String
    ): PendingIntent {
        val intent = Intent(context, SessionReminderReceiver::class.java).apply {
            action = ACTION_SESSION_REMINDER
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_SESSION_TITLE, sessionTitle)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(sessionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleReminder(
        context: Context,
        session: ScheduleSession,
        userPrefs: UserPreferencesRepository? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (session.id.isBlank() || session.startAtMillis <= nowMillis) return
        val triggerAtMillis = session.startAtMillis - ScheduleRules.REMINDER_LEAD_MILLIS

        // If we are already within the 15-minute window before start, post immediately once
        if (triggerAtMillis <= nowMillis) {
            val prefs = userPrefs ?: try { UserPreferencesRepository(context.applicationContext) } catch (_: Throwable) { null }
            val alreadyNotified = prefs?.getNotifiedReminderSessionIds()?.contains(session.id) == true
            if (!alreadyNotified) {
                showReminderNotification(context, session.id, session.title)
                prefs?.markReminderNotified(session.id)
            }
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = createPendingIntent(context, session.id, session.title)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    fun cancelReminder(
        context: Context,
        sessionId: String,
        userPrefs: UserPreferencesRepository? = null
    ) {
        if (sessionId.isBlank()) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val pendingIntent = createPendingIntent(context, sessionId, "")
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (_: Throwable) {
        }
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(notificationId(sessionId))
        } catch (_: Throwable) {
        }
        try {
            (userPrefs ?: UserPreferencesRepository(context.applicationContext)).clearReminderNotified(sessionId)
        } catch (_: Throwable) {
        }
    }

    fun syncReminders(
        context: Context,
        bookedItems: List<BookedSessionItem>,
        userPrefs: UserPreferencesRepository? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        for (item in bookedItems) {
            if (item.session.startAtMillis > nowMillis) {
                scheduleReminder(
                    context = context,
                    session = item.session,
                    userPrefs = userPrefs,
                    nowMillis = nowMillis
                )
            }
        }
    }

    fun showReminderNotification(
        context: Context,
        sessionId: String,
        sessionTitle: String
    ) {
        try {
            ensureNotificationChannel(context)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId(sessionId),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val title = if (sessionTitle.isNotBlank()) sessionTitle else "Upcoming Session"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_delivio_notification)
                .setContentTitle(title)
                .setContentText("Your session starts in 15 minutes")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(notificationId(sessionId), notification)
        } catch (_: Throwable) {
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Session Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "15-minute reminders before booked delivery sessions"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
