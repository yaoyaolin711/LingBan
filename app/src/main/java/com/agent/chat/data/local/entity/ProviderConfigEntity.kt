package com.agent.chat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    /** Room 中不存明文；真实 apiKey 在 EncryptedSharedPreferences */
    val apiKey: String = "",
    val modelName: String,
    val providerType: String,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val supportsVision: Boolean = false,
    val supportsToolCalling: Boolean = true,
)
