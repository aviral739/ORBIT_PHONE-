package com.orbit.agent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Mirrors the `evidence` table in `database/schema/orbit_schema.sql`. */
@Entity(
    tableName = "evidence",
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["event_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class EvidenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id", index = true) val eventId: String,
    val path: String? = null,
    val type: String? = null,
)
