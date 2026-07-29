package com.agent.chat.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val userName: String = "",
    val userAvatarPath: String = "",
    val userInterest: String = "",
    val themeMode: AppThemeMode = AppThemeMode.Light,
    val providerName: String? = null,
    val modelName: String? = null,
    val membershipLabel: String = "Solace 伙伴",
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatSettingsStore: ChatSettingsStore,
    private val providerConfigRepository: ProviderConfigRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        chatSettingsStore.snapshot,
        providerConfigRepository.observeConfigs(),
    ) { settings, providers ->
        val default = providers.firstOrNull()
        ProfileUiState(
            userName = settings.userNickname.ifBlank { "未命名用户" },
            userAvatarPath = settings.userAvatarPath,
            userInterest = settings.userInterest,
            themeMode = if (settings.isDarkTheme) AppThemeMode.Dark else AppThemeMode.Light,
            providerName = default?.name,
            modelName = default?.modelName,
            membershipLabel = "Solace 伙伴 · 免费",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun setThemeMode(mode: AppThemeMode) {
        chatSettingsStore.setThemeMode(
            when (mode) {
                AppThemeMode.Dark -> "dark"
                AppThemeMode.Light -> "light"
            },
        )
    }

    fun setNickname(nickname: String) {
        chatSettingsStore.setUserNickname(nickname)
    }

    fun setAvatarFromUri(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                copyAvatar(uri)
            } ?: return@launch
            chatSettingsStore.setUserAvatarPath(path)
        }
    }

    fun clearAvatar() {
        viewModelScope.launch(Dispatchers.IO) {
            val old = chatSettingsStore.get().userAvatarPath
            if (old.isNotBlank()) {
                runCatching { File(old).delete() }
            }
            chatSettingsStore.setUserAvatarPath(null)
        }
    }

    private fun copyAvatar(uri: Uri): String? {
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val dest = File(dir, "user_avatar.jpg")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        }.getOrNull()
    }
}
