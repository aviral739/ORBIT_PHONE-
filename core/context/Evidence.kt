package core.context

/**
 * Canonical Evidence model per the ORBIT Evidence contract.
 *
 * Represents source evidence behind ORBIT's Context and State decisions.
 *
 * Contract fields:
 * - id: Unique identifier for the evidence (evd_<eventId>)
 * - eventId: ID of the originating event
 * - source: Source of the event (e.g., NOTIFICATION, VOICE, CAMERA)
 * - text: Raw text content from the event
 * - timestamp: ISO-8601 timestamp from the event
 * - confidence: Confidence score supplied by the caller
 */
data class Evidence(
    val id: String,
    val eventId: String,
    val source: String,
    val text: String,
    val timestamp: String,
    val confidence: Double
)