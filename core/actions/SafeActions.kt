package core.actions

import java.util.UUID

enum class RiskLevel {
    SAFE, REVERSIBLE, SENSITIVE
}

data class Action(
    val id: String = UUID.randomUUID().toString(),
    val riskLevel: RiskLevel,
    val description: String,
    val payload: String
)

interface SafeActionsRegistry {
    fun executeSafe(action: Action)
    fun executeReversible(action: Action): String // returns rollback token
    fun rollback(token: String)
}

class SafeActions : SafeActionsRegistry {
    private val rollbackLog = mutableMapOf<String, Action>()

    override fun executeSafe(action: Action) {
        println("Executing SAFE action autonomously: ${action.description}")
    }

    override fun executeReversible(action: Action): String {
        println("Executing REVERSIBLE action: ${action.description}")
        val rollbackToken = UUID.randomUUID().toString()
        rollbackLog[rollbackToken] = action
        println("Action executed. Rollback token generated: $rollbackToken")
        return rollbackToken
    }

    override fun rollback(token: String) {
        val action = rollbackLog[token]
        if (action != null) {
            println("Rolling back action: ${action.description}")
            rollbackLog.remove(token)
        } else {
            println("Rollback failed: Invalid or expired token.")
        }
    }
}
