package core.context

/**
 * In-memory ContextStore implementation.
 * No external dependencies.
 */
class ContextStore {

    private val store = mutableMapOf<String, Context>()

    /**
     * Saves a context, replacing any existing context with the same ID.
     *
     * @param context The context to save
     * @throws IllegalArgumentException if context ID is blank
     */
    fun save(context: Context): Unit {
        if (context.id.isBlank()) {
            throw IllegalArgumentException("Context ID must not be blank")
        }
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
     * @return A copy of the stored contexts (callers cannot mutate internal collection)
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