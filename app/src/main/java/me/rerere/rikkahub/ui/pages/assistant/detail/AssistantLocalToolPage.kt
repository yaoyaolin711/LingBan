package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.device.CompanionAssistSetting
import me.rerere.rikkahub.data.device.DeviceShellExecutor
import me.rerere.rikkahub.data.device.ShizukuBootstrap
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.CompanionMonitorService
import me.rerere.rikkahub.service.SolaceAccessibilityService
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
            autoApprovedTools = settings.autoApprovedTools,
            onUpdate = { vm.update(it) },
            onUpdateCompanion = { next ->
                scope.launch {
                    settingsStore.update { it.copy(companionAssist = next) }
                }
            },
            onUpdateAutoApprovedTools = { transform ->
                scope.launch {
                    settingsStore.update { settings ->
                        settings.copy(autoApprovedTools = transform(settings.autoApprovedTools))
                    }
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
    autoApprovedTools: Set<String>,
    onUpdate: (Assistant) -> Unit,
    onUpdateCompanion: (CompanionAssistSetting) -> Unit,
    onUpdateAutoApprovedTools: ((Set<String>) -> Set<String>) -> Unit,
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

    val locationPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                displayName = { Text("粗略定位") },
                usage = { Text("用于获取大致位置，供助手回答「我在哪」等问题") },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                displayName = { Text("精确定位") },
                usage = { Text("用于获取更准确的经纬度") },
                required = false
            ),
        )
    )
    PermissionManager(permissionState = locationPermissionState)

    var showShizukuGuide by remember { mutableStateOf(false) }

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
        if (enabled && option == LocalToolOption.DeviceInfo && !locationPermissionState.allPermissionsGranted) {
            locationPermissionState.requestPermissions()
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
            if (assistant.localTools.contains(LocalToolOption.PhoneControl)) {
                item(
                    headlineContent = {
                        Text("打开应用无需确认")
                    },
                    supportingContent = {
                        Text("开启后 open_app 自动执行；也可在聊天里点「始终允许」")
                    },
                    trailingContent = {
                        Switch(
                            checked = autoApprovedTools.contains("open_app"),
                            onCheckedChange = { enabled ->
                                onUpdateAutoApprovedTools { current ->
                                    if (enabled) current + "open_app" else current - "open_app"
                                }
                            }
                        )
                    }
                )
                item(
                    headlineContent = {
                        Text("界面操作无需确认")
                    },
                    supportingContent = {
                        Text("自动允许 ui_click / ui_swipe / ui_type / ui_global")
                    },
                    trailingContent = {
                        val uiTools = setOf("ui_click", "ui_swipe", "ui_type", "ui_global")
                        Switch(
                            checked = uiTools.all { it in autoApprovedTools },
                            onCheckedChange = { enabled ->
                                onUpdateAutoApprovedTools { current ->
                                    if (enabled) current + uiTools else current - uiTools
                                }
                            }
                        )
                    }
                )
            }
            item(
                headlineContent = {
                    Text("Device Info")
                },
                supportingContent = {
                    Text("机型/系统、电量、定位；日历请单独开启上方 Calendar")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.DeviceInfo),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.DeviceInfo, it) }
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
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("允许助手强控手机")
                        TextButton(onClick = { showShizukuGuide = true }) {
                            Icon(
                                imageVector = Lucide.CircleHelp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("教程")
                        }
                    }
                },
                supportingContent = {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var refreshTick by remember { mutableIntStateOf(0) }
                    var starting by remember { mutableStateOf(false) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                refreshTick++
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }
                    val installed = remember(refreshTick) { ShizukuBootstrap.isInstalled(context) }
                    val running = remember(refreshTick) { DeviceShellExecutor.isShizukuAvailable() }
                    val granted = remember(refreshTick) { DeviceShellExecutor.hasShizukuPermission() }
                    val status = when {
                        !installed -> "还没装权限工具 Shizuku"
                        !running -> "Shizuku 已安装，但还没启动"
                        !granted -> "Shizuku 已启动，还差给本应用授权"
                        else -> "已就绪，助手可以使用强控能力"
                    }
                    val actionLabel = when {
                        starting -> "处理中…"
                        !installed -> "去安装 Shizuku"
                        !running -> "去启动 Shizuku"
                        !granted -> "去授权本应用"
                        else -> "检查状态"
                    }
                    Column {
                        Text("打开后，助手可强制关闭 App、模拟按键等。当前：$status")
                        TextButton(
                            enabled = !starting,
                            onClick = {
                                starting = true
                                scope.launch {
                                    val outcome = ShizukuBootstrap.oneClickPrepare(context)
                                    refreshTick++
                                    starting = false
                                    val message = when (outcome) {
                                        ShizukuBootstrap.Outcome.AlreadyReady -> "已经准备好了，可以打开右侧开关"
                                        ShizukuBootstrap.Outcome.Started -> "已自动启动，请确认授权弹窗"
                                        ShizukuBootstrap.Outcome.NeedPermission -> "请在弹窗里点「允许」"
                                        ShizukuBootstrap.Outcome.OpenedManager ->
                                            "已打开 Shizuku，请点「启动」，完成后再回到这里"
                                        ShizukuBootstrap.Outcome.NeedInstall ->
                                            "请先安装 Shizuku，装好后回来再点这个按钮"
                                        ShizukuBootstrap.Outcome.RootStartFailed ->
                                            "自动启动失败，已打开 Shizuku，请手动点「启动」"
                                    }
                                    toaster.show(
                                        message = message,
                                        type = when (outcome) {
                                            ShizukuBootstrap.Outcome.AlreadyReady,
                                            ShizukuBootstrap.Outcome.Started,
                                            -> ToastType.Success
                                            ShizukuBootstrap.Outcome.RootStartFailed -> ToastType.Warning
                                            else -> ToastType.Info
                                        },
                                    )
                                    if (outcome == ShizukuBootstrap.Outcome.AlreadyReady ||
                                        outcome == ShizukuBootstrap.Outcome.Started
                                    ) {
                                        updateCompanion { it.copy(enableAdvancedShell = true) }
                                    }
                                }
                            },
                        ) {
                            Text(actionLabel)
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = companionAssist.enableAdvancedShell,
                        onCheckedChange = { enabled ->
                            updateCompanion { it.copy(enableAdvancedShell = enabled) }
                            if (enabled) {
                                scope.launch {
                                    val outcome = ShizukuBootstrap.oneClickPrepare(context)
                                    val message = when (outcome) {
                                        ShizukuBootstrap.Outcome.AlreadyReady,
                                        ShizukuBootstrap.Outcome.Started,
                                        -> null
                                        ShizukuBootstrap.Outcome.NeedPermission -> "请在弹窗里点「允许」"
                                        ShizukuBootstrap.Outcome.OpenedManager ->
                                            "已打开 Shizuku，请点「启动」后再回来"
                                        ShizukuBootstrap.Outcome.NeedInstall -> "请先安装 Shizuku"
                                        ShizukuBootstrap.Outcome.RootStartFailed ->
                                            "请到 Shizuku 里手动点「启动」"
                                    }
                                    message?.let {
                                        toaster.show(message = it, type = ToastType.Info)
                                    }
                                }
                            }
                        }
                    )
                }
            )
        }

        if (showShizukuGuide) {
            AlertDialog(
                onDismissRequest = { showShizukuGuide = false },
                title = { Text("强控手机怎么用") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("这个开关用来让助手做普通 App 做不到的事，比如强制关闭抖音、模拟按 Home 键。")
                        Text("它依赖一个叫 Shizuku 的免费工具（不用 Root）。按下面做即可：")
                        Text("1. 点「去安装 Shizuku」，从应用商店装好。")
                        Text("2. 再点「去启动 Shizuku」。在 Shizuku 里用「无线调试」启动（Android 11+ 不用电脑）。")
                        Text("3. 回到这里，若弹出授权，点「允许」。")
                        Text("4. 打开右侧开关。之后聊天里助手要用强控时，仍会先问你同不同意。")
                        Text("提示：手机重启后，通常要重新进 Shizuku 点一次「启动」。")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShizukuGuide = false }) {
                        Text("知道了")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showShizukuGuide = false
                            scope.launch {
                                ShizukuBootstrap.oneClickPrepare(context)
                            }
                        }
                    ) {
                        Text("按教程开始")
                    }
                },
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
