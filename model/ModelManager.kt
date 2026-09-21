package model

sealed class ParsedModelResult {
    data class Success(val intent: String, val confidence: Float) : ParsedModelResult()
    data class Error(val message: String) : ParsedModelResult()
}

class ModelManager(private val inferenceEngine: LocalInference) {

    suspend fun processEvent(eventData: String): ParsedModelResult {
        val systemPrompt = "You are a helpful assistant. Parse the input into a structured intent."

        val result = inferenceEngine.generateResponse(systemPrompt, eventData)

        return when (result) {
            is InferenceResult.Success -> parseOutput(result.response)
            is InferenceResult.Failure -> ParsedModelResult.Error("Inference failed: ${result.reason}")
        }
    }

    private fun parseOutput(rawOutput: String): ParsedModelResult {
        try {
            if (!rawOutput.contains("INTENT:") || !rawOutput.contains("CONFIDENCE:")) {
                return ParsedModelResult.Error("Parsing failed: missing markers")
            }

            val intentPart = rawOutput.substringAfter("INTENT:").substringBefore(",").trim()
            if (intentPart.isEmpty()) {
                return ParsedModelResult.Error("Parsing failed: empty intent")
            }

            val confidencePart = rawOutput.substringAfter("CONFIDENCE:").trim()
            if (confidencePart.isEmpty()) {
                return ParsedModelResult.Error("Parsing failed: empty confidence")
            }

            val confidence = try {
                confidencePart.toFloat()
            } catch (e: NumberFormatException) {
                return ParsedModelResult.Error("Parsing failed: invalid confidence format")
            }

            if (confidence.isNaN() || confidence.isInfinite()) {
                return ParsedModelResult.Error("Parsing failed: confidence is not finite")
            }

            if (confidence < 0.0f || confidence > 1.0f) {
                return ParsedModelResult.Error("Parsing failed: confidence out of bounds")
            }

            return ParsedModelResult.Success(intentPart, confidence)
        } catch (e: Exception) {
            return ParsedModelResult.Error("Parsing failed: unexpected error")
        }
    }
}
