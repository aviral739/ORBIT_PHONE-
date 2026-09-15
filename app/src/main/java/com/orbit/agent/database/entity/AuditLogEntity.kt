package com.orbit.agent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the `audit_log` table in `database/schema/orbit_schema.sql`.
 * [riskTier] must be one of SAFE | REVERSIBLE | SENSITIVE
 * as defined in `config/policies.json`.
 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    @ColumnInfo(name = "risk_tier") val riskTier: String = "SAFE",
    @ColumnInfo(name = "event_id", index = true) val eventId: String? = null,
)
