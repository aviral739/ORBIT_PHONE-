package com.orbit.agent.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * KnowledgeEntity
 *
 * Stores informational/reference snippets captured from any ingestion
 * source — camera OCR of receipts, audio memos, clipboard pastes, etc.
 *
 * [sourceType] values: "CAMERA_OCR" | "AUDIO_MEMO" | "CLIPBOARD"
 */
@Entity(tableName = "knowledge")
data class KnowledgeEntity(
    @PrimaryKey
    val id: String,

    /**
     * Origin of the snippet.
     * One of: CAMERA_OCR, AUDIO_MEMO, CLIPBOARD.
     */
    @ColumnInfo(name = "source_type")
    val sourceType: String,

    /** Raw captured text — the searchable body of this knowledge item. */
    @ColumnInfo(name = "raw_text")
    val rawText: String,

    /**
     * Pipe-delimited tag list, e.g. "receipt|finance|2026-09".
     * Stored as a plain string; the Room TypeConverter in [Converters]
     * handles List<String> ↔ String serialisation.
     */
    val tags: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
