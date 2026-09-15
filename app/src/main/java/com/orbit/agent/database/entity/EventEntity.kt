package com.orbit.agent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mirrors the `events` table in `database/schema/orbit_schema.sql`. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val data: String? = null,
    val importance: Float = 0f,
    val status: String = "QUEUE",
    @ColumnInfo(name = "office_kit_sync_status") val officeKitSyncStatus: String = "pending",
)
