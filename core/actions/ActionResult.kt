package core.actions

sealed class ActionResult {
    data class Allowed(val actionId: String) : ActionResult()
    data class ApprovalRequired(val actionId: String, val reason: String) : ActionResult()
    data class DeniedBlocked(val actionId: String, val reason: String) : ActionResult()
    data class ConflictEscalationRequired(val actionId: String, val reason: String) : ActionResult()
    data class InvalidUnknownAction(val actionId: String, val reason: String) : ActionResult()
    data class ExecutionFailure(val actionId: String, val error: String) : ActionResult()
}
