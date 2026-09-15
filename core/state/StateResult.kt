package core.state

sealed class StateResult {
    data class Created(val commitment: Commitment) : StateResult()
    data class Updated(val commitment: Commitment) : StateResult()
    data class ConflictDetected(val conflict: Conflict) : StateResult()
    data class Error(val message: String) : StateResult()
    data class Ignored(val reason: String) : StateResult()
    data class Unresolved(val reason: String) : StateResult()
}
