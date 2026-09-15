package core.actions

class ApprovalManager {
    private val pendingApprovals = mutableMapOf<String, Action>()

    fun queueForApproval(action: Action) {
        println("SENSITIVE Action queued for user approval: ${action.description}")
        pendingApprovals[action.id] = action
        notifyUser(action)
    }

    fun approveAction(actionId: String, onApproved: (Action) -> Unit) {
        val action = pendingApprovals.remove(actionId)
        if (action != null) {
            println("User approved action: ${action.description}")
            onApproved(action)
        } else {
            println("Action not found or already processed.")
        }
    }

    fun rejectAction(actionId: String) {
        val action = pendingApprovals.remove(actionId)
        if (action != null) {
            println("User rejected action: ${action.description}")
        }
    }

    private fun notifyUser(action: Action) {
        // Simulated notification to the user
        println("NOTIFICATION: Action requires your approval -> ${action.description}")
    }
}
