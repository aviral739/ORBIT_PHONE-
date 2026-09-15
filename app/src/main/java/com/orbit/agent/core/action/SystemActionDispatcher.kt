package com.orbit.agent.core.action

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log

/**
 * SystemActionDispatcher
 *
 * Executes programmatic device-level actions triggered by the
 * [DecisionEngine] — setting alarms, toggling Do-Not-Disturb, and
 * dispatching calendar intents — all without leaving the app process.
 *
 * Usage:
 *   val dispatcher = SystemActionDispatcher(context)
 *   dispatcher.setReminder(eventId = "c-001", triggerAtMs = …, label = "Submit report")
 *   dispatcher.toggleDoNotDisturb(enable = true)
 *   dispatcher.openCalendarEventCreator(title = "Team Sync", startMs = …, endMs = …)
 *
 * Required permissions (AndroidManifest.xml):
 *   <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
 *   <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY"/>
 *   <uses-permission android:name="android.permission.WRITE_CALENDAR"/>   <!-- optional -->
 */
class SystemActionDispatcher(private val context: Context) {

    companion object {
        private const val TAG = "SystemActionDispatcher"

        // Intent extra keys shared with any BroadcastReceiver that handles alarms
        const val EXTRA_EVENT_ID = "orbit_event_id"
        const val EXTRA_LABEL    = "orbit_label"
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ── Reminders / Alarms ────────────────────────────────────────────────

    /**
     * Schedule an exact alarm that fires at [triggerAtMs] (epoch ms).
     * The alarm wakes the device and broadcasts to [OrbitAlarmReceiver]
     * which then posts a heads-up notification with [label].
     *
     * Uses [AlarmManager.setExactAndAllowWhileIdle] to survive Doze mode.
     *
     * @param eventId     Stable ID linking the alarm back to a commitment/task.
     * @param triggerAtMs Epoch-millisecond fire time.
     * @param label       Human-readable reminder text shown in the notification.
     * @return            The request code used; pass to [cancelReminder] to cancel.
     */
    fun setReminder(eventId: String, triggerAtMs: Long, label: String): Int {
        val requestCode = eventId.hashCode()
        val intent      = buildAlarmIntent(eventId, label, requestCode)

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                intent,
            )
            Log.i(TAG, "Reminder set [reqCode=$requestCode] for '$label' at $triggerAtMs")
        } catch (se: SecurityException) {
            // On Android 12+ SCHEDULE_EXACT_ALARM may be revoked at runtime.
            // Fall back to inexact alarm — still fires, just not to the millisecond.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, intent)
            Log.w(TAG, "Exact alarm denied — falling back to inexact alarm: ${se.message}")
        }
        return requestCode
    }

    /**
     * Cancel a previously set reminder by its [requestCode]
     * (returned from [setReminder]).
     */
    fun cancelReminder(eventId: String, requestCode: Int) {
        val intent = buildAlarmIntent(eventId, "", requestCode)
        alarmManager.cancel(intent)
        Log.i(TAG, "Reminder cancelled [reqCode=$requestCode]")
    }

    // ── Do-Not-Disturb ────────────────────────────────────────────────────

    /**
     * Enable or disable Do-Not-Disturb (priority-only / total-silence).
     *
     * Requires [android.app.NotificationManager.isNotificationPolicyAccessGranted].
     * If permission is missing the call is a no-op and a warning is logged.
     *
     * @param enable  true = activate DND (INTERRUPTION_FILTER_PRIORITY),
     *                false = restore normal (INTERRUPTION_FILTER_ALL).
     */
    fun toggleDoNotDisturb(enable: Boolean) {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND toggle skipped — notification policy access not granted")
            return
        }
        val filter = if (enable) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        notificationManager.setInterruptionFilter(filter)
        Log.i(TAG, "DND ${if (enable) "enabled" else "disabled"} (filter=$filter)")
    }

    // ── Calendar intent dispatcher ─────────────────────────────────────────

    /**
     * Launch the system calendar app to create a new event pre-filled with
     * [title], [startMs], and [endMs].  Fires as a new-task Activity intent
     * so it works from a Service context.
     *
     * @param title   Event title shown in the calendar.
     * @param startMs Epoch-ms start time.
     * @param endMs   Epoch-ms end time.
     * @param description Optional event description / agenda.
     */
    fun openCalendarEventCreator(
        title: String,
        startMs: Long,
        endMs: Long,
        description: String = "",
    ) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data        = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE,            title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,  startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME,    endMs)
            putExtra(CalendarContract.Events.DESCRIPTION,      description)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Log.i(TAG, "Calendar intent dispatched: '$title' start=$startMs end=$endMs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open calendar: ${e.message}")
        }
    }

    /**
     * View an existing calendar event by its content-provider [eventId].
     */
    fun openCalendarEvent(eventId: Long) {
        val uri    = CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventId.toString()).build()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to view calendar event $eventId: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun buildAlarmIntent(eventId: String, label: String, requestCode: Int): PendingIntent {
        val broadcastIntent = Intent("com.orbit.agent.ACTION_REMINDER").apply {
            setPackage(context.packageName)
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_LABEL,    label)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
