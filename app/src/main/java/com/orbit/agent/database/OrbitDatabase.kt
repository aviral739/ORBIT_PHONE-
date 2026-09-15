package com.orbit.agent.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.orbit.agent.database.dao.KnowledgeDao
import com.orbit.agent.database.dao.TaskDao
import com.orbit.agent.database.entity.AuditLogEntity
import com.orbit.agent.database.entity.CommitmentEntity
import com.orbit.agent.database.entity.EvidenceEntity
import com.orbit.agent.database.entity.EventEntity
import com.orbit.agent.database.entity.KnowledgeEntity
import com.orbit.agent.database.entity.TaskEntity
import com.orbit.agent.database.util.Converters

/**
 * OrbitDatabase
 *
 * Single [RoomDatabase] instance for the ORBIT agent.
 * Registers all entity types and exposes typed DAOs.
 *
 * Schema version history
 * ──────────────────────
 *   1 → 001_initial_schema.sql   (events, evidence, commitments, audit_log)
 *   2 → 002_add_office_kit_sync.sql  (events.office_kit_sync_status column)
 *   3 → tasks and knowledge tables added (Phase 5 — multifunctional agent)
 */
@Database(
    entities = [
        EventEntity::class,
        EvidenceEntity::class,
        CommitmentEntity::class,
        AuditLogEntity::class,
        TaskEntity::class,
        KnowledgeEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OrbitDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        private const val DB_NAME = "orbit_database"

        @Volatile
        private var INSTANCE: OrbitDatabase? = null

        /**
         * Returns the singleton [OrbitDatabase].
         * v1→v2 migration is incremental; v2→v3 uses destructive migration
         * (tasks and knowledge tables are newly created, no user data exists yet).
         */
        fun getInstance(context: Context): OrbitDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): OrbitDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                OrbitDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                // v2 → v3: new tables (tasks, knowledge) — safe to recreate
                .fallbackToDestructiveMigration()
                .build()

        // ── Migration: v1 → v2 ────────────────────────────────────────────
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE events ADD COLUMN office_kit_sync_status TEXT DEFAULT 'pending'"
                )
            }
        }
    }
}

// ── Stub EvidenceDao (full version deferred to Phase 6) ──────────────────────

/** Placeholder until a dedicated EvidenceDao.kt is created. */
@Dao
interface EvidenceDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(evidence: EvidenceEntity): Long
}
