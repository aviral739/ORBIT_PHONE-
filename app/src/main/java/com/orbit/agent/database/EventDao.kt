package com.orbit.agent.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.agent.database.entity.EventEntity

/**
 * EventDao
 *
 * Provides CRUD access to the [events] table as defined in
 * `database/schema/orbit_schema.sql`.
 *
 * Column reference:
 *   id TEXT PK | type TEXT | created_at DATETIME | data TEXT
 *   importance REAL | status TEXT | office_kit_sync_status TEXT
 */
@Dao
interface EventDao {

    // ── Writes ──────────────────────────────────────────────────────────

    /**
     * Insert or fully replace an event.  Replaces on conflict so that
     * re-ingested duplicates overwrite stale rows rather than accumulating.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(event: EventEntity): Long

    /** Batch-insert multiple events in one transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(events: List<EventEntity>)

    // ── Reads ────────────────────────────────────────────────────────────

    /** All events, newest first — used by the UI timeline and batch export. */
    @Query("SELECT * FROM events ORDER BY created_at DESC")
    suspend fun getAllByDateDesc(): List<EventEntity>

    /** Events currently awaiting triage in the QUEUE. */
    @Query("SELECT * FROM events WHERE status = 'QUEUE' ORDER BY created_at ASC")
    suspend fun getQueued(): List<EventEntity>

    /** Events assigned to REASON tier for SLM processing. */
    @Query("SELECT * FROM events WHERE status = 'REASON' ORDER BY importance DESC")
    suspend fun getPendingReason(): List<EventEntity>

    /** Retrieve a single event by its stable identifier. */
    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    suspend fun getById(eventId: String): EventEntity?

    /** Lookup duplicate candidates sharing the same [type] and [data] hash. */
    @Query(
        """
        SELECT * FROM events
        WHERE type = :type AND data = :dataHash
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    suspend fun findDuplicates(type: String, dataHash: String, limit: Int = 5): List<EventEntity>

    // ── Updates ──────────────────────────────────────────────────────────

    /** Transition an event to a new triage [status] (DROP/COMPRESS/QUEUE/REASON). */
    @Query("UPDATE events SET status = :status WHERE id = :eventId")
    suspend fun updateStatus(eventId: String, status: String)

    /** Mark event as synced with Office Kit desktop bridge. */
    @Query(
        "UPDATE events SET office_kit_sync_status = :syncStatus WHERE id = :eventId"
    )
    suspend fun updateOfficeSyncStatus(eventId: String, syncStatus: String)

    /** Bulk-mark a set of COMPRESS-tier events as consolidated. */
    @Query(
        "UPDATE events SET status = 'COMPRESSED' WHERE id IN (:ids)"
    )
    suspend fun markCompressed(ids: List<String>)

    // ── Deletes ──────────────────────────────────────────────────────────

    /** Hard-delete DROP-tier events older than [cutoffEpochMs] to save space. */
    @Query(
        "DELETE FROM events WHERE status = 'DROP' AND created_at < :cutoffEpochMs"
    )
    suspend fun purgeDroppedBefore(cutoffEpochMs: Long): Int
}
