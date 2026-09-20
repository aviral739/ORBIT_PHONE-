package core.decision

/**
 * DecisionEngine consumes EventScorer's ScoringResult and determines
 * the appropriate DecisionState (DROP, COMPRESS, QUEUE, REASON).
 *
 * It does NOT recompute scoring dimensions - those come from EventScorer.
 * It applies decision logic using thresholds from config/thresholds.json.
 * Default threshold values match config/thresholds.json.
 */
class DecisionEngine {

    data class Thresholds(
        val dropConfidenceCeiling: Double = 0.55,
        val dropScoreCeiling: Double = 0.3,
        val compressSimilarityThreshold: Double = 0.78,
        val queueUrgencyCeiling: Double = 0.65,
        val reasonEscalationThreshold: Double = 0.7,
        val reasonContextChangeThreshold: Double = 0.4,
        val reasonOverallScoreThreshold: Double = 0.75,
        val modelConfidenceThreshold: Double = 0.8
    )

    private val thresholds: Thresholds

    constructor() {
        this.thresholds = Thresholds() // Defaults match config/thresholds.json
    }

    /**
     * Allows injecting custom thresholds for testing.
     */
    constructor(thresholds: Thresholds) {
        this.thresholds = thresholds
    }

    /**
     * Evaluates an event and its scoring result to produce a DecisionResult.
     *
     * @param event The original event
     * @param scoringResult The ScoringResult from EventScorer
     * @return DecisionResult with the determined DecisionState
     */
    fun decide(event: Event, scoringResult: ScoringResult): DecisionResult {
        validateInputs(event, scoringResult)

        val state = determineState(scoringResult)
        val (requiresContext, requiresModel) = determineRequirements(state, scoringResult)
        val reason = buildReason(state, scoringResult)

        return DecisionResult(
            eventId = event.id,
            state = state,
            score = scoringResult.overallScore,
            confidence = scoringResult.confidence,
            reason = reason,
            requiresContext = requiresContext,
            requiresModel = requiresModel
        )
    }

    private fun validateInputs(event: Event, scoringResult: ScoringResult) {
        if (event.id.isBlank()) throw IllegalArgumentException("Event ID must not be blank")
        if (scoringResult.eventId != event.id) {
            throw IllegalArgumentException("ScoringResult eventId must match Event ID")
        }
        if (scoringResult.overallScore !in 0.0..1.0) {
            throw IllegalArgumentException("Overall score must be between 0.0 and 1.0")
        }
        if (scoringResult.confidence !in 0.0..1.0) {
            throw IllegalArgumentException("Confidence must be between 0.0 and 1.0")
        }
    }

    private fun determineState(scoring: ScoringResult): DecisionState {
        // REASON takes precedence - check if event is reasoning-worthy first
        val isReasonUrgency = scoring.urgency >= thresholds.reasonEscalationThreshold
        val isReasonContextChange = scoring.contextChange >= thresholds.reasonContextChangeThreshold
        val isReasonOverallScore = scoring.overallScore >= thresholds.reasonOverallScoreThreshold
        if (isReasonUrgency || isReasonContextChange || isReasonOverallScore) {
            return DecisionState.REASON
        }

        // DROP: Very low confidence AND low overall score
        // Low confidence alone shouldn't force DROP if the event has high value
        val isLowConfidence = scoring.confidence < thresholds.dropConfidenceCeiling
        val isLowScore = scoring.overallScore < thresholds.dropScoreCeiling
        if (isLowConfidence && isLowScore) {
            return DecisionState.DROP
        }

        // COMPRESS: Low novelty (high similarity to existing context)
        // compress_similarity_threshold = 0.78 means similarity > 0.78 triggers compress
        // novelty = 1 - similarity, so novelty < 0.22 means similarity > 0.78
        val compressNoveltyThreshold = 1.0 - thresholds.compressSimilarityThreshold
        if (scoring.novelty < compressNoveltyThreshold) {
            return DecisionState.COMPRESS
        }

        // QUEUE: Low urgency (below ceiling) - retain but don't reason yet
        if (scoring.urgency <= thresholds.queueUrgencyCeiling) {
            return DecisionState.QUEUE
        }

        // Default to REASON for moderate urgency that doesn't fit QUEUE
        return DecisionState.REASON
    }

    private fun determineRequirements(state: DecisionState, scoring: ScoringResult): Pair<Boolean, Boolean> {
        return when (state) {
            DecisionState.DROP -> Pair(false, false)
            DecisionState.COMPRESS -> Pair(true, false)
            DecisionState.QUEUE -> Pair(true, false)
            DecisionState.REASON -> Pair(true, scoring.confidence < thresholds.modelConfidenceThreshold)
        }
    }

    private fun buildReason(state: DecisionState, scoring: ScoringResult): String {
        val signals = mutableListOf<String>()

        when (state) {
            DecisionState.DROP -> signals.add("Low confidence (${String.format("%.2f", scoring.confidence)}) and low overall score (${String.format("%.2f", scoring.overallScore)})")
            DecisionState.COMPRESS -> signals.add("Low novelty (${String.format("%.2f", scoring.novelty)}) indicates redundant information")
            DecisionState.QUEUE -> signals.add("Moderate urgency (${String.format("%.2f", scoring.urgency)}) - queued for later processing")
            DecisionState.REASON -> {
                if (scoring.urgency >= thresholds.reasonEscalationThreshold) {
                    signals.add("High urgency (${String.format("%.2f", scoring.urgency)}) requires reasoning")
                }
                if (scoring.contextChange >= thresholds.reasonContextChangeThreshold) {
                    signals.add("Significant context change (${String.format("%.2f", scoring.contextChange)})")
                }
                if (scoring.overallScore >= thresholds.reasonOverallScoreThreshold) {
                    signals.add("High overall score (${String.format("%.2f", scoring.overallScore)})")
                }
                if (signals.isEmpty()) {
                    signals.add("Reasoning required based on combined signals")
                }
            }
        }

        return signals.joinToString("; ")
    }
}