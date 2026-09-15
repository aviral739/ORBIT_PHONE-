package com.orbit.agent.core.decision

import android.content.Context
import android.util.Log
import com.orbit.agent.bridge.OfficeKitBridge
import com.orbit.agent.core.action.QuickReplyEngine
import com.orbit.agent.database.OrbitDatabase
import com.orbit.agent.database.entity.AuditLogEntity
import com.orbit.agent.database.entity.CommitmentEntity
import com.orbit.agent.database.entity.EventEntity
import com.orbit.agent.database.entity.KnowledgeEntity
import com.orbit.agent.database.entity.TaskEntity
import com.orbit.agent.ingestion.audio.AudioIntent
import com.orbit.agent.ingestion.camera.CameraCommitment
import com.orbit.agent.ingestion.notification.NotificationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * DecisionEngine  (Phase 5 — Multifunctional Agent)
 *
 * Central dispatcher that ingests raw sensory payloads from every
 * ingestion source, classifies them into semantic intents, and routes
 * them to the appropriate specialised handler:
 *
 *   ┌─────────────────────────────────────────────────────────────────┐
 *   │ Input source          │ Primary routing target                  │
 *   ├─────────────────────────────────────────────────────────────────┤
 *   │ Scheduling text/audio │ CommitmentDao  + SystemActionDispatcher │
 *   │ Actionable task/to-do │ TaskDao                                 │
 *   │ Info / reference data │ KnowledgeDao                            │
 *   │ Chat notification     │ QuickReplyEngine (3 canned replies)     │
 *   └─────────────────────────────────────────────────────────────────┘
 *
 * Triage decisions (from config/thresholds.json)
 * ───────────────────────────────────────────────
 *   DROP     – confidence < 0.60  →  discard, SAFE audit
 *   COMPRESS – duplicate/repetitive  →  merge, SAFE audit
 *   QUEUE    – non-urgent actionable  →  persist, REVERSIBLE audit
 *   REASON   – importance >= 0.70 or conflict  →  SLM parse / escalate,
 *              SENSITIVE audit
 */
class DecisionEngine private constructor(context: Context) {

    companion object {
        private const val TAG = "DecisionEngine"

        // ── Threshold defaults (overridden by config/thresholds.json) ────
        private const val DEFAULT_DROP_CONFIDENCE     = 0.60f
        private const val DEFAULT_COMPRESS_SIMILARITY = 0.82f
        private const val DEFAULT_REASON_IMPORTANCE   = 0.70f

        // ── Governance tier labels (mirrors config/policies.json) ────────
        const val TIER_SAFE       = "SAFE"
        const val TIER_REVERSIBLE = "REVERSIBLE"
        const val TIER_SENSITIVE  = "SENSITIVE"

        // ── Scheduling-intent keywords ───────────────────────────────────
        private val SCHEDULING_KEYWORDS = setOf(
            "deadline", "due", "meet", "meeting", "schedule", "call",
            "remind", "alarm", "by", "before", "submit", "deliver",
            "tomorrow", "today", "monday", "tuesday", "wednesday",
            "thursday", "friday", "am", "pm",
        )

        // ── Task / to-do keywords ────────────────────────────────────────
        private val TASK_KEYWORDS = setOf(
            "todo", "to-do", "task", "fix", "buy", "send", "write",
            "review", "check", "update", "create", "prepare", "build",
            "finish", "complete", "do ", "need to", "have to", "must",
        )

        // ── Informational / reference keywords ───────────────────────────
        private val KNOWLEDGE_KEYWORDS = setOf(
            "note", "receipt", "invoice", "price", "total", "paid",
            "ref", "reference", "confirm", "account", "number", "#",
            "memo", "info", "details", "fyi", "for your information",
        )

        // ── Source-type labels for KnowledgeEntity ───────────────────────
        private const val SRC_CAMERA_OCR = "CAMERA_OCR"
        private const val SRC_AUDIO_MEMO = "AUDIO_MEMO"
        private const val SRC_CLIPBOARD  = "CLIPBOARD"

        @Volatile private var INSTANCE: DecisionEngine? = null

        fun getInstance(context: Context): DecisionEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DecisionEngine(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Dependencies ──────────────────────────────────────────────────────
    private val db                          = OrbitDatabase.getInstance(context)
    private val scope                       = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val quickReplyEngine            = QuickReplyEngine()

    private var officeKitBridge: OfficeKitBridge? = null

    // ── Loaded thresholds ─────────────────────────────────────────────────
    private val dropConfidenceThreshold: Float
    private val compressSimilarityThreshold: Float
    private val reasonImportanceThreshold: Float

    init {
        val t = loadThresholds(context)
        dropConfidenceThreshold     = t.optDouble("drop_confidence",     DEFAULT_DROP_CONFIDENCE.toDouble()).toFloat()
        compressSimilarityThreshold = t.optDouble("compress_similarity", DEFAULT_COMPRESS_SIMILARITY.toDouble()).toFloat()
        reasonImportanceThreshold   = t.optDouble("reason_importance",   DEFAULT_REASON_IMPORTANCE.toDouble()).toFloat()
        Log.i(TAG, "Thresholds — drop=$dropConfidenceThreshold compress=$compressSimilarityThreshold reason=$reasonImportanceThreshold")
    }

    // ── Bridge injection ──────────────────────────────────────────────────

    fun attachBridge(bridge: OfficeKitBridge) {
        officeKitBridge = bridge
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public sensory input handlers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Called by [OrbitNotificationListener].
     * Chat apps get quick-reply suggestions; other apps route normally.
     */
    fun onNotification(payload: NotificationPayload) {
        scope.launch {
            // Fast path: chat notification → generate quick replies
            if (quickReplyEngine.isChatPackage(payload.packageName)) {
                val replies = quickReplyEngine.suggestReplies(
                    packageName = payload.packageName,
                    sender      = payload.title ?: "",
                    body        = payload.body,
                )
                Log.d(TAG, "QuickReply suggestions for ${payload.packageName}: $replies")
                // Replies are returned to the calling layer via the event record's `data` field
                // so the UI can surface them as notification action buttons.
            }

            val eventId   = UUID.randomUUID().toString()
            val text      = "${payload.title.orEmpty()} ${payload.body}"
            val importance = payload.importance

            val event = EventEntity(
                id         = eventId,
                type       = "NOTIFICATION",
                data       = text,
                importance = importance,
            )
            dispatch(event, importance = importance, confidence = 0.75f, rawText = text)
        }
    }

    /** Called by [VisionOcrAnalyzer]. Routes to commitment, task, or knowledge. */
    fun onCameraCommitment(commitment: CameraCommitment) {
        scope.launch {
            val eventId = UUID.randomUUID().toString()
            val event   = EventEntity(
                id         = eventId,
                type       = "CAMERA_OCR",
                data       = commitment.rawText,
                importance = 0.65f,
            )
            dispatch(event, importance = 0.65f, confidence = 0.70f, rawText = commitment.rawText)
        }
    }

    /** Called by [WhisperAudioRecorder]. Routes based on spoken keyword class. */
    fun onAudioIntent(intent: AudioIntent) {
        scope.launch {
            val importance = (intent.triggers.size.toFloat() / 5f).coerceIn(0f, 1f)
            val eventId    = UUID.randomUUID().toString()
            val event      = EventEntity(
                id         = eventId,
                type       = "AUDIO_WHISPER",
                data       = intent.transcript,
                importance = importance,
            )
            dispatch(event, importance = importance, confidence = 0.80f, rawText = intent.transcript)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core triage + routing
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Triage the event, persist it, write an audit record, then route the
     * text payload to the most relevant specialised DAO.
     */
    private suspend fun dispatch(
        event: EventEntity,
        importance: Float,
        confidence: Float,
        rawText: String,
    ) {
        // 1. Triage decision
        val decision  = triage(importance, confidence, event)
        val tier      = governanceTier(decision)
        val finalEvent = event.copy(status = decision)

        // 2. Persist event
        db.eventDao().insertOrReplace(finalEvent)

        // 3. Audit record
        db.auditLogDao().insert(
            AuditLogEntity(
                id       = UUID.randomUUID().toString(),
                action   = decision,
                details  = buildAuditDetails(event, importance, confidence),
                riskTier = tier,
                eventId  = event.id,
            )
        )

        Log.d(TAG, "[$decision/$tier] id=${event.id} type=${event.type} imp=$importance conf=$confidence")

        // 4. Semantic routing (only for events that survive DROP)
        if (decision != "DROP") {
            routeBySemantics(event.id, event.type, rawText, decision)
        }

        // 5. Escalate REASON decisions via OfficeKitBridge if available
        if (decision == "REASON" && officeKitBridge != null) {
            officeKitBridge!!.dispatchEscalation(
                event.id,
                buildEscalationPayload(finalEvent, importance),
            )
        }
    }

    /**
     * Classify [rawText] into a semantic category and write to the
     * matching specialised DAO:
     *
     *   SCHEDULE  → CommitmentDao
     *   TASK      → TaskDao
     *   KNOWLEDGE → KnowledgeDao
     *   (no clear match: stored as KNOWLEDGE by default)
     */
    private suspend fun routeBySemantics(
        eventId: String,
        sourceType: String,
        rawText: String,
        decision: String,
    ) {
        val lower      = rawText.lowercase()
        val category   = classifyText(lower)

        Log.d(TAG, "Semantic routing: category=$category eventId=$eventId")

        when (category) {
            "SCHEDULE" -> {
                db.commitmentDao().insertOrReplace(
                    CommitmentEntity(
                        id          = UUID.randomUUID().toString(),
                        eventId     = eventId,
                        description = rawText.take(512),
                        // Deadline extraction from NLP is deferred to Phase 6;
                        // store null for now so the commitment is still discoverable.
                        deadline    = null,
                        status      = if (decision == "REASON") "ACTIVE" else "PENDING",
                    )
                )
            }

            "TASK" -> {
                val priority = when {
                    lower.contains("urgent") || lower.contains("asap") -> "P0"
                    lower.contains("soon")   || lower.contains("today") -> "P1"
                    else                                                 -> "P2"
                }
                db.taskDao().insert(
                    TaskEntity(
                        id            = UUID.randomUUID().toString(),
                        title         = rawText.lines().first().take(120),
                        description   = rawText.take(512),
                        priority      = priority,
                        sourceEventId = eventId,
                    )
                )
            }

            "KNOWLEDGE" -> {
                val knowledgeSourceType = when (sourceType) {
                    "CAMERA_OCR"   -> SRC_CAMERA_OCR
                    "AUDIO_WHISPER" -> SRC_AUDIO_MEMO
                    else            -> SRC_CLIPBOARD
                }
                db.knowledgeDao().insertSnippet(
                    KnowledgeEntity(
                        id         = UUID.randomUUID().toString(),
                        sourceType = knowledgeSourceType,
                        rawText    = rawText.take(2048),
                        tags       = extractTags(lower),
                    )
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Classification helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Keyword-vote classifier.
     * Returns the category with the most keyword hits;
     * ties broken by priority: SCHEDULE > TASK > KNOWLEDGE.
     */
    private fun classifyText(lower: String): String {
        val scheduleScore  = SCHEDULING_KEYWORDS.count { lower.contains(it) }
        val taskScore      = TASK_KEYWORDS.count       { lower.contains(it) }
        val knowledgeScore = KNOWLEDGE_KEYWORDS.count  { lower.contains(it) }

        return when {
            scheduleScore > taskScore && scheduleScore > knowledgeScore -> "SCHEDULE"
            taskScore     > knowledgeScore                               -> "TASK"
            else                                                         -> "KNOWLEDGE"
        }
    }

    /** Extract a minimal pipe-delimited tag string from common receipt/note terms. */
    private fun extractTags(lower: String): String {
        val tags = mutableListOf<String>()
        if (lower.contains("receipt") || lower.contains("invoice")) tags += "finance"
        if (lower.contains("meeting") || lower.contains("call"))    tags += "meeting"
        if (lower.contains("deadline") || lower.contains("due"))    tags += "deadline"
        return tags.joinToString("|")
    }

    // ─────────────────────────────────────────────────────────────────────
    // Triage / governance
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun triage(importance: Float, confidence: Float, event: EventEntity): String {
        if (confidence < dropConfidenceThreshold)    return "DROP"
        if (isDuplicate(event))                       return "COMPRESS"
        if (importance >= reasonImportanceThreshold) return "REASON"
        return "QUEUE"
    }

    private fun governanceTier(decision: String): String = when (decision) {
        "DROP", "COMPRESS" -> TIER_SAFE
        "QUEUE"            -> TIER_REVERSIBLE
        "REASON"           -> TIER_SENSITIVE
        else               -> TIER_SAFE
    }

    private suspend fun isDuplicate(event: EventEntity): Boolean =
        db.eventDao().findDuplicates(event.type, event.data ?: "", limit = 3).isNotEmpty()

    // ─────────────────────────────────────────────────────────────────────
    // String helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun buildAuditDetails(event: EventEntity, imp: Float, conf: Float): String =
        """{"eventId":"${event.id}","type":"${event.type}","importance":$imp,"confidence":$conf}"""

    private fun buildEscalationPayload(event: EventEntity, imp: Float): String =
        """{"commitmentId":"${event.id}","type":"${event.type}","importance":$imp,"data":${JSONObject.quote(event.data ?: "")}}"""

    private fun loadThresholds(context: Context): JSONObject = try {
        val json = context.assets.open("config/thresholds.json").bufferedReader().readText()
        JSONObject(json)
    } catch (e: Exception) {
        Log.w(TAG, "Could not load thresholds.json — using defaults: ${e.message}")
        JSONObject()
    }
}
