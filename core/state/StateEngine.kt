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



class StateEngine {
    private val activeCommitments = mutableMapOf<String, Commitment>()

    fun processIntent(intent: EventIntent): Any {
        // NEW: If matchedTaskId is null or not in activeCommitments
        if (intent.matchedTaskId == null || !activeCommitments.containsKey(intent.matchedTaskId)) {
            val newId = UUID.randomUUID().toString()
            val newCommitment = Commitment(
                id = newId,
                taskDescription = intent.description,
                deadline = intent.detectedTime?.let {
                    Deadline(
                        originalTime = it,
                        newTime = null,
                        confidence = intent.confidence,
                        sourceEvidenceIds = listOf(intent.sourceId)
                    )
                },
                sourceEvidenceIds = mutableListOf(intent.sourceId),
                confidence = intent.confidence
            )
            activeCommitments[newId] = newCommitment
            return newCommitment
        }

        val existingCommitment = activeCommitments[intent.matchedTaskId]!!

        // ACCUMULATE: If task exists AND detectedTime matches existing deadline
        val currentDeadlineTime = existingCommitment.deadline?.newTime ?: existingCommitment.deadline?.originalTime
        if (currentDeadlineTime == intent.detectedTime) {
            existingCommitment.sourceEvidenceIds.add(intent.sourceId)
            existingCommitment.confidence = (existingCommitment.confidence + intent.confidence) / 2.0f
            return existingCommitment
        } 
        // CONFLICT: If task exists BUT detectedTime is different
        else {
            return Conflict(
                existingCommitmentId = existingCommitment.id,
                newEvidenceId = intent.sourceId,
                description = "Time conflict detected: existing deadline is $currentDeadlineTime, new intent time is ${intent.detectedTime}"
            )
        }
    }
}
