package com.agent.chat.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agent.chat.data.repository.ProviderConfigRepository
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val userName: String = "",
    val userInterest: String = "",
    val themeMode: AppThemeMode = AppThemeMode.Light,
    val providerName: String? = null,
    val modelName: String? = null,
    val membershipLabel: String = "灵伴伙伴",
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
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
            userInterest = settings.userInterest,
            themeMode = if (settings.isDarkTheme) AppThemeMode.Dark else AppThemeMode.Light,
            providerName = default?.name,
            modelName = default?.modelName,
            membershipLabel = "灵伴伙伴 · 免费",
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
}
