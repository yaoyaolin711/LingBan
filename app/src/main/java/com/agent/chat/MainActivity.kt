package com.agent.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.data.proactive.ProactiveNudgeWorker
import com.agent.chat.data.settings.ChatSettingsStore
import com.agent.chat.ui.navigation.AgentNavHost
import com.agent.chat.ui.theme.AgentChatTheme
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.AppThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var chatSettingsStore: ChatSettingsStore

    private var pendingConversationId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingConversationId = intent?.getStringExtra(ProactiveNudgeWorker.EXTRA_CONVERSATION_ID)
        enableEdgeToEdge()
        setContent {
            val settings by chatSettingsStore.snapshot.collectAsStateWithLifecycle()
            val themeMode = if (settings.isDarkTheme) AppThemeMode.Dark else AppThemeMode.Light

            AgentChatTheme(themeMode = themeMode) {
                val colors = AgentThemeColors
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background,
                ) {
                    AgentNavHost(
                        openConversationId = pendingConversationId,
                        onOpenConversationConsumed = { pendingConversationId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingConversationId = intent.getStringExtra(ProactiveNudgeWorker.EXTRA_CONVERSATION_ID)
    }
}
