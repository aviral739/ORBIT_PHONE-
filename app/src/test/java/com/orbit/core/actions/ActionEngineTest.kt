package com.orbit.core.actions

import core.actions.ActionEngine
import core.actions.ActionPolicy
import core.actions.ActionRequest
import core.actions.ActionResult
import core.actions.ActionTier
import core.actions.ApprovalManager
import core.actions.PolicyProvider
import core.actions.SafeActions
import core.actions.ThresholdProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionEngineTest {

    private lateinit var actionEngine: ActionEngine
    private lateinit var policyProvider: MockPolicyProvider
    private lateinit var thresholdProvider: MockThresholdProvider
    
    class MockPolicyProvider : PolicyProvider {
        val policies = mutableMapOf<String, ActionPolicy>()
        override fun getPolicy(actionId: String): ActionPolicy? = policies[actionId]
    }

    class MockThresholdProvider : ThresholdProvider {
        val thresholds = mutableMapOf<String, Float>()
        override fun getThreshold(key: String): Float? = thresholds[key]
    }

    class MockApprovalManager : ApprovalManager {}
    class MockSafeActions : SafeActions {}

    @Before
    fun setup() {
        policyProvider = MockPolicyProvider()
        thresholdProvider = MockThresholdProvider()
        actionEngine = ActionEngine(policyProvider, thresholdProvider, MockApprovalManager(), MockSafeActions())
    }

    @Test
    fun `valid SAFE action returns Allowed`() {
        // REAL action ID and semantics from policies.json
        policyProvider.policies["generate_quick_reply"] = ActionPolicy(
            tier = ActionTier.SAFE, 
            requiresConfirmation = false,
            confidenceThresholdKey = null // Real semantic: policies.json does not map this
        )
        
        val result = actionEngine.evaluate(ActionRequest("generate_quick_reply", 0.8f))
        assertTrue(result is ActionResult.Allowed)
    }

    @Test
    fun `unknown action fails closed as InvalidUnknownAction`() {
        // missing policy
        val result = actionEngine.evaluate(ActionRequest("some_invented_action", 0.9f))
        assertTrue(result is ActionResult.InvalidUnknownAction)
    }

    @Test
    fun `missing required threshold configuration returns DeniedBlocked`() {
        // Simulating an action that has been configured with a threshold key.
        // We use a mock key because policies.json does not map real thresholds to actions.
        policyProvider.policies["mock_threshold_action"] = ActionPolicy(
            tier = ActionTier.SAFE,
            requiresConfirmation = false,
            confidenceThresholdKey = "mock_key_missing_in_provider"
        )
        
        val result = actionEngine.evaluate(ActionRequest("mock_threshold_action", 0.8f))
        assertTrue(result is ActionResult.DeniedBlocked)
        assertTrue((result as ActionResult.DeniedBlocked).reason.contains("Missing required threshold configuration"))
    }

    @Test
    fun `confidence below configured requirement returns DeniedBlocked`() {
        // Simulating an action that has been configured with a threshold key.
        policyProvider.policies["mock_threshold_action"] = ActionPolicy(
            tier = ActionTier.REVERSIBLE, 
            requiresConfirmation = false, 
            confidenceThresholdKey = "mock_key_present_in_provider"
        )
        thresholdProvider.thresholds["mock_key_present_in_provider"] = 0.65f
        
        val result = actionEngine.evaluate(ActionRequest("mock_threshold_action", 0.5f)) // 0.5 < 0.65
        assertTrue(result is ActionResult.DeniedBlocked)
    }

    @Test
    fun `sensitive action requiring confirmation returns ApprovalRequired`() {
        // REAL action ID and semantics from policies.json
        policyProvider.policies["delete_task"] = ActionPolicy(
            tier = ActionTier.SENSITIVE, 
            requiresConfirmation = true, 
            confidenceThresholdKey = null
        )
        
        val result = actionEngine.evaluate(ActionRequest("delete_task", 0.9f))
        assertTrue(result is ActionResult.ApprovalRequired)
    }

    @Test
    fun `sensitive action misconfigured without required confirmation returns DeniedBlocked`() {
        // REAL action ID but testing fail-closed on misconfiguration
        policyProvider.policies["escalate_to_desktop_solver"] = ActionPolicy(
            tier = ActionTier.SENSITIVE, 
            requiresConfirmation = false // Invalid configuration!
        )
        
        val result = actionEngine.evaluate(ActionRequest("escalate_to_desktop_solver", 0.9f))
        assertTrue(result is ActionResult.DeniedBlocked)
        assertTrue((result as ActionResult.DeniedBlocked).reason.contains("SENSITIVE actions must require confirmation"))
    }
}
