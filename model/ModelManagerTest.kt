package model

suspend fun runModelManagerTests() {
    println("Running ModelManager tests...")
    testModelManager_success()
    testModelManager_inferenceError()
    testModelManager_parsingFailure_malformedString()
    testModelManager_parsingFailure_exception()
    println("All ModelManager tests passed")
}

suspend fun testModelManager_success() {
    val mockInference = object : LocalInference {
        override suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult {
            return InferenceResult.Success("INTENT: SEARCH, CONFIDENCE: 0.95")
        }
    }
    val manager = ModelManager(mockInference)
    val result = manager.processEvent("find something")
    
    check(result is ParsedModelResult.Success) { "Expected Success but got $result" }
    check(result.intent == "SEARCH") { "Expected intent SEARCH but got ${result.intent}" }
    check(result.confidence == 0.95f) { "Expected confidence 0.95 but got ${result.confidence}" }
}

suspend fun testModelManager_inferenceError() {
    val mockInference = object : LocalInference {
        override suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult {
            return InferenceResult.Failure("Model Not Loaded")
        }
    }
    val manager = ModelManager(mockInference)
    val result = manager.processEvent("find something")
    
    check(result is ParsedModelResult.Error) { "Expected Error but got $result" }
    check(result.message == "Inference failed: Model Not Loaded") { "Unexpected error message: ${result.message}" }
}

suspend fun testModelManager_parsingFailure_malformedString() {
    val mockInference = object : LocalInference {
        override suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult {
            return InferenceResult.Success("Mocked successful response")
        }
    }
    val manager = ModelManager(mockInference)
    val result = manager.processEvent("find something")
    
    check(result is ParsedModelResult.Error) { "Expected Error but got $result" }
    check(result.message == "Parsing failed: malformed format") { "Unexpected error message: ${result.message}" }
}

suspend fun testModelManager_parsingFailure_exception() {
    val mockInference = object : LocalInference {
        override suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult {
            return InferenceResult.Success("INTENT: SEARCH, CONFIDENCE: not_a_float")
        }
    }
    val manager = ModelManager(mockInference)
    val result = manager.processEvent("find something")
    
    check(result is ParsedModelResult.Error) { "Expected Error but got $result" }
    check(result.message.startsWith("Parsing failed:")) { "Unexpected error message: ${result.message}" }
}
