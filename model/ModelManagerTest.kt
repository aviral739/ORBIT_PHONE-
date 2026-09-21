package model

// Basic assertion helpers
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

class FakeLocalInference(private val result: InferenceResult) : LocalInference {
    override suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult {
        return result
    }
}

suspend fun runModelManagerTests() {
    println("Running ModelManager tests...")

    testA_validResponse()
    testB_anotherValidIntent()
    testC_confidenceZero()
    testD_confidenceOne()
    testE_invalidConfidenceAboveOne()
    testF_invalidNegativeConfidence()
    testG_invalidNonNumericConfidence()
    testH_nanInfinity()
    testI_missingIntent()
    testJ_missingConfidence()
    testK_missingFields()
    testL_intentWhitespace()
    testM_inferenceFailure()
    testN_modelNotLoaded()
    testO_deterministicParsing()

    println("All ModelManager tests passed")
}

suspend fun testA_validResponse() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: create_reminder, CONFIDENCE: 0.92"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Success, "Test A: Expected Success")
    val success = result as ParsedModelResult.Success
    assertEqual("create_reminder", success.intent, "Test A: Intent mismatch")
    assertEqual(0.92f, success.confidence, "Test A: Confidence mismatch")
}

suspend fun testB_anotherValidIntent() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: update_task, CONFIDENCE: 0.75"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Success, "Test B: Expected Success")
    val success = result as ParsedModelResult.Success
    assertEqual("update_task", success.intent, "Test B: Intent mismatch")
    assertEqual(0.75f, success.confidence, "Test B: Confidence mismatch")
}

suspend fun testC_confidenceZero() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: 0.0"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Success, "Test C: Expected Success")
    val success = result as ParsedModelResult.Success
    assertEqual(0.0f, success.confidence, "Test C: Confidence mismatch")
}

suspend fun testD_confidenceOne() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: 1.0"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Success, "Test D: Expected Success")
    val success = result as ParsedModelResult.Success
    assertEqual(1.0f, success.confidence, "Test D: Confidence mismatch")
}

suspend fun testE_invalidConfidenceAboveOne() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: 1.1"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test E: Expected Error")
}

suspend fun testF_invalidNegativeConfidence() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: -0.1"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test F: Expected Error")
}

suspend fun testG_invalidNonNumericConfidence() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: abc"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test G: Expected Error")
}

suspend fun testH_nanInfinity() {
    val managerNaN = ModelManager(FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: NaN")))
    assertTrue(managerNaN.processEvent("test") is ParsedModelResult.Error, "Test H: Expected Error for NaN")

    val managerInf = ModelManager(FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: Infinity")))
    assertTrue(managerInf.processEvent("test") is ParsedModelResult.Error, "Test H: Expected Error for Infinity")

    val managerNegInf = ModelManager(FakeLocalInference(InferenceResult.Success("INTENT: test, CONFIDENCE: -Infinity")))
    assertTrue(managerNegInf.processEvent("test") is ParsedModelResult.Error, "Test H: Expected Error for -Infinity")
}

suspend fun testI_missingIntent() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: , CONFIDENCE: 0.8"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test I: Expected Error")
}

suspend fun testJ_missingConfidence() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT: create_reminder, CONFIDENCE:"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test J: Expected Error")
}

suspend fun testK_missingFields() {
    val inference = FakeLocalInference(InferenceResult.Success("some random response"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test K: Expected Error")
}

suspend fun testL_intentWhitespace() {
    val inference = FakeLocalInference(InferenceResult.Success("INTENT:   create_reminder   , CONFIDENCE: 0.8"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Success, "Test L: Expected Success")
    val success = result as ParsedModelResult.Success
    assertEqual("create_reminder", success.intent, "Test L: Intent was not trimmed properly")
}

suspend fun testM_inferenceFailure() {
    val inference = FakeLocalInference(InferenceResult.Failure("Inference Timeout"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test M: Expected Error")
    val error = result as ParsedModelResult.Error
    assertEqual("Inference failed: Inference Timeout", error.message, "Test M: Error message mismatch")
}

suspend fun testN_modelNotLoaded() {
    val inference = FakeLocalInference(InferenceResult.Failure("Model Not Loaded"))
    val manager = ModelManager(inference)
    val result = manager.processEvent("test")

    assertTrue(result is ParsedModelResult.Error, "Test N: Expected Error")
    val error = result as ParsedModelResult.Error
    assertEqual("Inference failed: Model Not Loaded", error.message, "Test N: Error message mismatch")
}

suspend fun testO_deterministicParsing() {
    val rawResponse = "INTENT: something, CONFIDENCE: 0.5"
    val manager = ModelManager(FakeLocalInference(InferenceResult.Success(rawResponse)))

    val result1 = manager.processEvent("test1")
    val result2 = manager.processEvent("test2")

    assertTrue(result1 is ParsedModelResult.Success, "Test O: Expected Success")
    assertTrue(result2 is ParsedModelResult.Success, "Test O: Expected Success")

    val success1 = result1 as ParsedModelResult.Success
    val success2 = result2 as ParsedModelResult.Success

    assertEqual(success1.intent, success2.intent, "Test O: Intents differ")
    assertEqual(success1.confidence, success2.confidence, "Test O: Confidences differ")
}

suspend fun main() {
    runModelManagerTests()
}
