package model

// Simple assertion helpers
fun assertEqual(expected: Any?, actual: Any?, message: String) {
    if (expected != actual) {
        throw AssertionError("$message - Expected: $expected, but was: $actual")
    }
}

fun assertTrue(condition: Boolean, message: String) {
    if (!condition) {
        throw AssertionError("$message - Condition was false")
    }
}

suspend fun runAllTests() {
    println("Running LocalInference tests...")
    
    testA_defaultState()
    testB_defaultSuccessResponse()
    testC_modelNotLoaded()
    testD_timeout()
    testE_stateRecovery()
    testF_errorStateSwitching()
    testG_systemPromptAccepted()
    testH_userInputAccepted()
    testI_stubBehaviorIsDeterministic()
    testJ_independentInstances()
    
    println("All LocalInference tests passed")
}

suspend fun testA_defaultState() {
    val inference = StubLocalInference()
    val result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Success, "Test A Failed: Expected Success")
}

suspend fun testB_defaultSuccessResponse() {
    val inference = StubLocalInference()
    val result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Success, "Test B Failed: Expected Success")
    
    when (result) {
        is InferenceResult.Success -> {
            assertEqual("Mocked successful response", result.response, "Test B Failed: Unexpected success response")
        }
        is InferenceResult.Failure -> {
            assertTrue(false, "Test B Failed: Expected Success")
        }
    }
}

suspend fun testC_modelNotLoaded() {
    val inference = StubLocalInference()
    inference.simulateErrorState(StubLocalInference.ErrorState.MODEL_NOT_LOADED)
    val result = inference.generateResponse("system", "hello")
    
    assertTrue(result is InferenceResult.Failure, "Test C Failed: Expected Failure")
    
    when (result) {
        is InferenceResult.Success -> assertTrue(false, "Test C Failed: Expected Failure")
        is InferenceResult.Failure -> {
            assertEqual("Model Not Loaded", result.reason, "Test C Failed: Unexpected failure reason")
        }
    }
}

suspend fun testD_timeout() {
    val inference = StubLocalInference()
    inference.simulateErrorState(StubLocalInference.ErrorState.TIMEOUT)
    val result = inference.generateResponse("system", "hello")
    
    assertTrue(result is InferenceResult.Failure, "Test D Failed: Expected Failure")
    
    when (result) {
        is InferenceResult.Success -> assertTrue(false, "Test D Failed: Expected Failure")
        is InferenceResult.Failure -> {
            assertEqual("Inference Timeout", result.reason, "Test D Failed: Unexpected failure reason")
        }
    }
}

suspend fun testE_stateRecovery() {
    val inference = StubLocalInference()
    
    inference.simulateErrorState(StubLocalInference.ErrorState.MODEL_NOT_LOADED)
    var result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Failure, "Test E Failed: Expected Failure initially")
    
    inference.simulateErrorState(StubLocalInference.ErrorState.NONE)
    result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Success, "Test E Failed: Expected Success after recovery")
}

suspend fun testF_errorStateSwitching() {
    val inference = StubLocalInference()
    
    // NONE
    var result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Success, "Test F Failed: Expected Success for NONE")
    
    // MODEL_NOT_LOADED
    inference.simulateErrorState(StubLocalInference.ErrorState.MODEL_NOT_LOADED)
    result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Failure, "Test F Failed: Expected MODEL_NOT_LOADED")
    when (result) {
        is InferenceResult.Success -> assertTrue(false, "Test F Failed: Expected Failure")
        is InferenceResult.Failure -> assertEqual("Model Not Loaded", result.reason, "Test F Failed: Expected MODEL_NOT_LOADED reason")
    }
    
    // TIMEOUT
    inference.simulateErrorState(StubLocalInference.ErrorState.TIMEOUT)
    result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Failure, "Test F Failed: Expected TIMEOUT")
    when (result) {
        is InferenceResult.Success -> assertTrue(false, "Test F Failed: Expected Failure")
        is InferenceResult.Failure -> assertEqual("Inference Timeout", result.reason, "Test F Failed: Expected TIMEOUT reason")
    }
    
    // NONE
    inference.simulateErrorState(StubLocalInference.ErrorState.NONE)
    result = inference.generateResponse("system", "hello")
    assertTrue(result is InferenceResult.Success, "Test F Failed: Expected Success for NONE at the end")
}

suspend fun testG_systemPromptAccepted() {
    val inference = StubLocalInference()
    val result = inference.generateResponse(systemPrompt = "You are ORBIT.", userInput = "What should I do?")
    assertTrue(result is InferenceResult.Success, "Test G Failed: Expected Success")
}

suspend fun testH_userInputAccepted() {
    val inference = StubLocalInference()
    inference.simulateErrorState(StubLocalInference.ErrorState.TIMEOUT)
    
    val result1 = inference.generateResponse("system", "input 1")
    val result2 = inference.generateResponse("system", "input 2")
    
    assertTrue(result1 is InferenceResult.Failure, "Test H Failed: Expected Failure for input 1")
    assertTrue(result2 is InferenceResult.Failure, "Test H Failed: Expected Failure for input 2")
}

suspend fun testI_stubBehaviorIsDeterministic() {
    val inference1 = StubLocalInference()
    val inference2 = StubLocalInference()
    
    val result1 = inference1.generateResponse("system", "input")
    val result2 = inference2.generateResponse("system", "input")
    
    assertTrue(result1 is InferenceResult.Success && result2 is InferenceResult.Success, "Test I Failed: Both should be Success")
    
    val r1 = result1 as InferenceResult.Success
    val r2 = result2 as InferenceResult.Success
    assertEqual(r1.response, r2.response, "Test I Failed: Responses should match")
}

suspend fun testJ_independentInstances() {
    val inferenceA = StubLocalInference()
    val inferenceB = StubLocalInference()
    
    inferenceA.simulateErrorState(StubLocalInference.ErrorState.TIMEOUT)
    // inferenceB remains NONE
    
    val resultA = inferenceA.generateResponse("system", "hello")
    val resultB = inferenceB.generateResponse("system", "hello")
    
    assertTrue(resultA is InferenceResult.Failure, "Test J Failed: A should be Failure")
    assertTrue(resultB is InferenceResult.Success, "Test J Failed: B should be Success")
}

suspend fun main() {
    runAllTests()
}
