package model

/**
 * Interface representing the inference execution layer.
 * Target: Qwen3-0.6B locally on the phone (via Snapdragon NPU).
 * Escalation: iQOO Office Kit to a laptop running Ollama for heavy reasoning.
 */
interface LocalInference {
    suspend fun executeInference(prompt: String, fallbackToLaptop: Boolean = false): String
}

class LocalInferenceEngine : LocalInference {
    override suspend fun executeInference(prompt: String, fallbackToLaptop: Boolean): String {
        // Simulated hardware inference execution.
        if (fallbackToLaptop) {
            // Escalate to Ollama on Laptop for heavy reasoning
            println("Escalating to laptop (Ollama) for heavy reasoning...")
            return "{ \"status\": \"laptop_inference_simulated\" }"
        }
        
        // Local execution via Snapdragon NPU (Qwen3-0.6B)
        println("Executing fast on-device inference via Snapdragon NPU (Qwen3-0.6B)...")
        return "{ \"status\": \"local_inference_simulated\" }"
    }
}
