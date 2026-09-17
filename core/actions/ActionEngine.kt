package core.actions

class ActionEngine(
    private val policyProvider: PolicyProvider,
    private val thresholdProvider: ThresholdProvider,
    private val approvalManager: ApprovalManager,
    private val safeActions: SafeActions
) {
    fun evaluate(request: ActionRequest): ActionResult {
        val policy = policyProvider.getPolicy(request.actionId)
        
        if (policy == null || policy.tier == ActionTier.UNKNOWN) {
            return ActionResult.InvalidUnknownAction(request.actionId, "Unknown action or missing policy")
        }

        policy.confidenceThresholdKey?.let { key ->
            val requiredConfidence = thresholdProvider.getThreshold(key)
            if (requiredConfidence == null) {
                return ActionResult.DeniedBlocked(request.actionId, "Missing required threshold configuration for key: $key")
            }
            
            if (request.confidence < requiredConfidence) {
                return ActionResult.DeniedBlocked(request.actionId, "Confidence below required threshold ($key)")
            }
        }

        // 6. SENSITIVE action must not accidentally evaluate as SAFE.
        if (policy.tier == ActionTier.SENSITIVE && !policy.requiresConfirmation) {
            return ActionResult.DeniedBlocked(request.actionId, "Invalid policy: SENSITIVE actions must require confirmation")
        }

        if (policy.requiresConfirmation) {
            return ActionResult.ApprovalRequired(request.actionId, "User confirmation required for tier ${policy.tier}")
        }
        
        return ActionResult.Allowed(request.actionId)
    }
}
