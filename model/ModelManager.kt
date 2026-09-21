package model

sealed class ParsedModelResult {
    data class Success(val intent: String, val confidence: Float) : ParsedModelResult()
    data class Error(val message: String) : ParsedModelResult()
}

class ModelManager(private val inferenceEngine: LocalInference) {

    suspend fun processEvent(eventData: String): ParsedModelResult {
        // Dummy system prompt for now
        val systemPrompt = "You are a helpful assistant. Parse the input into a structured intent."
        
        val result = inferenceEngine.generateResponse(systemPrompt, eventData)
        
        return when (result) {
            is InferenceResult.Success -> parseOutput(result.response)
            is InferenceResult.Failure -> ParsedModelResult.Error("Inference failed: ${result.reason}")
        }
    }
    
    private fun parseOutput(rawOutput: String): ParsedModelResult {
        // Very basic mock parser: expects format "INTENT: <name>, CONFIDENCE: <float>"
        return try {
            if (rawOutput.contains("INTENT:") && rawOutput.contains("CONFIDENCE:")) {
                val intentPart = rawOutput.substringAfter("INTENT:").substringBefore(",").trim()
                val confidencePart = rawOutput.substringAfter("CONFIDENCE:").trim()
                val confidence = confidencePart.toFloat()
                ParsedModelResult.Success(intentPart, confidence)
            } else {
                ParsedModelResult.Error("Parsing failed: malformed format")
            }
        } catch (e: Exception) {
            ParsedModelResult.Error("Parsing failed: ${e.message ?: "unknown error"}")
        }
    }
}
