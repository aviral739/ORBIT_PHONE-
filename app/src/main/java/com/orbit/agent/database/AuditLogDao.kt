package com.orbit.agent.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.agent.database.entity.AuditLogEntity

/**
 * AuditLogDao
 *
 * Append-only DAO for the [audit_log] table defined in
 * `database/schema/orbit_schema.sql`.
 *
 * Each record captures one governance-tier decision made by
 * [DecisionEngine], with a [risk_tier] value matching
 * `config/policies.json`: SAFE | REVERSIBLE | SENSITIVE.
 *
 * Column reference:
 *   id TEXT PK | action TEXT | timestamp DATETIME
 *   details TEXT | risk_tier TEXT | event_id TEXT
 */
@Dao
interface AuditLogDao {

    // ── Writes ───────────────────────────────────────────────────────────

    /**
     * Append a single audit record.  IGNORE on conflict — log rows must
     * never be silently overwritten; duplicate IDs are a bug, not a retry.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: AuditLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<AuditLogEntity>)

    // ── Reads ────────────────────────────────────────────────────────────

    /** Full log, newest first — used by audit viewer and export. */
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    suspend fun getAll(): List<AuditLogEntity>

    /**
     * All records for a specific governance tier.
     * [tier] must be one of SAFE | REVERSIBLE | SENSITIVE.
     */
    @Query(
        "SELECT * FROM audit_log WHERE risk_tier = :tier ORDER BY timestamp DESC"
    )
    suspend fun getByTier(tier: String): List<AuditLogEntity>

    /**
     * Audit trail for a specific originating event — useful when the user
     * wants to inspect every decision taken about a notification or commitment.
     */
    @Query(
        "SELECT * FROM audit_log WHERE event_id = :eventId ORDER BY timestamp ASC"
    )
    suspend fun getForEvent(eventId: String): List<AuditLogEntity>

    /** Count records per tier — used by the benchmark / smoke-test assertions. */
    @Query(
        "SELECT COUNT(*) FROM audit_log WHERE risk_tier = :tier"
    )
    suspend fun countByTier(tier: String): Int

    /**
     * Return all REVERSIBLE and SENSITIVE records since [sinceMs].
     * Matched by the smoke-test to confirm every non-SAFE action is logged.
     */
    @Query(
        """
        SELECT * FROM audit_log
        WHERE risk_tier IN ('REVERSIBLE', 'SENSITIVE')
          AND timestamp >= :sinceMs
        ORDER BY timestamp ASC
        """
    )
    suspend fun getNonSafeSince(sinceMs: Long): List<AuditLogEntity>

    // ── Retention ────────────────────────────────────────────────────────

    /** Remove SAFE-tier records older than [cutoffMs] to cap log size. */
    @Query("DELETE FROM audit_log WHERE risk_tier = 'SAFE' AND timestamp < :cutoffMs")
    suspend fun purgeSafeBefore(cutoffMs: Long): Int
}
