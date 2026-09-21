package core.actions

enum class ActionTier {
    SAFE, REVERSIBLE, SENSITIVE, UNKNOWN
}

data class ActionPolicy(
    val tier: ActionTier,
    val requiresConfirmation: Boolean,
    val confidenceThresholdKey: String? = null
)

interface PolicyProvider {
    fun getPolicy(actionId: String): ActionPolicy?
}

interface ThresholdProvider {
    fun getThreshold(key: String): Float?
}
