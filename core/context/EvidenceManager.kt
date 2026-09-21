package core.context

import core.decision.Event

/**
 * In-memory EvidenceManager implementation.
 * Stores and retrieves Evidence records deterministically.
 * No external dependencies.
 */
class EvidenceManager {

    private val store = mutableMapOf<String, Evidence>()

    /**
     * Creates an Evidence record from an Event and a caller-supplied confidence.
     *
     * The Evidence ID is deterministic: "evd_<eventId>".
     *
     * @param event The event to preserve as evidence
     * @param confidence The confidence score supplied by the caller
     * @return The created Evidence record
     */
    fun createEvidence(event: Event, confidence: Double): Evidence {
        return Evidence(
            id = generateEvidenceId(event.id),
            eventId = event.id,
            source = event.source.name,
            text = event.text,
            timestamp = event.timestamp.toString(),
            confidence = confidence
        )
    }

    /**
     * Saves an evidence record, replacing any existing record with the same ID.
     *
     * @param evidence The evidence to save
     * @throws IllegalArgumentException if evidence ID is blank
     */
    fun save(evidence: Evidence): Unit {
        if (evidence.id.isBlank()) {
            throw IllegalArgumentException("Evidence ID must not be blank")
        }
        store[evidence.id] = evidence
    }

    /**
     * Retrieves an evidence record by ID.
     *
     * @param id The evidence ID
     * @return The stored Evidence, or null if not found or ID is blank
     */
    fun getById(id: String): Evidence? {
        if (id.isBlank()) return null
        return store[id]
    }

    /**
     * Retrieves all evidence records for a given event ID.
     *
     * @param eventId The event ID to search for
     * @return A copy of matching evidence records, or empty list if none found
     */
    fun getByEventId(eventId: String): List<Evidence> {
        if (eventId.isBlank()) return emptyList()
        return store.values.filter { it.eventId == eventId }
    }

    /**
     * Checks if an evidence record with the given ID exists.
     *
     * @param id The evidence ID to check
     * @return true if a record with the ID exists, false otherwise
     */
    fun contains(id: String): Boolean {
        if (id.isBlank()) return false
        return store.containsKey(id)
    }

    /**
     * Returns the number of stored evidence records.
     *
     * @return The number of evidence records in the store
     */
    fun size(): Int {
        return store.size
    }

    /**
     * Returns all stored evidence records.
     *
     * @return A copy of the stored records (callers cannot mutate internal collection)
     */
    fun getAll(): List<Evidence> {
        return store.values.toList()
    }

    /**
     * Deletes an evidence record by ID.
     *
     * @param id The evidence ID to delete
     * @return true if a record was removed, false if no record existed with that ID
     */
    fun delete(id: String): Boolean {
        return store.remove(id) != null
    }

    /**
     * Removes all evidence records from the store.
     */
    fun clear(): Unit {
        store.clear()
    }

    /**
     * Generates a deterministic evidence ID from an event ID.
     *
     * @param eventId The event ID
     * @return An evidence ID in the format "evd_<eventId>"
     */
    private fun generateEvidenceId(eventId: String): String {
        return "evd_$eventId"
    }
}