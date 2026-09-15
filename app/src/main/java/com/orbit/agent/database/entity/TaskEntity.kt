package com.orbit.agent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * TaskEntity
 *
 * Represents a discrete actionable to-do item extracted from any
 * ingestion source (audio, OCR, notification, clipboard).
 *
 * [priority] values: "P0" (critical), "P1" (high), "P2" (normal)
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,

    val title: String,

    val description: String? = null,

    /** Priority tier: P0 = critical, P1 = high, P2 = normal. */
    val priority: String = "P2",

    /** Epoch-ms deadline for this task. Null = no fixed deadline. */
    @ColumnInfo(name = "due_timestamp")
    val dueTimestamp: Long? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** The event that triggered this task, if any. */
    @ColumnInfo(name = "source_event_id")
    val sourceEventId: String? = null,
)
