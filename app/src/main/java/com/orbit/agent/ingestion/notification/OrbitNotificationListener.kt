package com.orbit.agent.ingestion.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.orbit.agent.core.decision.DecisionEngine

/**
 * OrbitNotificationListener
 *
 * Listens for system-wide notification events and forwards
 * semantically meaningful payloads to the [DecisionEngine].
 *
 * Registered in AndroidManifest.xml with:
 *   <service android:name=".ingestion.notification.OrbitNotificationListener"
 *            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
 *     <intent-filter>
 *       <action android:name="android.service.notification.NotificationListenerService"/>
 *     </intent-filter>
 *   </service>
 */
class OrbitNotificationListener : NotificationListenerService() {

    private val decisionEngine: DecisionEngine by lazy { DecisionEngine.getInstance(applicationContext) }

    // ── System-noise flags we silently skip ──────────────────────────────
    private val IGNORED_FLAGS = setOf(
        Notification.FLAG_FOREGROUND_SERVICE,   // ongoing service ticker
        Notification.FLAG_ONGOING_EVENT,         // persistent ongoing (e.g., media playback)
        Notification.FLAG_LOCAL_ONLY,            // wearable bridge — not user-facing on phone
    )

    // ── Package prefixes we skip to avoid feedback loops ─────────────────
    private val IGNORED_PACKAGES = setOf(
        "com.orbit.agent",                        // self
        "android",                                // low-battery, USB, etc.
        "com.android.systemui",                   // status bar noise
    )

    // ── Temporal / urgency keywords: forwarded with elevated importance ───
    private val TEMPORAL_KEYWORDS = listOf(
        "deadline", "due", "meeting", "submit",
        "urgent", "asap", "reminder", "schedule",
        "AM", "PM", "today", "tomorrow",
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg   = sbn.packageName ?: return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // ── Drop noise categories ─────────────────────────────────────────
        if (IGNORED_PACKAGES.any { pkg.startsWith(it) }) return
        if (IGNORED_FLAGS.any { flag -> (notif.flags and flag) != 0 }) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val body  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()

        // Require at least a body to be useful
        if (body.isNullOrBlank()) return

        val combinedText = "${title.orEmpty()} $body"
        val importance   = computeImportance(combinedText)

        val payload = NotificationPayload(
            packageName  = pkg,
            title        = title,
            body         = body,
            importance   = importance,
            postedAt     = sbn.postTime,
        )

        decisionEngine.onNotification(payload)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not used in Phase 2 — reserved for dismissal tracking.
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Returns a [0.0, 1.0] importance score heuristic based on
     * temporal keyword density in [text].
     */
    private fun computeImportance(text: String): Float {
        val lower   = text.lowercase()
        val matches = TEMPORAL_KEYWORDS.count { kw -> lower.contains(kw.lowercase()) }
        return (matches.toFloat() / TEMPORAL_KEYWORDS.size).coerceIn(0f, 1f)
    }
}

/** Immutable value object forwarded to [DecisionEngine]. */
data class NotificationPayload(
    val packageName : String,
    val title       : String?,
    val body        : String,
    val importance  : Float,
    val postedAt    : Long,
)
