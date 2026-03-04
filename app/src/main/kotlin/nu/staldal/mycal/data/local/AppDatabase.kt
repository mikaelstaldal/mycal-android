package nu.staldal.mycal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EventEntity::class, PendingChange::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun pendingChangeDao(): PendingChangeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate events table with String ID and new columns
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS events_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        startTime TEXT NOT NULL DEFAULT '',
                        endTime TEXT NOT NULL DEFAULT '',
                        allDay INTEGER NOT NULL DEFAULT 0,
                        color TEXT NOT NULL DEFAULT '',
                        recurrenceFreq TEXT NOT NULL DEFAULT '',
                        location TEXT NOT NULL DEFAULT '',
                        categories TEXT NOT NULL DEFAULT '',
                        url TEXT NOT NULL DEFAULT '',
                        reminderMinutes INTEGER NOT NULL DEFAULT 0,
                        latitude REAL,
                        longitude REAL,
                        createdAt TEXT NOT NULL DEFAULT '',
                        updatedAt TEXT NOT NULL DEFAULT '',
                        parentId TEXT,
                        recurrenceCount INTEGER,
                        recurrenceUntil TEXT,
                        recurrenceInterval INTEGER,
                        recurrenceByDay TEXT,
                        recurrenceByMonthday TEXT,
                        recurrenceByMonth TEXT,
                        exdates TEXT,
                        rdates TEXT,
                        recurrenceParentId TEXT,
                        recurrenceOriginalStart TEXT,
                        duration TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO events_new (id, title, description, startTime, endTime, allDay, color, recurrenceFreq, location, categories, url, reminderMinutes, latitude, longitude, createdAt, updatedAt)
                    SELECT CAST(id AS TEXT), title, description, startTime, endTime, allDay, color, recurrenceFreq, location, categories, url, reminderMinutes, latitude, longitude, createdAt, updatedAt FROM events
                """.trimIndent())
                db.execSQL("DROP TABLE events")
                db.execSQL("ALTER TABLE events_new RENAME TO events")

                // Recreate pending_changes table with String eventId
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_changes_new (
                        changeId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId TEXT NOT NULL,
                        changeType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pending_changes_new (changeId, eventId, changeType, timestamp)
                    SELECT changeId, CAST(eventId AS TEXT), changeType, timestamp FROM pending_changes
                """.trimIndent())
                db.execSQL("DROP TABLE pending_changes")
                db.execSQL("ALTER TABLE pending_changes_new RENAME TO pending_changes")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mycal_database"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
