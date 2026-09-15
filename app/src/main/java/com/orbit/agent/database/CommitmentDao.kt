package com.orbit.agent.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.agent.database.entity.CommitmentEntity

/**
 * CommitmentDao
 *
 * Provides CRUD access to the [commitments] table defined in
 * `database/schema/orbit_schema.sql`.
 *
 * Column reference:
 *   id TEXT PK | event_id TEXT FK | deadline DATETIME
 *   description TEXT | status TEXT
 */
@Dao
interface CommitmentDao {

    // ── Writes ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(commitment: CommitmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(commitments: List<CommitmentEntity>)

    // ── Reads ────────────────────────────────────────────────────────────

    /**
     * All commitments still marked ACTIVE — the primary feed for
     * DecisionEngine triage and OfficeKitBridge escalation.
     */
    @Query("SELECT * FROM commitments WHERE status = 'ACTIVE' ORDER BY deadline ASC")
    suspend fun getActive(): List<CommitmentEntity>

    /** Commitments whose deadlines fall within the next [windowMs] ms. */
    @Query(
        """
        SELECT * FROM commitments
        WHERE status = 'ACTIVE'
          AND deadline BETWEEN :nowMs AND :nowMs + :windowMs
        ORDER BY deadline ASC
        """
    )
    suspend fun getUpcoming(nowMs: Long, windowMs: Long): List<CommitmentEntity>

    /**
     * Detect deadline conflicts — two or more ACTIVE commitments for the
     * same event_id that have non-identical deadlines (deadline slip scenario).
     */
    @Query(
        """
        SELECT * FROM commitments
        WHERE event_id = :eventId
          AND status = 'ACTIVE'
        ORDER BY deadline ASC
        """
    )
    suspend fun getConflictsForEvent(eventId: String): List<CommitmentEntity>

    /**
     * Find all ACTIVE commitments whose deadline falls before [deadlineMs].
     * Used to detect overdue items after a rescheduling decision.
     */
    @Query(
        """
        SELECT * FROM commitments
        WHERE status = 'ACTIVE'
          AND deadline < :deadlineMs
        ORDER BY deadline ASC
        """
    )
    suspend fun getOverdue(deadlineMs: Long): List<CommitmentEntity>

    /** Look up a single commitment by primary key. */
    @Query("SELECT * FROM commitments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CommitmentEntity?

    // ── Deadline conflict resolution ──────────────────────────────────────

    /**
     * When a newer deadline supersedes an older one, mark the obsolete row
     * RESOLVED and let the caller insert the winning commitment separately.
     */
    @Query(
        "UPDATE commitments SET status = 'RESOLVED' WHERE event_id = :eventId AND id != :winningId"
    )
    suspend fun resolveConflictsKeeping(eventId: String, winningId: String): Int

    // ── Status transitions ────────────────────────────────────────────────

    @Query("UPDATE commitments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /** Reschedule: update the deadline of an existing commitment. */
    @Query("UPDATE commitments SET deadline = :newDeadlineMs WHERE id = :id")
    suspend fun reschedule(id: String, newDeadlineMs: Long)

    // ── Cleanup ───────────────────────────────────────────────────────────

    @Query("DELETE FROM commitments WHERE status IN ('RESOLVED', 'CANCELLED') AND deadline < :cutoffMs")
    suspend fun purgeOld(cutoffMs: Long): Int
}
