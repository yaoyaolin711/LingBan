package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(29, 30)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_entity_edge` (
                    `assistant_id` TEXT NOT NULL,
                    `from_entity_id` INTEGER NOT NULL,
                    `to_entity_id` INTEGER NOT NULL,
                    `relation` TEXT NOT NULL,
                    `weight` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`from_entity_id`, `to_entity_id`, `relation`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_entity_edge_assistant_id` " +
                    "ON `memory_entity_edge` (`assistant_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_entity_edge_from_entity_id` " +
                    "ON `memory_entity_edge` (`from_entity_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_entity_edge_to_entity_id` " +
                    "ON `memory_entity_edge` (`to_entity_id`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_embedding` (
                    `memory_id` INTEGER NOT NULL,
                    `content_hash` TEXT NOT NULL,
                    `dims` INTEGER NOT NULL,
                    `vector_json` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`memory_id`)
                )
                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
