package core.actions

data class ActionRequest(
    val actionId: String,
    val confidence: Float,
    val evidenceIds: List<String> = emptyList()
)
