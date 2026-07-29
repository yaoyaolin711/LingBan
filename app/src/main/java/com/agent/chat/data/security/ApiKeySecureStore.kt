package com.agent.chat.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeySecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(providerId: String, apiKey: String) {
        prefs.edit().putString(key(providerId), apiKey).apply()
    }

    fun get(providerId: String): String =
        prefs.getString(key(providerId), "").orEmpty()

    fun delete(providerId: String) {
        prefs.edit().remove(key(providerId)).apply()
    }

    private fun key(providerId: String): String = "api_key_$providerId"

    companion object {
        private const val PREFS_NAME = "agent_chat_secure_prefs"
    }
}
