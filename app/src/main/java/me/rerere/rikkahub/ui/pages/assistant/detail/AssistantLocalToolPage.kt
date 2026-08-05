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
import androidx.compose.runtime.LaunchedEffect
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
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.accessibility.AccessibilityKeepAlive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.device.CompanionAssistSetting
import me.rerere.rikkahub.data.device.DeviceShellExecutor
import me.rerere.rikkahub.data.device.ShizukuBootstrap
import me.rerere.rikkahub.data.companion.policy.CompanionActionLevel
import me.rerere.rikkahub.data.life.LifeContextResolver
import me.rerere.rikkahub.data.life.LifeContextSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CompanionOverlayStyle
import me.rerere.rikkahub.data.model.CompanionPixelPetSkin
import me.rerere.rikkahub.data.model.resolvedCompanionOverlayStyle
import me.rerere.rikkahub.data.model.withCompanionOverlayStyle
import me.rerere.rikkahub.overlay.pet.CompanionPetHost
import me.rerere.rikkahub.service.CompanionMonitorService
import me.rerere.rikkahub.service.SolaceAccessibilityService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ChipScrollRow
import me.rerere.rikkahub.ui.components.ui.IntOutlinedTextField
import me.rerere.rikkahub.ui.components.ui.chipUnshrinkable
import me.rerere.rikkahub.ui.components.ui.permission.PermissionInfo
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.HeartCheck
import me.rerere.rikkahub.utils.canDrawOverlays
import me.rerere.rikkahub.utils.hasUsageStatsPermission
import me.rerere.rikkahub.utils.isSolaceAccessibilityEnabled
import me.rerere.rikkahub.utils.openAccessibilitySettings
import me.rerere.rikkahub.utils.openOverlayPermissionSettings
import me.rerere.rikkahub.utils.openUsageAccessSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import androidx.compose.material3.FilterChip

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
            lifeContext = settings.lifeContext,
            autoApprovedTools = settings.autoApprovedTools,
            onUpdate = { vm.update(it) },
            onUpdateCompanion = { next ->
                scope.launch {
                    settingsStore.update { it.copy(companionAssist = next) }
                }
            },
            onUpdateLifeContext = { next ->
                scope.launch {
                    settingsStore.update { it.copy(lifeContext = next) }
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
    lifeContext: LifeContextSetting,
    autoApprovedTools: Set<String>,
    onUpdate: (Assistant) -> Unit,
    onUpdateCompanion: (CompanionAssistSetting) -> Unit,
    onUpdateLifeContext: (LifeContextSetting) -> Unit,
    onUpdateAutoApprovedTools: ((Set<String>) -> Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val nav = LocalNavController.current
    val scope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val lifeContextResolver = koinInject<LifeContextResolver>()
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
    var showA11yKeepAliveGuide by remember { mutableStateOf(false) }
    var showDeviceToolsConfirm by remember { mutableStateOf(false) }

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        if (enabled && (option == LocalToolOption.ScreenTime || option == LocalToolOption.DeviceAssist) &&
            !context.hasUsageStatsPermission()
        ) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.PhoneControl) {
            if (!context.isSolaceAccessibilityEnabled()) {
                toaster.show(
                    message = "请在系统无障碍设置中开启 Solace，才能操控手机界面",
                    type = ToastType.Warning,
                )
                context.openAccessibilitySettings()
            }
            // OEM force-stop strips accessibility; request battery whitelist + show keep-alive guide.
            AccessibilityKeepAlive.requestIgnoreBatteryOptimizations(context)
            showA11yKeepAliveGuide = true
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
            val settings = settingsStore.settingsFlow.value.copy(companionAssist = next)
            CompanionMonitorService.syncWithSettings(context, settings)
        }
    }

    fun updateLifeContext(enabled: Boolean) {
        onUpdateLifeContext(lifeContext.copy(enabled = enabled))
        if (enabled && !context.hasUsageStatsPermission()) {
            toaster.show(message = permissionRequiredText, type = ToastType.Warning)
            context.openUsageAccessSettings()
        }
    }

    var restPreview by remember { mutableStateOf("—") }
    LaunchedEffect(lifeContext.enabled) {
        if (!lifeContext.enabled) {
            restPreview = "已关闭"
            return@LaunchedEffect
        }
        restPreview = "读取中…"
        restPreview = runCatching {
            val settings = settingsStore.settingsFlow.value.copy(lifeContext = lifeContext)
            val snapshot = lifeContextResolver.readSnapshot(settings, forceRefresh = true)
            lifeContextResolver.formatForUi(snapshot)
        }.getOrElse { "暂无足够数据" }
    }

    fun updateProactiveChat(enabled: Boolean) {
        onUpdate(assistant.copy(proactiveChatEnabled = enabled))
        scope.launch {
            val clearGlobal = companionAssist.proactiveChatEnabled
            if (clearGlobal) {
                onUpdateCompanion(companionAssist.copy(proactiveChatEnabled = false))
            }
            val settings = settingsStore.settingsFlow.value.let { s ->
                s.copy(
                    companionAssist = if (clearGlobal) {
                        s.companionAssist.copy(proactiveChatEnabled = false)
                    } else {
                        s.companionAssist
                    },
                    assistants = s.assistants.map {
                        if (it.id == assistant.id) it.copy(proactiveChatEnabled = enabled) else it
                    },
                )
            }
            CompanionMonitorService.syncWithSettings(context, settings)
        }
    }

    val petHost = koinInject<CompanionPetHost>()

    fun updateCompanionMode(enabled: Boolean) {
        if (enabled) {
            if (!context.canDrawOverlays()) {
                toaster.show(message = "陪伴悬浮头像需要悬浮窗权限", type = ToastType.Warning)
                context.openOverlayPermissionSettings()
            }
            onUpdate(
                assistant.copy(
                    enableCompanion = true,
                )
            )
            return
        }
        // 关闭陪伴模式 = 关掉悬浮头像 + 本助手主动找人 + 后台监测，并停掉 FGS
        onUpdate(
            assistant.copy(
                enableCompanion = false,
                companionPetEnabled = false,
                companionOverlayStyle = CompanionOverlayStyle.AVATAR,
                proactiveChatEnabled = false,
            )
        )
        runCatching { petHost.hide() }
        updateCompanion {
            it.copy(
                monitorEnabled = false,
                proactiveChatEnabled = false,
            )
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
                headlineContent = { Text("陪伴模式") },
                supportingContent = {
                    Text("开启后显示悬浮伴侣（头像或像素桌宠）；主动找人时旁侧出短气泡。关闭会停监测与主动找人")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.enableCompanion,
                        onCheckedChange = { enabled -> updateCompanionMode(enabled) }
                    )
                }
            )
            if (assistant.enableCompanion) {
                item(
                    headlineContent = { Text("悬浮样式") },
                    supportingContent = {
                        Text("头像=圆形伴侣头像；像素桌宠=WebView SVG 角色（需悬浮窗权限），可选皮肤")
                    },
                    trailingContent = {}
                )
                item(
                    headlineContent = { Text("主动动作上限") },
                    supportingContent = {
                        Text("仅消息=通知；轻量=notify/open_solace；设备控制=超时可回桌面（需手机控制）")
                    },
                    trailingContent = {}
                )
            }
        }

        CardGroup {
            item(
                onClick = { nav.navigate(Screen.SettingIntimate) },
                leadingContent = { Icon(HugeIcons.HeartCheck, null) },
                headlineContent = { Text("亲密互动") },
                supportingContent = {
                    Text("信息资源不足，暂缓开发。需要你提出建议，这将决定功能去留与方向。")
                },
                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
            )
        }

        if (assistant.enableCompanion) {
            ChipScrollRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    CompanionOverlayStyle.AVATAR to "头像",
                    CompanionOverlayStyle.PIXEL_PET to "像素桌宠",
                ).forEach { (style, label) ->
                    FilterChip(
                        selected = assistant.resolvedCompanionOverlayStyle() == style,
                        onClick = {
                            if (style == CompanionOverlayStyle.PIXEL_PET && !context.canDrawOverlays()) {
                                toaster.show(message = "像素桌宠需要悬浮窗权限", type = ToastType.Warning)
                                context.openOverlayPermissionSettings()
                            }
                            onUpdate(assistant.withCompanionOverlayStyle(style))
                        },
                        label = { Text(label) },
                        modifier = Modifier.chipUnshrinkable(),
                    )
                }
            }
        }

        if (assistant.enableCompanion &&
            assistant.resolvedCompanionOverlayStyle() == CompanionOverlayStyle.PIXEL_PET
        ) {
            ChipScrollRow(modifier = Modifier.fillMaxWidth()) {
                CompanionPixelPetSkin.entries.forEach { skin ->
                    FilterChip(
                        selected = assistant.companionPixelPetSkin == skin,
                        onClick = {
                            onUpdate(assistant.copy(companionPixelPetSkin = skin))
                        },
                        label = { Text(skin.displayName) },
                        modifier = Modifier.chipUnshrinkable(),
                    )
                }
            }
        }

        if (assistant.enableCompanion) {
            ChipScrollRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    CompanionActionLevel.MESSAGE_ONLY to "仅消息",
                    CompanionActionLevel.SOFT_TOOLS to "轻量工具",
                    CompanionActionLevel.DEVICE_TOOLS to "设备控制",
                ).forEach { (level, label) ->
                    FilterChip(
                        selected = assistant.companionActionLevel == level,
                        onClick = {
                            if (level == CompanionActionLevel.DEVICE_TOOLS &&
                                assistant.companionActionLevel != CompanionActionLevel.DEVICE_TOOLS
                            ) {
                                showDeviceToolsConfirm = true
                            } else {
                                onUpdate(assistant.copy(companionActionLevel = level))
                            }
                        },
                        label = { Text(label) },
                        modifier = Modifier.chipUnshrinkable(),
                    )
                }
            }
        }

        CardGroup {
            item(
                headlineContent = { Text("后台使用监测") },
                supportingContent = {
                    Text("超时刷别的 App 时，伴侣会用人设主动找你（需「使用情况访问」权限）")
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
                headlineContent = { Text("作息感知") },
                supportingContent = {
                    Text(
                        "根据屏幕使用估计休息时间，注入对话并用于早安问候" +
                            (if (lifeContext.enabled) "\n预览：$restPreview" else "")
                    )
                },
                trailingContent = {
                    Switch(
                        checked = lifeContext.enabled,
                        onCheckedChange = { enabled ->
                            updateLifeContext(enabled)
                        }
                    )
                }
            )
            item(
                headlineContent = { Text("主动找我聊天") },
                supportingContent = {
                    Text("仅对本助手生效：按人设发早安/晚间问候，或长时间没聊时来找你（通知进会话，不强打断）")
                },
                trailingContent = {
                    Switch(
                        checked = assistant.proactiveChatEnabled || companionAssist.proactiveChatEnabled,
                        onCheckedChange = { enabled ->
                            updateProactiveChat(enabled)
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
                headlineContent = { Text("快测模式") },
                supportingContent = {
                    Text("阈值/冷却 1 分钟，沉默按分钟计，重度 2 分钟；测完请关")
                },
                trailingContent = {
                    Switch(
                        checked = companionAssist.companionTestMode,
                        onCheckedChange = { enabled ->
                            updateCompanion { it.copy(companionTestMode = enabled) }
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

        if (showDeviceToolsConfirm) {
            AlertDialog(
                onDismissRequest = { showDeviceToolsConfirm = false },
                title = { Text("允许设备控制？") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("开启后，使用关怀在「刷太久」时可能自动按 Home 回到桌面，再打开 Solace 提醒你。")
                        Text("仅限白名单动作（回到桌面），不会自由点击界面或支付。需先开启「手机控制」和无障碍。")
                        Text("夜间（0–6 点）不会自动控机。")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeviceToolsConfirm = false
                            val needPhone = !assistant.localTools.contains(LocalToolOption.PhoneControl)
                            if (needPhone) {
                                if (!context.isSolaceAccessibilityEnabled()) {
                                    toaster.show(
                                        message = "请在系统无障碍设置中开启 Solace",
                                        type = ToastType.Warning,
                                    )
                                    context.openAccessibilitySettings()
                                }
                                AccessibilityKeepAlive.requestIgnoreBatteryOptimizations(context)
                                showA11yKeepAliveGuide = true
                            }
                            onUpdate(
                                assistant.copy(
                                    companionActionLevel = CompanionActionLevel.DEVICE_TOOLS,
                                    localTools = if (needPhone) {
                                        assistant.localTools + LocalToolOption.PhoneControl
                                    } else {
                                        assistant.localTools
                                    },
                                )
                            )
                        }
                    ) {
                        Text("确认开启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeviceToolsConfirm = false }) {
                        Text("取消")
                    }
                },
            )
        }

        if (showA11yKeepAliveGuide) {
            AlertDialog(
                onDismissRequest = { showA11yKeepAliveGuide = false },
                title = { Text("让无障碍尽量一直开着") },
                text = {
                    Text(AccessibilityKeepAlive.keepAliveGuideText())
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            AccessibilityKeepAlive.requestIgnoreBatteryOptimizations(context)
                        }
                    ) {
                        Text("电池无限制")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                AccessibilityKeepAlive.openOemAutostartSettings(context)
                            }
                        ) {
                            Text("自启动设置")
                        }
                        TextButton(
                            onClick = {
                                showA11yKeepAliveGuide = false
                                AccessibilityKeepAlive.openAccessibilitySettings(context)
                            }
                        ) {
                            Text("去开无障碍")
                        }
                    }
                },
            )
        }

        if (assistant.proactiveChatEnabled || companionAssist.proactiveChatEnabled) {
            IntOutlinedTextField(
                value = companionAssist.silenceHours,
                onValueChange = { hours ->
                    updateCompanion { it.copy(silenceHours = hours) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 1..72,
                label = { Text("沉默多久后主动找你（小时）") },
                supportingText = { Text("默认 6 小时没对话就按人设来一句开场白") },
            )
            IntOutlinedTextField(
                value = companionAssist.proactiveCooldownMinutes,
                onValueChange = { minutes ->
                    updateCompanion { it.copy(proactiveCooldownMinutes = minutes) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 30..1440,
                label = { Text("主动聊天冷却（分钟）") },
                supportingText = { Text("两次主动找你的最小间隔，默认 180") },
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
            IntOutlinedTextField(
                value = companionAssist.morningHour,
                onValueChange = { hour ->
                    updateCompanion { it.copy(morningHour = hour) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 0..23,
                label = { Text("早安小时（0-23）") },
            )
            IntOutlinedTextField(
                value = companionAssist.eveningHour,
                onValueChange = { hour ->
                    updateCompanion { it.copy(eveningHour = hour) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 0..23,
                label = { Text("晚间小时（0-23）") },
            )
            IntOutlinedTextField(
                value = companionAssist.quietHourStart,
                onValueChange = { hour ->
                    updateCompanion { it.copy(quietHourStart = hour) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 0..23,
                label = { Text("静默开始小时") },
                supportingText = { Text("默认 0：该时段优先通知、不控机") },
            )
            IntOutlinedTextField(
                value = companionAssist.quietHourEnd,
                onValueChange = { hour ->
                    updateCompanion { it.copy(quietHourEnd = hour) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 0..23,
                label = { Text("静默结束小时（含）") },
                supportingText = { Text("默认 6；可跨午夜如 22–6") },
            )
            IntOutlinedTextField(
                value = companionAssist.maxProactivePerDay,
                onValueChange = { n ->
                    updateCompanion { it.copy(maxProactivePerDay = n) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 1..50,
                label = { Text("每日主动上限") },
                supportingText = { Text("使用关怀 + 主动聊天合计次数，默认 8") },
            )
        }

        if (companionAssist.monitorEnabled || assistant.localTools.contains(LocalToolOption.DeviceAssist)) {
            IntOutlinedTextField(
                value = companionAssist.thresholdMinutes,
                onValueChange = { minutes ->
                    updateCompanion { it.copy(thresholdMinutes = minutes) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 1..240,
                label = { Text("连续使用阈值（分钟）") },
                supportingText = { Text("默认 30：同一 App 连续前台超过该时长触发提醒") },
            )
            IntOutlinedTextField(
                value = companionAssist.cooldownMinutes,
                onValueChange = { minutes ->
                    updateCompanion { it.copy(cooldownMinutes = minutes) }
                },
                modifier = Modifier.fillMaxWidth(),
                range = 1..720,
                label = { Text("干预冷却（分钟）") },
                supportingText = { Text("同一 App 两次提醒的最小间隔，默认 45") },
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
