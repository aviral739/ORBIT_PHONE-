package core.context

import core.decision.Event
import java.util.regex.Pattern

/**
 * ContextEngine groups related Events into logical Contexts.
 *
 * It uses deterministic token-based matching to group related events.
 * No external dependencies, no ML, no network calls.
 *
 * Matching strategy:
 * - Tokenize event text (lowercase, alphanumeric tokens)
 * - Compare token overlap with existing context topic/summary
 * - Use Jaccard-style similarity for deterministic matching
 * - Threshold: 0.25 minimum similarity for a match
 *
 * Context ID strategy:
 * - Derived from normalized topic (lowercase, alphanumeric only)
 * - Collisions handled by appending counter
 *
 * Confidence strategy:
 * - Based on token overlap ratio
 * - Heuristic, not calibrated probability
 * - Range: 0.0 to 1.0
 */
class ContextEngine(
    private val contextStore: ContextStore
) {

    companion object {
        /**
         * Minimum similarity threshold for considering an event related to an existing context.
         * Jaccard similarity of token sets must exceed this value.
         */
        private const val MIN_SIMILARITY_THRESHOLD = 0.25

        /**
         * Pattern for tokenizing text into words.
         * Splits on non-word characters.
         */
        private val TOKEN_PATTERN = Pattern.compile("\\W+")

        /**
         * Stop words that are filtered out during tokenization.
         */
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
            "by", "from", "up", "down", "out", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why", "how", "all", "each",
            "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only",
            "own", "same", "so", "than", "too", "very", "can", "will", "just", "don",
            "should", "now", "is", "are", "was", "were", "be", "been", "being", "have",
            "has", "had", "do", "does", "did", "doing"
        )
    }

    /**
     * Processes a single event, either adding it to an existing context
     * or creating a new context.
     *
     * @param event The event to process
     * @return The Context the event was added to (new or existing)
     */
    fun processEvent(event: Event): Context {
        // Find the best matching existing context
        val existingContexts = contextStore.getAll()
        val bestMatch = findBestMatch(event, existingContexts)

        return if (bestMatch != null) {
            // Add event to existing context
            addEventToContext(bestMatch, event)
        } else {
            // Create new context
            createNewContext(event)
        }
    }

    /**
     * Processes multiple events in order.
     *
     * @param events List of events to process
     * @return List of contexts created/updated (one per event)
     */
    fun processEvents(events: List<Event>): List<Context> {
        val results = mutableListOf<Context>()
        for (event in events) {
            results.add(processEvent(event))
        }
        return results
    }

    /**
     * Finds existing contexts that are related to the given event.
     *
     * @param event The event to find related contexts for
     * @return List of related contexts, ordered by similarity (highest first)
     */
    fun findRelatedContexts(event: Event): List<Context> {
        val existingContexts = contextStore.getAll()
        val eventTokens = tokenize(event.text)

        return existingContexts
            .map { context ->
                val similarity = computeSimilarity(eventTokens, context)
                Pair(context, similarity)
            }
            .filter { it.second >= MIN_SIMILARITY_THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Finds the best matching context for an event.
     * Returns null if no context meets the similarity threshold.
     */
    private fun findBestMatch(event: Event, existingContexts: List<Context>): Context? {
        val eventTokens = tokenize(event.text)
        var bestMatch: Context? = null
        var bestSimilarity = 0.0

        for (context in existingContexts) {
            val similarity = computeSimilarity(eventTokens, context)
            if (similarity > bestSimilarity && similarity >= MIN_SIMILARITY_THRESHOLD) {
                bestSimilarity = similarity
                bestMatch = context
            }
        }

        return bestMatch
    }

    /**
     * Adds an event to an existing context, updating its fields.
     * Does not duplicate event IDs.
     */
    private fun addEventToContext(context: Context, event: Event): Context {
        // Check if event ID already exists in context
        val existingEventIds = context.eventIds.toMutableList()
        if (event.id !in existingEventIds) {
            existingEventIds.add(event.id)
        }

        // Generate updated summary (deterministic)
        val updatedSummary = buildSummary(context, event)

        // Generate updated topic (deterministic)
        val updatedTopic = buildTopic(context, event)

        // Compute confidence based on match quality
        val eventTokens = tokenize(event.text)
        val contextTokens = tokenize(context.topic + " " + context.summary)
        val similarity = computeTokenSimilarity(eventTokens, contextTokens)
        val confidence = (similarity * 0.8 + 0.2).coerceIn(0.0, 1.0)

        val updatedContext = Context(
            id = context.id,
            topic = updatedTopic,
            eventIds = existingEventIds,
            summary = updatedSummary,
            confidence = confidence
        )

        contextStore.save(updatedContext)
        return updatedContext
    }

    /**
     * Creates a new context for an event.
     */
    private fun createNewContext(event: Event): Context {
        val tokens = tokenize(event.text)
        val topic = buildTopicFromTokens(tokens, event)
        val summary = buildSummaryFromEvent(event)
        val confidence = computeInitialConfidence(event)

        // Generate deterministic ID from topic
        val baseId = generateContextId(topic)
        val finalId = resolveIdCollision(baseId)

        val context = Context(
            id = finalId,
            topic = topic,
            eventIds = listOf(event.id),
            summary = summary,
            confidence = confidence
        )

        contextStore.save(context)
        return context
    }

    /**
     * Computes Jaccard similarity between event tokens and context topic+summary.
     */
    private fun computeSimilarity(eventTokens: Set<String>, context: Context): Double {
        val contextTokens = tokenize(context.topic + " " + context.summary)
        return computeTokenSimilarity(eventTokens, contextTokens)
    }

    /**
     * Computes Jaccard similarity between two token sets.
     */
    private fun computeTokenSimilarity(tokensA: Set<String>, tokensB: Set<String>): Double {
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union > 0) intersection.toDouble() / union else 0.0
    }

    /**
     * Tokenizes text into meaningful words.
     * Lowercases, removes stop words, filters short tokens.
     */
    private fun tokenize(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        return TOKEN_PATTERN.split(text.lowercase())
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }

    /**
     * Builds a summary by combining existing summary with new event info.
     * Deterministic: keeps first 200 chars of combined text.
     */
    private fun buildSummary(context: Context, event: Event): String {
        val combined = "${context.summary} | ${event.text}"
        return if (combined.length <= 200) combined else combined.substring(0, 200)
    }

    /**
     * Creates initial summary from a single event.
     */
    private fun buildSummaryFromEvent(event: Event): String {
        val text = event.text.trim()
        return if (text.length <= 200) text else text.substring(0, 200)
    }

    /**
     * Updates topic by combining existing topic with new event topic signals.
     */
    private fun buildTopic(context: Context, event: Event): String {
        val eventTopic = extractTopicFromEvent(event)
        if (eventTopic.isBlank()) return context.topic

        val existingLower = context.topic.lowercase()
        val newLower = eventTopic.lowercase()

        // If topics are already similar, keep existing
        if (existingLower == newLower || existingLower.contains(newLower) || newLower.contains(existingLower)) {
            return context.topic
        }

        // Otherwise combine (max 60 chars)
        val combined = "${context.topic}, ${eventTopic}"
        return if (combined.length <= 60) combined else combined.substring(0, 60)
    }

    /**
     * Extracts a topic label from event text.
     * Uses first few meaningful words.
     */
    private fun buildTopicFromTokens(tokens: Set<String>, event: Event): String {
        // Extract from event type first
        val typeTopic = event.type.lowercase()
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.length >= 3 }
            .take(3)
            .joinToString(" ")

        if (typeTopic.isNotBlank()) {
            return typeTopic.capitalize()
        }

        // Fallback: use first few tokens
        val topicTokens = tokens.take(3).joinToString(" ")
        return if (topicTopic.isNotBlank()) typeTopic.capitalize() else topicTopic.capitalize()
    }

    /**
     * Extracts topic from event text for summary building.
     */
    private fun extractTopicFromEvent(event: Event): String {
        // Use event type as primary topic signal
        val typeLower = event.type.lowercase()
        if (typeLower.isNotBlank()) {
            return typeLower.replace("_", " ").replace("-", " ").capitalize()
        }
        return ""
    }

    /**
     * Computes initial confidence for a new context.
     * Based on text length and content richness.
     */
    private fun computeInitialConfidence(event: Event): Double {
        val text = event.text.trim()
        var confidence = 0.5

        when {
            text.length < 20 -> confidence = 0.4
            text.length < 50 -> confidence = 0.5
            text.length < 100 -> confidence = 0.6
            else -> confidence = 0.7
        }

        // Boost for actionable keywords
        val textLower = event.text.lowercase()
        if (textLower.contains(Regex("\\b(deadline|meeting|task|remind|schedule|appointment)\\b"))) {
            confidence += 0.1
        }

        return confidence.coerceIn(0.0, 1.0)
    }

    /**
     * Generates a deterministic context ID from topic.
     */
    private fun generateContextId(topic: String): String {
        val normalized = topic.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim { it == '_' }
        return "ctx_$normalized"
    }

    /**
     * Resolves ID collisions by appending a counter.
     * Deterministic: always produces the same result for same base ID.
     */
    private fun resolveIdCollision(baseId: String): String {
        if (!contextStore.contains(baseId)) {
            return baseId
        }

        var counter = 1
        var candidateId = "${baseId}_$counter"
        while (contextStore.contains(candidateId)) {
            counter++
            candidateId = "${baseId}_$counter"
        }
        return candidateId
    }
}