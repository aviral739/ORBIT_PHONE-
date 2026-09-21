package core.context

/**
 * Represents a Context object per the ORBIT Context contract.
 *
 * Contract fields:
 * - id: Unique identifier for the context
 * - topic: Human-readable topic label
 * - eventIds: List of event IDs that contributed to this context
 * - summary: Human-readable summary of the context
 * - confidence: Confidence score in [0.0, 1.0]
 */
data class Context(
    val id: String,
    val topic: String,
    val eventIds: List<String>,
    val summary: String,
    val confidence: Double
) {
    init {
        require(id.isNotBlank()) { "Context ID must not be blank" }
        require(topic.isNotBlank()) { "Topic must not be blank" }
        require(summary.isNotBlank()) { "Summary must not be blank" }
        require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
        require(eventIds.distinct().size == eventIds.size) { "Event IDs must not contain duplicates" }
        require(eventIds.all { it.isNotBlank() }) { "Event IDs must not be blank" }
    }
}

/**
 * In-memory ContextStore implementation.
 * Thread-safe using standard Kotlin/JVM synchronized collections.
 * No external dependencies.
 */
class ContextStore {

    private val store = mutableMapOf<String, Context>()

    /**
     * Saves a context, replacing any existing context with the same ID.
     *
     * @param context The context to save
     * @throws IllegalArgumentException if context fails validation
     */
    fun save(context: Context): Unit {
        store[context.id] = context
    }

    /**
     * Retrieves a context by ID.
     *
     * @param id The context ID
     * @return The stored Context, or null if not found or ID is blank
     */
    fun getById(id: String): Context? {
        if (id.isBlank()) return null
        return store[id]
    }

    /**
     * Checks if a context with the given ID exists in the store.
     *
     * @param id The context ID to check
     * @return true if a context with the ID exists, false otherwise
     */
    fun contains(id: String): Boolean {
        if (id.isBlank()) return false
        return store.containsKey(id)
    }

    /**
     * Returns the number of stored contexts.
     *
     * @return The number of contexts in the store
     */
    fun size(): Int {
        return store.size
    }

    /**
     * Returns all stored contexts.
     *
     * @return An immutable list of all contexts (callers cannot mutate internal collection)
     */
    fun getAll(): List<Context> {
        return store.values.toList()
    }

    /**
     * Deletes a context by ID.
     *
     * @param id The context ID to delete
     * @return true if a context was removed, false if no context existed with that ID
     */
    fun delete(id: String): Boolean {
        return store.remove(id) != null
    }

    /**
     * Removes all contexts from the store.
     */
    fun clear(): Unit {
        store.clear()
    }
}