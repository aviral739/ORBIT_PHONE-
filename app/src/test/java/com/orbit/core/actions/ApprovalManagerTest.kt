package com.orbit.core.actions

import core.actions.ActionResult
import core.actions.ApprovalManager
import core.actions.ApprovalState
import core.actions.InMemoryApprovalManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovalManagerTest {

    private lateinit var approvalManager: ApprovalManager

    @Before
    fun setup() {
        approvalManager = InMemoryApprovalManager()
    }

    @Test
    fun `when approval is requested state transitions to PENDING`() {
        val request = approvalManager.requestApproval("delete_task")
        
        assertNotNull(request.approvalId)
        assertEquals("delete_task", request.actionId)
        assertEquals(ApprovalState.PENDING, request.state)
        
        val status = approvalManager.getStatus(request.approvalId)
        assertEquals(ApprovalState.PENDING, status)
    }

    @Test
    fun `pending action that is APPROVED transitions to approved state and returns Allowed`() {
        val request = approvalManager.requestApproval("delete_task")
        
        val result = approvalManager.approve(request.approvalId)
        
        assertEquals(ApprovalState.APPROVED, approvalManager.getStatus(request.approvalId))
        assertTrue(result is ActionResult.Allowed)
        assertEquals("delete_task", (result as ActionResult.Allowed).actionId)
    }

    @Test
    fun `pending action that is REJECTED transitions to denied state and returns DeniedBlocked`() {
        val request = approvalManager.requestApproval("delete_task")
        
        val result = approvalManager.reject(request.approvalId)
        
        assertEquals(ApprovalState.REJECTED, approvalManager.getStatus(request.approvalId))
        assertTrue(result is ActionResult.DeniedBlocked)
        assertEquals("delete_task", (result as ActionResult.DeniedBlocked).actionId)
    }

    @Test
    fun `duplicate approve on already approved action returns error`() {
        val request = approvalManager.requestApproval("delete_task")
        
        approvalManager.approve(request.approvalId)
        
        // Attempt duplicate approval
        val duplicateResult = approvalManager.approve(request.approvalId)
        
        assertTrue(duplicateResult is ActionResult.ExecutionFailure)
        assertTrue((duplicateResult as ActionResult.ExecutionFailure).error.contains("already APPROVED"))
        
        // State remains APPROVED
        assertEquals(ApprovalState.APPROVED, approvalManager.getStatus(request.approvalId))
    }

    @Test
    fun `duplicate reject on already rejected action returns error`() {
        val request = approvalManager.requestApproval("delete_task")
        
        approvalManager.reject(request.approvalId)
        
        // Attempt duplicate rejection
        val duplicateResult = approvalManager.reject(request.approvalId)
        
        assertTrue(duplicateResult is ActionResult.ExecutionFailure)
        assertTrue((duplicateResult as ActionResult.ExecutionFailure).error.contains("already REJECTED"))
        
        // State remains REJECTED
        assertEquals(ApprovalState.REJECTED, approvalManager.getStatus(request.approvalId))
    }

    @Test
    fun `approve on already rejected action returns error`() {
        val request = approvalManager.requestApproval("delete_task")
        
        approvalManager.reject(request.approvalId)
        
        // Attempt to approve a rejected action
        val invalidResult = approvalManager.approve(request.approvalId)
        
        assertTrue(invalidResult is ActionResult.ExecutionFailure)
        assertTrue((invalidResult as ActionResult.ExecutionFailure).error.contains("already REJECTED"))
    }
    
    @Test
    fun `approval manager never automatically approves an action`() {
        val request = approvalManager.requestApproval("some_action")
        
        // Ensure state is PENDING, not APPROVED
        assertEquals(ApprovalState.PENDING, approvalManager.getStatus(request.approvalId))
    }
}
