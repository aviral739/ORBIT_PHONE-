package com.orbit.model

import model.LocalInference
import model.StubLocalInference
import model.InferenceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class LocalInferenceTest {

    private lateinit var inferenceEngine: LocalInference

    @Before
    fun setup() {
        inferenceEngine = StubLocalInference()
    }

    @Test
    fun `when provided with valid prompt, returns successful structured response`() = runBlocking {
        val result = inferenceEngine.generateResponse("System Prompt", "User Input")
        
        assertTrue(result is InferenceResult.Success)
        val successResult = result as InferenceResult.Success
        assertEquals("Mocked successful response", successResult.response)
    }

    @Test
    fun `when model is not loaded, returns ModelNotLoaded error`() = runBlocking {
        val stub = inferenceEngine as StubLocalInference
        stub.simulateErrorState(StubLocalInference.ErrorState.MODEL_NOT_LOADED)
        
        val result = stub.generateResponse("System Prompt", "User Input")
        
        assertTrue(result is InferenceResult.Failure)
        val failureResult = result as InferenceResult.Failure
        assertEquals("Model Not Loaded", failureResult.reason)
    }

    @Test
    fun `when inference times out, returns Timeout error`() = runBlocking {
        val stub = inferenceEngine as StubLocalInference
        stub.simulateErrorState(StubLocalInference.ErrorState.TIMEOUT)
        
        val result = stub.generateResponse("System Prompt", "User Input")
        
        assertTrue(result is InferenceResult.Failure)
        val failureResult = result as InferenceResult.Failure
        assertEquals("Inference Timeout", failureResult.reason)
    }
}
