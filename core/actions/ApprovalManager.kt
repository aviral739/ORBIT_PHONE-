package core.actions

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ApprovalState {
    PENDING, APPROVED, REJECTED
}

data class ApprovalRequest(
    val approvalId: String,
    val actionId: String,
    val state: ApprovalState = ApprovalState.PENDING
)

interface ApprovalManager {
    fun requestApproval(actionId: String): ApprovalRequest { throw NotImplementedError("Stub implementation") }
    fun approve(approvalId: String): ActionResult { throw NotImplementedError("Stub implementation") }
    fun reject(approvalId: String): ActionResult { throw NotImplementedError("Stub implementation") }
    fun getStatus(approvalId: String): ApprovalState? { throw NotImplementedError("Stub implementation") }
}

class InMemoryApprovalManager : ApprovalManager {
    private val approvals = ConcurrentHashMap<String, ApprovalRequest>()

    override fun requestApproval(actionId: String): ApprovalRequest {
        val request = ApprovalRequest(
            approvalId = UUID.randomUUID().toString(),
            actionId = actionId
        )
        approvals[request.approvalId] = request
        return request
    }

    override fun approve(approvalId: String): ActionResult {
        val request = approvals[approvalId]
            ?: return ActionResult.ExecutionFailure("unknown", "Approval request not found")

        if (request.state != ApprovalState.PENDING) {
            return ActionResult.ExecutionFailure(request.actionId, "Cannot approve: request is already ${request.state}")
        }

        approvals[approvalId] = request.copy(state = ApprovalState.APPROVED)
        return ActionResult.Allowed(request.actionId)
    }

    override fun reject(approvalId: String): ActionResult {
        val request = approvals[approvalId]
            ?: return ActionResult.ExecutionFailure("unknown", "Approval request not found")

        if (request.state != ApprovalState.PENDING) {
            return ActionResult.ExecutionFailure(request.actionId, "Cannot reject: request is already ${request.state}")
        }

        approvals[approvalId] = request.copy(state = ApprovalState.REJECTED)
        return ActionResult.DeniedBlocked(request.actionId, "User rejected the action")
    }

    override fun getStatus(approvalId: String): ApprovalState? {
        return approvals[approvalId]?.state
    }
}
