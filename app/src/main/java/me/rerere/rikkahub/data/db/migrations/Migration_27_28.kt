package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(27, 28)
        try {
            val now = System.currentTimeMillis()
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN topic_key TEXT")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN layer TEXT NOT NULL DEFAULT 'episode'")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN status TEXT NOT NULL DEFAULT 'active'")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE MemoryEntity ADD COLUMN supersedes_id INTEGER")
            db.execSQL(
                "UPDATE MemoryEntity SET created_at = $now, updated_at = $now " +
                    "WHERE created_at = 0 OR updated_at = 0"
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
