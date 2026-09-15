package com.orbit.agent.core.decision

import android.content.Context
import android.util.Log
import com.orbit.agent.bridge.OfficeKitBridge
import com.orbit.agent.database.OrbitDatabase
import com.orbit.agent.database.entity.AuditLogEntity
import com.orbit.agent.database.entity.EventEntity
import com.orbit.agent.ingestion.audio.AudioIntent
import com.orbit.agent.ingestion.camera.CameraCommitment
import com.orbit.agent.ingestion.notification.NotificationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * DecisionEngine
 *
 * Central dispatcher that ingests raw sensory payloads from every
 * Phase 2/3 source and applies the threshold + policy rules defined in
 * `config/thresholds.json` and `config/policies.json`.
 *
 * Triage decisions
 * ────────────────
 *   DROP       – confidence < 0.60  →  discard, log SAFE audit entry
 *   COMPRESS   – duplicate/repetitive thread detected  →  log SAFE
 *   QUEUE      – non-urgent, actionable  →  persist for later, log SAFE
 *   REASON     – importance ≥ 0.70 or conflict  →  SLM entity parse,
 *                optionally escalate via OfficeKitBridge, log REVERSIBLE
 *                or SENSITIVE per policies.json
 *
 * All writes to Room are fire-and-forget on [Dispatchers.IO].
 *
 * Instantiation
 * ─────────────
 *   val engine = DecisionEngine.getInstance(context)
 */
class DecisionEngine private constructor(context: Context) {

    companion object {
        private const val TAG = "DecisionEngine"

        // ── Threshold keys (mirrors config/thresholds.json) ──────────────
        private const val KEY_DROP_CONFIDENCE     = "drop_confidence"
        private const val KEY_COMPRESS_SIMILARITY = "compress_similarity"
        private const val KEY_REASON_IMPORTANCE   = "reason_importance"

        private const val DEFAULT_DROP_CONFIDENCE     = 0.60f
        private const val DEFAULT_COMPRESS_SIMILARITY = 0.82f
        private const val DEFAULT_REASON_IMPORTANCE   = 0.70f

        // ── Governance tier names (mirrors config/policies.json) ──────────
        const val TIER_SAFE       = "SAFE"
        const val TIER_REVERSIBLE = "REVERSIBLE"
        const val TIER_SENSITIVE  = "SENSITIVE"

        @Volatile private var INSTANCE: DecisionEngine? = null

        fun getInstance(context: Context): DecisionEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DecisionEngine(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Dependencies ──────────────────────────────────────────────────────
    private val db: OrbitDatabase     = OrbitDatabase.getInstance(context)
    private val scope                 = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // OfficeKitBridge is injected lazily — not every process has a foreground
    // Activity, so we avoid crashing if it hasn't been attached yet.
    private var officeKitBridge: OfficeKitBridge? = null

    // ── Thresholds (loaded once from assets) ─────────────────────────────
    private val dropConfidenceThreshold: Float
    private val compressSimilarityThreshold: Float
    private val reasonImportanceThreshold: Float

    init {
        val thresholds = loadThresholds(context)
        dropConfidenceThreshold     = thresholds.optDouble(KEY_DROP_CONFIDENCE,     DEFAULT_DROP_CONFIDENCE.toDouble()).toFloat()
        compressSimilarityThreshold = thresholds.optDouble(KEY_COMPRESS_SIMILARITY, DEFAULT_COMPRESS_SIMILARITY.toDouble()).toFloat()
        reasonImportanceThreshold   = thresholds.optDouble(KEY_REASON_IMPORTANCE,   DEFAULT_REASON_IMPORTANCE.toDouble()).toFloat()
        Log.i(TAG, "Thresholds loaded — drop=$dropConfidenceThreshold compress=$compressSimilarityThreshold reason=$reasonImportanceThreshold")
    }

    // ── Public API: bridge injection ──────────────────────────────────────

    fun attachBridge(bridge: OfficeKitBridge) {
        officeKitBridge = bridge
    }

    // ── Public API: sensory input handlers ───────────────────────────────

    /** Called by [OrbitNotificationListener] for every valid notification. */
    fun onNotification(payload: NotificationPayload) {
        val eventId = UUID.randomUUID().toString()
        val event   = EventEntity(
            id         = eventId,
            type       = "NOTIFICATION",
            data       = "${payload.title}|${payload.body}",
            importance = payload.importance,
        )
        dispatch(event, payload.importance, confidence = 0.75f)   // notifications carry medium-high confidence
    }

    /** Called by [VisionOcrAnalyzer] when a commitment text block is detected. */
    fun onCameraCommitment(commitment: CameraCommitment) {
        val eventId = UUID.randomUUID().toString()
        val event   = EventEntity(
            id         = eventId,
            type       = "CAMERA_OCR",
            data       = commitment.rawText,
            importance = 0.65f,   // OCR hits are moderately important by default
        )
        dispatch(event, importance = 0.65f, confidence = 0.70f)
    }

    /** Called by [WhisperAudioRecorder] when spoken commitments are detected. */
    fun onAudioIntent(intent: AudioIntent) {
        val eventId   = UUID.randomUUID().toString()
        val importance = (intent.triggers.size.toFloat() / 5f).coerceIn(0f, 1f)
        val event     = EventEntity(
            id         = eventId,
            type       = "AUDIO_WHISPER",
            data       = intent.transcript,
            importance = importance,
        )
        dispatch(event, importance = importance, confidence = 0.80f)
    }

    // ── Core triage ───────────────────────────────────────────────────────

    /**
     * Apply threshold rules and persist [event] + one audit record.
     *
     * @param event       Prepared entity ready for Room insert.
     * @param importance  Semantic importance score ∈ [0, 1].
     * @param confidence  Source-layer confidence ∈ [0, 1].
     */
    private fun dispatch(event: EventEntity, importance: Float, confidence: Float) {
        scope.launch {
            val decision = triage(importance, confidence, event)
            val tier     = governanceTier(decision)
            val finalEvent = event.copy(status = decision)

            // Persist event
            db.eventDao().insertOrReplace(finalEvent)

            // Append audit record
            db.auditLogDao().insert(
                AuditLogEntity(
                    id       = UUID.randomUUID().toString(),
                    action   = decision,
                    details  = buildAuditDetails(event, importance, confidence),
                    riskTier = tier,
                    eventId  = event.id,
                )
            )

            Log.d(TAG, "[$decision / $tier] event=${event.id} type=${event.type} imp=$importance conf=$confidence")

            // Escalate REASON decisions with desktop bridge if attached
            if (decision == "REASON" && officeKitBridge != null) {
                val payloadJson = buildEscalationPayload(finalEvent, importance)
                officeKitBridge!!.dispatchEscalation(event.id, payloadJson)
            }
        }
    }

    /**
     * Pure triage function — no I/O, fully unit-testable.
     *
     * Rule priority (first match wins):
     *   1. Low confidence  → DROP
     *   2. Duplicate data  → COMPRESS
     *   3. High importance → REASON
     *   4. Default         → QUEUE
     */
    private suspend fun triage(
        importance: Float,
        confidence: Float,
        event: EventEntity,
    ): String {
        if (confidence < dropConfidenceThreshold)   return "DROP"
        if (isDuplicate(event))                      return "COMPRESS"
        if (importance >= reasonImportanceThreshold) return "REASON"
        return "QUEUE"
    }

    /** Governance tier lookup — mirrors policies.json tier mapping. */
    private fun governanceTier(decision: String): String = when (decision) {
        "DROP", "COMPRESS" -> TIER_SAFE
        "QUEUE"            -> TIER_REVERSIBLE
        "REASON"           -> TIER_SENSITIVE
        else               -> TIER_SAFE
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Check if a semantically identical event already exists in Room. */
    private suspend fun isDuplicate(event: EventEntity): Boolean {
        val candidates = db.eventDao().findDuplicates(event.type, event.data ?: "", limit = 3)
        return candidates.isNotEmpty()
    }

    private fun buildAuditDetails(event: EventEntity, imp: Float, conf: Float): String =
        """{"eventId":"${event.id}","type":"${event.type}","importance":$imp,"confidence":$conf}"""

    private fun buildEscalationPayload(event: EventEntity, imp: Float): String =
        """{"commitmentId":"${event.id}","type":"${event.type}","importance":$imp,"data":${JSONObject.quote(event.data ?: "")}}"""

    /**
     * Load `config/thresholds.json` from the APK assets folder.
     * Returns an empty [JSONObject] on any failure so defaults apply.
     */
    private fun loadThresholds(context: Context): JSONObject {
        return try {
            val json = context.assets.open("config/thresholds.json")
                .bufferedReader()
                .readText()
            JSONObject(json)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load thresholds.json — using defaults: ${e.message}")
            JSONObject()
        }
    }
}
