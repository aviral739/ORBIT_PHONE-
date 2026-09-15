package com.orbit.agent.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.orbit.agent.database.entity.AuditLogEntity
import com.orbit.agent.database.entity.CommitmentEntity
import com.orbit.agent.database.entity.EvidenceEntity
import com.orbit.agent.database.entity.EventEntity
import com.orbit.agent.database.util.Converters

/**
 * OrbitDatabase
 *
 * Single [RoomDatabase] instance for the ORBIT agent.
 * Registers all four entity types defined in
 * `database/schema/orbit_schema.sql` and exposes typed DAOs.
 *
 * Schema version history
 * ──────────────────────
 *   1 → 001_initial_schema.sql  (events, evidence, commitments, audit_log)
 *   2 → 002_add_office_kit_sync.sql  (events.office_kit_sync_status column)
 */
@Database(
    entities = [
        EventEntity::class,
        EvidenceEntity::class,
        CommitmentEntity::class,
        AuditLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OrbitDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private const val DB_NAME = "orbit_database"

        @Volatile
        private var INSTANCE: OrbitDatabase? = null

        /**
         * Returns the singleton [OrbitDatabase], creating it on first access.
         * Uses [Room.databaseBuilder] with a sequential migration from v1 to v2
         * corresponding to `migrations/002_add_office_kit_sync.sql`.
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
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        // ── Migration: v1 → v2 ────────────────────────────────────────────
        // Corresponds to database/migrations/002_add_office_kit_sync.sql
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE events ADD COLUMN office_kit_sync_status TEXT DEFAULT 'pending'"
                )
            }
        }
    }
}

// ── Stub DAOs & entities referenced above but not yet generated ───────────────
// Remove these once the full entity layer is scaffolded.

/** Placeholder until EvidenceDao.kt is created in a later phase. */
@Dao
interface EvidenceDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(evidence: EvidenceEntity): Long
}
