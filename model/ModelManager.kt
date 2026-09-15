package model

/**
 * Manages prompt construction, execution dispatching, and orchestration of 
 * classification, context extraction, and complex reasoning pipelines.
 */
class ModelManager(private val inferenceEngine: LocalInference) {
    
    suspend fun classifyEvent(eventContext: String): String {
        val promptTemplate = loadPrompt("classification.txt")
        val prompt = promptTemplate.replace("{event_context}", eventContext)
        // Classification is fast and handled strictly locally
        return inferenceEngine.executeInference(prompt, fallbackToLaptop = false)
    }

    suspend fun extractContext(contextData: String): String {
        val promptTemplate = loadPrompt("context.txt")
        val prompt = promptTemplate.replace("{context_data}", contextData)
        // Context extraction is also handled locally
        return inferenceEngine.executeInference(prompt, fallbackToLaptop = false)
    }

    suspend fun executeReasoning(systemState: String, triggerEvent: String): String {
        val promptTemplate = loadPrompt("reasoning.txt")
        val prompt = promptTemplate
            .replace("{system_state}", systemState)
            .replace("{trigger_event}", triggerEvent)
        // Reasoning is heavy; escalates to laptop running Ollama
        return inferenceEngine.executeInference(prompt, fallbackToLaptop = true)
    }

    private fun loadPrompt(filename: String): String {
        // Simulated loading of prompt files from the prompts directory
        // In actual implementation, this would read from assets/ or raw resources
        return "Loaded prompt template for $filename"
    }
}
