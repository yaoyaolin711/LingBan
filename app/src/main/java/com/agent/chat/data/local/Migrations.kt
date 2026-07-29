package com.agent.chat.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8：personas 增加 Persona Engine 结构化字段 [personaProfileJson]。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE personas ADD COLUMN personaProfileJson TEXT NOT NULL DEFAULT ''",
        )
    }
}

/**
 * v9：conversations 增加 Relationship Engine 字段 [relationshipProfileJson]。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE conversations ADD COLUMN relationshipProfileJson TEXT NOT NULL DEFAULT ''",
        )
    }
}

/**
 * v10：conversations 增加 Expression Style Engine 字段 [expressionProfileJson]。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE conversations ADD COLUMN expressionProfileJson TEXT NOT NULL DEFAULT ''",
        )
    }
}
