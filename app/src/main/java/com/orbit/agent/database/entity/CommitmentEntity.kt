package com.orbit.agent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Mirrors the `commitments` table in `database/schema/orbit_schema.sql`. */
@Entity(
    tableName = "commitments",
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["event_id"],
        onDelete = ForeignKey.SET_NULL,
    )],
)
data class CommitmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id", index = true) val eventId: String? = null,
    val deadline: Long? = null,
    val description: String? = null,
    val status: String = "ACTIVE",
)
