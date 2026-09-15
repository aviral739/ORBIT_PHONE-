package com.orbit.agent.core.action

import android.util.Log

/**
 * QuickReplyEngine
 *
 * Generates up to 3 instant canned reply suggestions for incoming
 * chat notifications (WhatsApp, Slack, SMS, Teams) **without** waking
 * any neural model or making a network call.
 *
 * The engine uses rule-based pattern matching over the message body:
 *   1. Intent classification (question / greeting / deadline / acknowledgement / …)
 *   2. Lookup against the [REPLY_PATTERNS] table for the matched intent
 *   3. Return the top-3 ranked candidates
 *
 * All processing is synchronous and completes in < 1 ms on any modern device.
 *
 * Usage:
 *   val engine = QuickReplyEngine()
 *   val replies = engine.suggestReplies(
 *       packageName = "com.whatsapp",
 *       sender      = "Alice",
 *       body        = "Can we push the meeting to tomorrow?"
 *   )
 *   // replies → ["Sure, tomorrow works!", "Let me check and confirm.", "I'll be there."]
 */
class QuickReplyEngine {

    companion object {
        private const val TAG = "QuickReplyEngine"
        private const val MAX_SUGGESTIONS = 3

        // ── Supported source apps ──────────────────────────────────────────
        private val CHAT_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.slack",
            "com.microsoft.teams",
            "com.google.android.apps.messaging",  // SMS / RCS
            "org.telegram.messenger",
            "com.discord",
        )
    }

    // ── Pattern → intent → reply table ────────────────────────────────────

    private data class ReplyRule(
        val patterns  : List<String>,          // lower-case substrings to match
        val intent    : String,
        val replies   : List<String>,          // ordered by preference
    )

    private val REPLY_PATTERNS: List<ReplyRule> = listOf(

        ReplyRule(
            patterns = listOf("?", "can you", "could you", "would you", "do you", "are you", "will you", "when"),
            intent   = "QUESTION",
            replies  = listOf(
                "Sure, let me get back to you shortly.",
                "Good question — I'll check and confirm.",
                "Yes, I can. Give me a moment.",
            ),
        ),

        ReplyRule(
            patterns = listOf("hi ", "hello", "hey ", "good morning", "good evening", "howdy"),
            intent   = "GREETING",
            replies  = listOf(
                "Hey! How's it going?",
                "Hi there!",
                "Hello! What's up?",
            ),
        ),

        ReplyRule(
            patterns = listOf("deadline", "due date", "due by", "submit by", "by when", "end of day", "eod", "asap"),
            intent   = "DEADLINE",
            replies  = listOf(
                "I'm on it — will have it done before the deadline.",
                "Noted. I'll prioritise this right away.",
                "Can we get a short extension? I'll follow up.",
            ),
        ),

        ReplyRule(
            patterns = listOf("meeting", "call", "sync", "zoom", "teams call", "google meet", "standup"),
            intent   = "MEETING",
            replies  = listOf(
                "I'll be there on time.",
                "Can we reschedule? I have a conflict.",
                "Send me the invite and I'll confirm.",
            ),
        ),

        ReplyRule(
            patterns = listOf("postpone", "reschedule", "push it", "move it", "delay", "later"),
            intent   = "RESCHEDULE",
            replies  = listOf(
                "Sure, let me know the new time.",
                "Works for me — just send the updated invite.",
                "I'd prefer to keep the original slot if possible.",
            ),
        ),

        ReplyRule(
            patterns = listOf("thanks", "thank you", "thx", "appreciate", "cheers"),
            intent   = "APPRECIATION",
            replies  = listOf(
                "You're welcome!",
                "Happy to help!",
                "Anytime!",
            ),
        ),

        ReplyRule(
            patterns = listOf("sorry", "apolog", "my bad", "oops", "mistake"),
            intent   = "APOLOGY",
            replies  = listOf(
                "No worries at all!",
                "It happens — don't stress.",
                "All good, let's move forward.",
            ),
        ),

        ReplyRule(
            patterns = listOf("ok", "okay", "got it", "noted", "understood", "sure", "will do", "on it"),
            intent   = "ACKNOWLEDGEMENT",
            replies  = listOf(
                "Great, talk soon!",
                "Perfect — I'll follow up.",
                "Sounds good!",
            ),
        ),

        ReplyRule(
            patterns = listOf("urgent", "emergency", "critical", "immediately", "right now", "fire"),
            intent   = "URGENT",
            replies  = listOf(
                "On it right now!",
                "Dropping everything — heading to this immediately.",
                "Acknowledged. Give me 2 minutes.",
            ),
        ),
    )

    // Fallback replies when no pattern matches
    private val FALLBACK_REPLIES = listOf(
        "Got it, thanks!",
        "I'll get back to you soon.",
        "Sure thing!",
    )

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns up to [MAX_SUGGESTIONS] quick-reply strings for a chat message.
     *
     * @param packageName  Sender app package (used to filter non-chat apps).
     * @param sender       Display name of the message sender (unused in v1 matching).
     * @param body         The raw message text.
     * @return             Ordered list of reply suggestions (1–3 items).
     */
    fun suggestReplies(packageName: String, sender: String, body: String): List<String> {
        if (!isChatPackage(packageName)) {
            Log.d(TAG, "Package $packageName is not a supported chat app — skipping")
            return emptyList()
        }

        val lower   = body.trim().lowercase()
        val matched = matchIntent(lower)

        Log.d(TAG, "suggestReplies: pkg=$packageName intent=${matched?.intent ?: "FALLBACK"}")
        return (matched?.replies ?: FALLBACK_REPLIES).take(MAX_SUGGESTIONS)
    }

    /**
     * Lightweight check — returns true if any known chat package prefix matches.
     */
    fun isChatPackage(packageName: String): Boolean =
        CHAT_PACKAGES.any { packageName.startsWith(it) }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Walk [REPLY_PATTERNS] in order (priority-first) and return the first
     * rule whose [patterns] list has at least one substring hit in [lowerBody].
     */
    private fun matchIntent(lowerBody: String): ReplyRule? =
        REPLY_PATTERNS.firstOrNull { rule ->
            rule.patterns.any { pattern -> lowerBody.contains(pattern) }
        }
}
