package core.state

// Placeholder for kotlinx.serialization.Serializable (since Gradle is not configured)
annotation class Serializable

@Serializable
data class Deadline(
    val originalTime: Long,
    val newTime: Long?,
    val confidence: Float,
    val sourceEvidenceIds: List<String>
)
