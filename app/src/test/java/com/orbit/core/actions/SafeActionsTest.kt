package com.orbit.core.actions

import core.actions.ExecutionResult
import core.actions.SafeActions
import core.actions.StubSafeActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafeActionsTest {

    private lateinit var safeActions: SafeActions

    @Before
    fun setup() {
        safeActions = StubSafeActions()
    }

    @Test
    fun `AUTHORIZED action executes stub and returns successful execution result`() {
        val result = safeActions.execute("STORE_CONTEXT")
        
        assertTrue(result.success)
        assertEquals("STORE_CONTEXT", result.actionId)
        assertTrue(result.auditLog.contains("Context stored securely"))
    }

    @Test
    fun `stubbed external execution failure properly surfaces failure and not success`() {
        val payload = mapOf("simulate_failure" to true)
        val result = safeActions.execute("SEND_MESSAGE", payload)
        
        assertFalse(result.success)
        assertEquals("SEND_MESSAGE", result.actionId)
        assertEquals("Simulated execution failure", result.error)
        assertTrue(result.auditLog.contains("encountered a simulated failure"))
    }

    @Test
    fun `SafeActions produces structured audit information for unknown actions`() {
        val result = safeActions.execute("SOME_UNKNOWN_ACTION")
        
        assertTrue(result.success)
        assertEquals("SOME_UNKNOWN_ACTION", result.actionId)
        assertTrue(result.auditLog.contains("generic stub"))
    }
    
    @Test
    fun `verify specific domain actions produce specific audit logs`() {
        val compressResult = safeActions.execute("COMPRESS_NOTIFICATION")
        assertTrue(compressResult.auditLog.contains("Successfully compressed notification"))
        
        val sendResult = safeActions.execute("SEND_MESSAGE")
        assertTrue(sendResult.auditLog.contains("Message dispatched"))
    }
}
