package core.actions

class ActionEngine(
    private val safeActions: SafeActionsRegistry,
    private val approvalManager: ApprovalManager
) {
    fun routeAction(action: Action) {
        when (action.riskLevel) {
            RiskLevel.SAFE -> {
                safeActions.executeSafe(action)
            }
            RiskLevel.REVERSIBLE -> {
                safeActions.executeReversible(action)
            }
            RiskLevel.SENSITIVE -> {
                approvalManager.queueForApproval(action)
            }
        }
    }

    fun executeApprovedSensitiveAction(action: Action) {
        // Once approved by user, a sensitive action is executed
        println("Executing approved SENSITIVE action: ${action.description}")
        // Execution logic goes here
    }
}
