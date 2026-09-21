package model

sealed class InferenceResult {
    data class Success(val response: String) : InferenceResult()
    data class Failure(val reason: String) : InferenceResult()
}

interface LocalInference {
    suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult
}

class StubLocalInference : LocalInference {

    enum class ErrorState {
        NONE, MODEL_NOT_LOADED, TIMEOUT
    }

    private var currentErrorState = ErrorState.NONE

    fun simulateErrorState(state: ErrorState) {
        currentErrorState = state
    }

    override suspend fun generateResponse(systemPrompt: String, userInput: String): InferenceResult {
        return when (currentErrorState) {
            ErrorState.NONE -> InferenceResult.Success("Mocked successful response")
            ErrorState.MODEL_NOT_LOADED -> InferenceResult.Failure("Model Not Loaded")
            ErrorState.TIMEOUT -> InferenceResult.Failure("Inference Timeout")
        }
    }
}
