package core.state

// Placeholder for kotlinx.serialization.Serializable (since Gradle is not configured)
annotation class Serializable

@Serializable
data class Conflict(
    val existingCommitmentId: String,
    val newEvidenceId: String,
    val description: String
)
