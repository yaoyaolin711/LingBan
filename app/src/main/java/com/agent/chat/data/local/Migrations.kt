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

/**
 * v11：添加性能优化索引 + provider_configs 新增字段。
 *   - messages: 复合索引 (conversationId, timestamp) 加速消息列表排序查询
 *   - conversations: updatedAt 索引加速会话列表排序
 *   - memories: (personaId, importance) 复合索引加速记忆检索
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // messages 表添加 imageUri 列（视觉功能）
        db.execSQL("ALTER TABLE messages ADD COLUMN imageUri TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 性能索引
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_messages_conversationId_timestamp " +
                "ON messages (conversationId, timestamp)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversations_updatedAt " +
                "ON conversations (updatedAt)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memories_personaId_importance " +
                "ON memories (personaId, importance)",
        )
        // provider_configs 新增字段（多 Provider 支持）
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN supportsVision INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE provider_configs ADD COLUMN supportsToolCalling INTEGER NOT NULL DEFAULT 1")
    }
}
