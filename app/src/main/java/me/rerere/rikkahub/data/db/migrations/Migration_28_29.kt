package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DatabaseMigrationTracker.onMigrationStart(28, 29)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_entity_node` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `mention_count` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_entity_node_assistant_id_name` " +
                    "ON `memory_entity_node` (`assistant_id`, `name`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_entity_link` (
                    `memory_id` INTEGER NOT NULL,
                    `entity_id` INTEGER NOT NULL,
                    `role` TEXT NOT NULL,
                    PRIMARY KEY(`memory_id`, `entity_id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_entity_link_entity_id` ON `memory_entity_link` (`entity_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_memory_entity_link_memory_id` ON `memory_entity_link` (`memory_id`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_recall_meta` (
                    `memory_id` INTEGER NOT NULL,
                    `summary_short` TEXT NOT NULL,
                    `observed_at` INTEGER,
                    `emotion_tags` TEXT NOT NULL,
                    `importance` INTEGER NOT NULL,
                    `last_recalled_at` INTEGER NOT NULL,
                    PRIMARY KEY(`memory_id`)
                )
                """.trimIndent()
            )
        } finally {
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
