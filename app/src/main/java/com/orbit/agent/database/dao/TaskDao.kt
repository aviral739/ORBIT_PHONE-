package com.orbit.agent.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.orbit.agent.database.entity.TaskEntity

/**
 * TaskDao
 *
 * CRUD + query interface for the [tasks] table.
 *
 * Priority ordering convention:
 *   P0 < P1 < P2  (ASCII sort satisfies this naturally)
 */
@Dao
interface TaskDao {

    // ── Writes ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    // ── Reads ─────────────────────────────────────────────────────────────

    /** All tasks that are not yet completed, sorted by priority then due date. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
        ORDER BY priority ASC, due_timestamp ASC
        """
    )
    suspend fun getPendingTasks(): List<TaskEntity>

    /** Tasks due within the next [windowMs] milliseconds. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE is_completed = 0
          AND due_timestamp IS NOT NULL
          AND due_timestamp BETWEEN :nowMs AND :nowMs + :windowMs
        ORDER BY due_timestamp ASC
        """
    )
    suspend fun getUpcomingTasks(nowMs: Long, windowMs: Long): List<TaskEntity>

    /** Only P0 critical tasks that are still pending. */
    @Query(
        "SELECT * FROM tasks WHERE is_completed = 0 AND priority = 'P0' ORDER BY due_timestamp ASC"
    )
    suspend fun getCriticalPendingTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    /** Full-text search over title and description. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE title LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
        ORDER BY priority ASC, created_at DESC
        """
    )
    suspend fun search(query: String): List<TaskEntity>

    // ── State transitions ─────────────────────────────────────────────────

    /**
     * Toggle [is_completed] for [taskId].
     * Pass [completed] = true to mark done, false to reopen.
     */
    @Query("UPDATE tasks SET is_completed = :completed WHERE id = :taskId")
    suspend fun toggleTaskCompletion(taskId: String, completed: Boolean)

    /** Update only the priority of an existing task. */
    @Query("UPDATE tasks SET priority = :priority WHERE id = :taskId")
    suspend fun updatePriority(taskId: String, priority: String)

    /** Reschedule a task to a new deadline. */
    @Query("UPDATE tasks SET due_timestamp = :newDueMs WHERE id = :taskId")
    suspend fun reschedule(taskId: String, newDueMs: Long)

    // ── Cleanup ───────────────────────────────────────────────────────────

    @Query("DELETE FROM tasks WHERE is_completed = 1 AND created_at < :cutoffMs")
    suspend fun purgeCompleted(cutoffMs: Long): Int

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: String)
}
