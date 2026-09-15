package core.state

import java.util.UUID

// Temporary dummy data class (to be moved to model module later)
data class EventIntent(
    val matchedTaskId: String?, 
    val description: String, 
    val detectedTime: Long?, 
    val confidence: Float, 
    val sourceId: String
)

data class Deadline(
    val targetTime: Long?
)

data class Commitment(
    val id: String,
    val description: String,
    val deadline: Deadline,
    val sourceEvidenceIds: MutableList<String>,
    var confidence: Float
)

data class Conflict(
    val existingCommitmentId: String,
    val newDescription: String,
    val message: String
)

class StateEngine {
    private val activeCommitments = mutableMapOf<String, Commitment>()

    fun processIntent(intent: EventIntent): Any {
        // NEW: If matchedTaskId is null or not in activeCommitments
        if (intent.matchedTaskId == null || !activeCommitments.containsKey(intent.matchedTaskId)) {
            val newId = UUID.randomUUID().toString()
            val newCommitment = Commitment(
                id = newId,
                description = intent.description,
                deadline = Deadline(intent.detectedTime),
                sourceEvidenceIds = mutableListOf(intent.sourceId),
                confidence = intent.confidence
            )
            activeCommitments[newId] = newCommitment
            return newCommitment
        }

        val existingCommitment = activeCommitments[intent.matchedTaskId]!!

        // ACCUMULATE: If task exists AND detectedTime matches existing deadline
        if (existingCommitment.deadline.targetTime == intent.detectedTime) {
            existingCommitment.sourceEvidenceIds.add(intent.sourceId)
            existingCommitment.confidence = (existingCommitment.confidence + intent.confidence) / 2.0f
            return existingCommitment
        } 
        // CONFLICT: If task exists BUT detectedTime is different
        else {
            return Conflict(
                existingCommitmentId = existingCommitment.id,
                newDescription = intent.description,
                message = "Time conflict detected: existing deadline is ${existingCommitment.deadline.targetTime}, new intent time is ${intent.detectedTime}"
            )
        }
    }
}
