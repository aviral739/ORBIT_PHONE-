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

    fun processIntent(intent: EventIntent): StateResult {
        if (intent.matchedTaskId == null) {
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
                sourceEvidenceIds = if (intent.sourceId.isNotBlank()) mutableListOf(intent.sourceId) else mutableListOf(),
                confidence = intent.confidence
            )
            activeCommitments[newId] = newCommitment
            return StateResult.Created(newCommitment)
        }

        if (!activeCommitments.containsKey(intent.matchedTaskId)) {
            return StateResult.Error("Unknown matchedTaskId: ${intent.matchedTaskId}")
        }

        val existingCommitment = activeCommitments[intent.matchedTaskId]!!
        val currentDeadlineTime = existingCommitment.deadline?.newTime ?: existingCommitment.deadline?.originalTime

        // Explicitly handle intents with no deadline
        if (currentDeadlineTime == null && intent.detectedTime == null) {
            return StateResult.ConflictDetected(
                Conflict(
                    existingCommitmentId = existingCommitment.id,
                    newEvidenceId = intent.sourceId,
                    description = "Cannot automatically merge intents with no deadlines"
                )
            )
        }

        // ACCUMULATE: If detectedTime matches existing deadline
        if (currentDeadlineTime == intent.detectedTime) {
            if (intent.sourceId.isNotBlank() && !existingCommitment.sourceEvidenceIds.contains(intent.sourceId)) {
                existingCommitment.sourceEvidenceIds.add(intent.sourceId)
            }
            existingCommitment.confidence = (existingCommitment.confidence + intent.confidence) / 2.0f
            return StateResult.Updated(existingCommitment)
        } 
        
        // CONFLICT: If detectedTime is different
        return StateResult.ConflictDetected(
            Conflict(
                existingCommitmentId = existingCommitment.id,
                newEvidenceId = intent.sourceId,
                description = "Time conflict detected: existing deadline is $currentDeadlineTime, new intent time is ${intent.detectedTime}"
            )
        )
    }
}
