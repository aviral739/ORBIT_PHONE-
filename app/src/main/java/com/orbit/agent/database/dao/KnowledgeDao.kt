package com.orbit.agent.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orbit.agent.database.entity.KnowledgeEntity

/**
 * KnowledgeDao
 *
 * Append-and-search interface for the [knowledge] table.
 * All writes go through [insertSnippet]; retrieval is primarily
 * full-text search over [raw_text] and [tags].
 */
@Dao
interface KnowledgeDao {

    // ── Writes ────────────────────────────────────────────────────────────

    /** Insert a new knowledge snippet. Ignores exact-ID duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnippet(snippet: KnowledgeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(snippets: List<KnowledgeEntity>)

    // ── Reads ─────────────────────────────────────────────────────────────

    /** Full-text search across [raw_text] and [tags]. */
    @Query(
        """
        SELECT * FROM knowledge
        WHERE raw_text LIKE '%' || :query || '%'
           OR tags     LIKE '%' || :query || '%'
        ORDER BY created_at DESC
        """
    )
    suspend fun searchKnowledge(query: String): List<KnowledgeEntity>

    /** All snippets from a specific source (CAMERA_OCR / AUDIO_MEMO / CLIPBOARD). */
    @Query(
        "SELECT * FROM knowledge WHERE source_type = :sourceType ORDER BY created_at DESC"
    )
    suspend fun getBySource(sourceType: String): List<KnowledgeEntity>

    /** Most recent [limit] knowledge items — used by the memory overview card. */
    @Query(
        "SELECT * FROM knowledge ORDER BY created_at DESC LIMIT :limit"
    )
    suspend fun getRecent(limit: Int = 20): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KnowledgeEntity?

    /** Snippets tagged with [tag] (pipe-delimited tags column). */
    @Query(
        "SELECT * FROM knowledge WHERE tags LIKE '%' || :tag || '%' ORDER BY created_at DESC"
    )
    suspend fun getByTag(tag: String): List<KnowledgeEntity>

    // ── Cleanup ───────────────────────────────────────────────────────────

    @Query("DELETE FROM knowledge WHERE id = :id")
    suspend fun delete(id: String)

    /** Remove CAMERA_OCR snippets older than [cutoffMs] to cap storage. */
    @Query(
        "DELETE FROM knowledge WHERE source_type = 'CAMERA_OCR' AND created_at < :cutoffMs"
    )
    suspend fun purgeOldOcrSnippets(cutoffMs: Long): Int
}
