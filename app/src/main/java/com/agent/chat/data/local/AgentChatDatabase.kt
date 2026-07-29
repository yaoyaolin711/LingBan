package com.agent.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agent.chat.data.local.dao.ConversationDao
import com.agent.chat.data.local.dao.MemoryDao
import com.agent.chat.data.local.dao.MessageDao
import com.agent.chat.data.local.dao.PersonaDao
import com.agent.chat.data.local.dao.ProviderConfigDao
import com.agent.chat.data.local.entity.ConversationEntity
import com.agent.chat.data.local.entity.MemoryEntity
import com.agent.chat.data.local.entity.MessageEntity
import com.agent.chat.data.local.entity.PersonaEntity
import com.agent.chat.data.local.entity.ProviderConfigEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        PersonaEntity::class,
        ProviderConfigEntity::class,
        MemoryEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class AgentChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun memoryDao(): MemoryDao
}
