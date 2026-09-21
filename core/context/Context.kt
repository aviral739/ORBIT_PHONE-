package core.context

/**
 * Canonical Context model per the ORBIT Context contract.
 *
 * This represents an already-created context. ContextStore is not responsible
 * for deciding whether a context is semantically correct.
 *
 * Contract fields:
 * - id: Unique identifier for the context
 * - topic: Human-readable topic label
 * - eventIds: List of event IDs that contributed to this context
 * - summary: Human-readable summary of the context
 * - confidence: Confidence score
 */
data class Context(
    val id: String,
    val topic: String,
    val eventIds: List<String>,
    val summary: String,
    val confidence: Double
)