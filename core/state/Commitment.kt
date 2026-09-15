package core.state

import java.util.UUID

// Placeholder for kotlinx.serialization.Serializable (since Gradle is not configured)
annotation class Serializable

@Serializable
data class Deadline(
    val targetTime: Long?
)

@Serializable
data class Commitment(
    val id: String = UUID.randomUUID().toString(),
    val taskDescription: String,
    val deadline: Deadline?,
    val sourceEvidenceIds: MutableList<String> = mutableListOf(),
    var confidence: Float,
    var status: String = "PENDING"
)
