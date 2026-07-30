package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.device.CompanionAssistSetting
import me.rerere.rikkahub.data.device.DeviceShellExecutor
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.CompanionMonitorService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.isSolaceAccessibilityEnabled
import me.rerere.rikkahub.utils.openAccessibilitySettings
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import rikka.shizuku.Shizuku
import me.rerere.rikkahub.service.SolaceAccessibilityService

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            innerPadding = innerPadding,
            assistant = assistant,
            companionAssist = settings.companionAssist,
            onUpdate = { vm.update(it) },
            onUpdateCompanion = { next ->
                scope.launch {
                    settingsStore.update { it.copy(companionAssist = next) }
                }
            },
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    companionAssist: CompanionAssistSetting,
    onUpdate: (Assistant) -> Unit,
    onUpdateCompanion: (CompanionAssistSetting) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)

    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_read)) },
                usage = { Text(stringResource(R.string.permission_calendar_read_desc)) },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_write)) },
                usage = { Text(stringResource(R.string.permission_calendar_write_desc)) },
                required = true
            ),
        )
    )
    PermissionManager(permissionState = calendarPermissionState)

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        if (enabled && (option == LocalToolOption.ScreenTime || option == LocalToolOption.DeviceAssist) &&
            !context.hasUsageStatsPermission()
        ) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.PhoneControl && !SolaceAccessibilityService.isRunning()) {
            toaster.show(
                message = "请在系统无障碍设置中开启 Solace，才能操控手机界面",
                type = ToastType.Warning,
            )
            context.openAccessibilitySettings()
        }
        if (enabled && option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
            calendarPermissionState.requestPermissions()
            return
        }
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    fun updateCompanion(transform: (CompanionAssistSetting) -> CompanionAssistSetting) {
        val next = transform(companionAssist)
        onUpdateCompanion(next)
        scope.launch {
            if (next.monitorEnabled && !context.hasUsageStatsPermission()) {
                toaster.show(message = permissionRequiredText, type = ToastType.Warning)
                context.openUsageAccessSettings()
            }
            CompanionMonitorService.syncWithSettings(context, next)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardGroup {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_time_info_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_time_info_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.TimeInfo),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_clipboard_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_clipboard_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Clipboard),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_tts_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_tts_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Tts),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.AskUser),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_screen_time_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_screen_time_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.ScreenTime),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.ScreenTime, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_calendar_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_calendar_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Calendar),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Calendar, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Device Assist")
                },
                supportingContent = {
                    Text("让助手调用 get_foreground_app / open_solace / notify_user 等本机关怀工具")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.DeviceAssist),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.DeviceAssist, it) }
                    )
                }
            )
            item(
                headlineContent = {
                    Text("Phone Control")
                },
                supportingContent = {
                    val a11yOn = context.isSolaceAccessibilityEnabled()
                    Text(
                        if (a11yOn) {
                            "无障碍已开启：dump_ui / ui_click / ui_swipe / ui_type / open_app"
                        } else {
                            "需开启系统无障碍中的 Solace，才能读界面并点击/滑动/输入"
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.PhoneControl),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.PhoneControl, it) }
                    )
                }
            )
        }

        CardGroup {
            item(
                headlineContent = { Text("后台使用监测") },
                supportingContent = {
                    Text("超时刷抖音等 App 时自动切回 Solace 提醒休息（需「使用情况访问」权限）")
                },
                trailingContent = {
                    Switch(
                        checked = companionAssist.monitorEnabled,
                        onCheckedChange = { enabled ->
                            updateCompanion { it.copy(monitorEnabled = enabled) }
                        }
                    )
                }
            )
            item(
                headlineContent = { Text("主动找我聊天") },
                supportingContent = {
                    Text("按人设主动发早安/晚间问候，或长时间没聊时来找你（通知进会话，不强打断）")
                },
                trailingContent = {
                    Switch(
                        checked = companionAssist.proactiveChatEnabled,
                        onCheckedChange = { enabled ->
                            updateCompanion { it.copy(proactiveChatEnabled = enabled) }
                        }
                    )
                }
            )
            item(
                headlineContent = { Text("LLM 生成提醒文案") },
                supportingContent = {
                    Text("开启后按助手人设生成话术；关闭则用固定兜底文案")
                },
                trailingContent = {
                    Switch(
                        checked = companionAssist.useLlmMessage,
                        onCheckedChange = { enabled ->
                            updateCompanion { it.copy(useLlmMessage = enabled) }
                        }
                    )
                }
            )
            item(
                headlineContent = { Text("高级 Shell (Shizuku)") },
                supportingContent = {
                    val status = when {
                        !DeviceShellExecutor.isShizukuAvailable() -> "Shizuku 未运行"
                        !DeviceShellExecutor.hasShizukuPermission() -> "已连接，待授权"
                        else -> "已授权"
                    }
                    Text("白名单 am/input 等命令。状态: $status")
                },
                trailingContent = {
                    Switch(
                        checked = companionAssist.enableAdvancedShell,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                runCatching {
                                    if (DeviceShellExecutor.isShizukuAvailable() &&
                                        !DeviceShellExecutor.hasShizukuPermission()
                                    ) {
                                        Shizuku.requestPermission(1001)
                                    }
                                }
                            }
                            updateCompanion { it.copy(enableAdvancedShell = enabled) }
                        }
                    )
                }
            )
        }

        if (companionAssist.proactiveChatEnabled) {
            OutlinedTextField(
                value = companionAssist.silenceHours.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.coerceIn(1, 72)?.let { hours ->
                        updateCompanion { it.copy(silenceHours = hours) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("沉默多久后主动找你（小时）") },
                supportingText = { Text("默认 6 小时没对话就按人设来一句开场白") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = companionAssist.proactiveCooldownMinutes.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.coerceIn(30, 1440)?.let { minutes ->
                        updateCompanion { it.copy(proactiveCooldownMinutes = minutes) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("主动聊天冷却（分钟）") },
                supportingText = { Text("两次主动找你的最小间隔，默认 180") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            CardGroup {
                item(
                    headlineContent = { Text("早安问候") },
                    supportingContent = {
                        Text("每天约 ${companionAssist.morningHour}:00 按人设说早安")
                    },
                    trailingContent = {
                        Switch(
                            checked = companionAssist.morningGreetingEnabled,
                            onCheckedChange = { enabled ->
                                updateCompanion { it.copy(morningGreetingEnabled = enabled) }
                            }
                        )
                    }
                )
                item(
                    headlineContent = { Text("晚间问候") },
                    supportingContent = {
                        Text("每天约 ${companionAssist.eveningHour}:00 按人设关心/道晚安")
                    },
                    trailingContent = {
                        Switch(
                            checked = companionAssist.eveningGreetingEnabled,
                            onCheckedChange = { enabled ->
                                updateCompanion { it.copy(eveningGreetingEnabled = enabled) }
                            }
                        )
                    }
                )
            }
            OutlinedTextField(
                value = companionAssist.morningHour.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.coerceIn(0, 23)?.let { hour ->
                        updateCompanion { it.copy(morningHour = hour) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("早安小时（0-23）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = companionAssist.eveningHour.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.coerceIn(0, 23)?.let { hour ->
                        updateCompanion { it.copy(eveningHour = hour) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("晚间小时（0-23）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }

        if (companionAssist.monitorEnabled || assistant.localTools.contains(LocalToolOption.DeviceAssist)) {
            OutlinedTextField(
                value = companionAssist.thresholdMinutes.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.coerceIn(1, 240)?.let { minutes ->
                        updateCompanion { it.copy(thresholdMinutes = minutes) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("连续使用阈值（分钟）") },
                supportingText = { Text("默认 30：同一 App 连续前台超过该时长触发提醒") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = companionAssist.cooldownMinutes.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.coerceIn(1, 720)?.let { minutes ->
                        updateCompanion { it.copy(cooldownMinutes = minutes) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("干预冷却（分钟）") },
                supportingText = { Text("同一 App 两次提醒的最小间隔，默认 45") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = companionAssist.monitoredPackages.joinToString("\n"),
                onValueChange = { raw ->
                    val packages = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    updateCompanion { it.copy(monitoredPackages = packages) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("监测包名（每行一个）") },
                supportingText = {
                    Text("留空则监测所有第三方 App；预置含抖音/快手/B 站等")
                },
                minLines = 4,
            )
        }
    }
}
