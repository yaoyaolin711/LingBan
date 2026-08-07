package me.rerere.rikkahub.ui.components.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.hasUsableChatProvider
import me.rerere.rikkahub.ui.components.companion.CompanionPermissionGuideDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.compose.koinInject

private const val STEP_WELCOME = 0
private const val STEP_PROVIDER = 1
private const val STEP_EXPERIENCE = 2
private const val TOTAL_STEPS = 3

/**
 * 首次引导：在文明上网协议之后展示，不阻塞冷启动恢复 Chat 的后台逻辑。
 * 老用户通过 [Settings.onboardingCompleted] 迁移逻辑跳过。
 */
@Composable
fun SolaceOnboardingDialog(
    onComplete: () -> Unit,
) {
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(STEP_WELCOME) }
    var enableSentenceSend by remember { mutableStateOf(true) }
    var enableCompanion by remember { mutableStateOf(false) }
    var showPermissionGuide by remember { mutableStateOf(false) }

    val chatReady = settings.hasUsableChatProvider()
    val assistant = settings.getCurrentAssistant()

    BackHandler(enabled = true) {
        // 允许跳过，不强制完成每一步
        onComplete()
    }

    if (showPermissionGuide) {
        CompanionPermissionGuideDialog(
            assistant = assistant.copy(enableCompanion = true),
            companionAssist = settings.companionAssist,
            onDismiss = {
                showPermissionGuide = false
                onComplete()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { onComplete() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
        title = {
            Column {
                Text(
                    text = when (step) {
                        STEP_WELCOME -> "欢迎使用 Solace"
                        STEP_PROVIDER -> "连接 AI 服务"
                        else -> "个性化伴侣体验"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (step + 1).toFloat() / TOTAL_STEPS },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        text = {
            when (step) {
                STEP_WELCOME -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Solace 是你的 AI 伴侣空间：聊天、语音通话、悬浮陪伴与记忆，都在这里。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "接下来只需 2 步，帮你快速开始第一次对话。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                STEP_PROVIDER -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (chatReady) {
                            Text(
                                text = "已检测到可用的 AI 服务配置，可以直接开始聊天。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = "需要配置至少一个 AI 提供商（API Key），Solace 才能回复你的消息。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "可在「个人 → AI 提供商」中配置 Claude / OpenAI / Google 等。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                STEP_EXPERIENCE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "推荐开启以下体验，让对话更有「陪伴感」。可随时在助手设置中修改。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "分句展示",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "AI 长回复逐句出现，每句独立气泡",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enableSentenceSend,
                                onCheckedChange = { enableSentenceSend = it },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "陪伴模式",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "悬浮伴侣 + 权限引导（可选）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enableCompanion,
                                onCheckedChange = { enableCompanion = it },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (step) {
                        STEP_WELCOME -> step = STEP_PROVIDER
                        STEP_PROVIDER -> {
                            if (!chatReady) {
                                nav.navigate(Screen.SettingProvider)
                            } else {
                                step = STEP_EXPERIENCE
                            }
                        }
                        STEP_EXPERIENCE -> {
                            scope.launch {
                                settingsStore.update { current ->
                                    val assistantId = current.getCurrentAssistant().id
                                    current.copy(
                                        onboardingCompleted = true,
                                        assistants = current.assistants.map { a ->
                                            if (a.id != assistantId) return@map a
                                            a.copy(
                                                enableSentenceSend = enableSentenceSend,
                                                enableCompanion = enableCompanion || a.enableCompanion,
                                            )
                                        },
                                    )
                                }
                                if (enableCompanion) {
                                    showPermissionGuide = true
                                } else {
                                    onComplete()
                                }
                            }
                        }
                    }
                },
            ) {
                Text(
                    when (step) {
                        STEP_PROVIDER -> if (chatReady) "下一步" else "去配置"
                        STEP_EXPERIENCE -> "开始使用"
                        else -> "下一步"
                    },
                )
            }
        },
        dismissButton = {
            when (step) {
                STEP_PROVIDER -> {
                    if (chatReady) {
                        TextButton(onClick = { step = STEP_EXPERIENCE }) {
                            Text("跳过")
                        }
                    } else {
                        OutlinedButton(onClick = { step = STEP_EXPERIENCE }) {
                            Text("稍后配置")
                        }
                    }
                }
                STEP_EXPERIENCE -> {
                    TextButton(onClick = { onComplete() }) {
                        Text("跳过")
                    }
                }
                else -> {
                    TextButton(onClick = { onComplete() }) {
                        Text("跳过引导")
                    }
                }
            }
        },
    )
}
