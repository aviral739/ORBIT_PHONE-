package core.actions

data class ExecutionResult(
    val actionId: String,
    val success: Boolean,
    val auditLog: String,
    val error: String? = null
)

interface SafeActions {
    fun execute(actionId: String, payload: Map<String, Any> = emptyMap()): ExecutionResult {
        throw NotImplementedError("Stub implementation")
    }
}

class StubSafeActions : SafeActions {
    override fun execute(actionId: String, payload: Map<String, Any>): ExecutionResult {
        val simulateFailure = payload["simulate_failure"] as? Boolean ?: false
        
        if (simulateFailure) {
            return ExecutionResult(
                actionId = actionId,
                success = false,
                auditLog = "Attempted to execute $actionId but encountered a simulated failure.",
                error = "Simulated execution failure"
            )
        }

        val auditDetails = when (actionId) {
            "COMPRESS_NOTIFICATION" -> "Successfully compressed notification."
            "STORE_CONTEXT" -> "Context stored securely."
            "SEND_MESSAGE" -> "Message dispatched to communication bridge."
            else -> "Action $actionId executed via generic stub."
        }
        
        return ExecutionResult(
            actionId = actionId,
            success = true,
            auditLog = "Executed side-effect for $actionId. Details: $auditDetails"
        )
    }
}
