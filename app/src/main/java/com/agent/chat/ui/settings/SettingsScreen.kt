package com.agent.chat.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.domain.model.InteractionPreference
import com.agent.chat.domain.model.LingBanChatMode
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.ErrorSoftText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onPermissionsClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val colors = AgentThemeColors

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSummaryProviderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatusMessage()
    }

    if (uiState.isEditorOpen) {
        ProviderEditorScreen(
            isEditing = uiState.editingId != null,
            name = uiState.editorName,
            baseUrl = uiState.editorBaseUrl,
            apiKey = uiState.editorApiKey,
            modelName = uiState.editorModelName,
            isTesting = uiState.isTesting,
            inlineError = uiState.inlineError,
            inlineDebugDetail = uiState.inlineDebugDetail,
            onNameChange = viewModel::onEditorNameChange,
            onBaseUrlChange = viewModel::onEditorBaseUrlChange,
            onApiKeyChange = viewModel::onEditorApiKeyChange,
            onModelNameChange = viewModel::onEditorModelNameChange,
            onTest = viewModel::testEditorConfig,
            onBack = viewModel::closeEditor,
            onConfirm = viewModel::saveEditor,
            snackbarHostState = snackbarHostState,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable(onClick = viewModel::onTitleTapped),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateEditor,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增 Provider")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ChatDisplaySettingsSection(
                    naturalChatPaceEnabled = uiState.naturalChatPaceEnabled,
                    companionStyleEnabled = uiState.companionStyleEnabled,
                    chatMode = uiState.chatMode,
                    responseControllerEnabled = uiState.responseControllerEnabled,
                    splitBubbleByNewline = uiState.splitBubbleByNewline,
                    userNickname = uiState.userNickname,
                    proactiveEnabled = uiState.proactiveEnabled,
                    proactiveIdleHours = uiState.proactiveIdleHours,
                    onNaturalChatPaceChange = viewModel::setNaturalChatPaceEnabled,
                    onCompanionStyleChange = viewModel::setCompanionStyleEnabled,
                    onChatModeChange = viewModel::setChatMode,
                    onResponseControllerChange = viewModel::setResponseControllerEnabled,
                    onSplitByNewlineChange = viewModel::setSplitBubbleByNewline,
                    onNicknameChange = viewModel::setUserNickname,
                    onProactiveChange = viewModel::setProactiveEnabled,
                    onProactiveIdleHoursChange = viewModel::setProactiveIdleHours,
                )
            }

            item {
                HomeLocationSection(
                    hasHomeLocation = uiState.hasHomeLocation,
                    homeRadiusMeters = uiState.homeRadiusMeters,
                    onSetHomeToCurrentLocation = viewModel::setHomeToCurrentLocation,
                    onClearHomeLocation = viewModel::clearHomeLocation,
                    onRadiusChange = viewModel::setHomeRadius,
                )
            }

            item {
                InteractionPreferenceSection(
                    preference = uiState.interactionPreference,
                    onRomanticChange = viewModel::setRomanticConversation,
                    onFlirtingChange = viewModel::setFlirting,
                    onIntimateChange = viewModel::setIntimateConversation,
                    onRoleplayChange = viewModel::setInteractionRoleplay,
                )
            }

            item {
                if (onPermissionsClick != null) {
                    ToolSettingsSummaryCard(
                        settings = uiState.toolSettings,
                        onClick = onPermissionsClick,
                    )
                } else {
                    ToolSettingsSection(
                        settings = uiState.toolSettings,
                        onUpdate = viewModel::updateToolSettings,
                    )
                }
            }

            item {
                MemorySettingsSection(
                    providers = uiState.providers,
                    summaryProviderId = uiState.summaryProviderId,
                    extractThreshold = uiState.extractThreshold,
                    extractTokenEstimate = uiState.extractTokenEstimate,
                    promptMemoryTokenEstimate = uiState.promptMemoryTokenEstimate,
                    onPickSummaryProvider = { showSummaryProviderPicker = true },
                    onClearSummaryProvider = { viewModel.setSummaryProvider(null) },
                    onThresholdChange = viewModel::setExtractThreshold,
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Provider 配置",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    text = "API Key 使用 EncryptedSharedPreferences 加密存储",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                if (uiState.showDeveloperOptions) {
                    Text(
                        text = "开发者选项已开启 · 行内将显示技术错误细节",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = viewModel::openPromptLog) {
                        Text(
                            text = uiState.lastPromptLog?.let {
                                "查看最近 System Prompt（${it.charCount} 字 · ${it.timeLabel}）"
                            } ?: "查看最近 System Prompt（尚无记录，先发一条消息）",
                        )
                    }
                }
            }

            if (uiState.providers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "还没有 Provider，点右下角添加",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(uiState.providers, key = { it.id }) { provider ->
                    ProviderConfigItem(
                        config = provider,
                        isTesting = uiState.isTesting && uiState.testingProviderId == provider.id,
                        isSummaryProvider = provider.id == uiState.summaryProviderId,
                        onClick = { viewModel.openEditEditor(provider) },
                        onTest = { viewModel.testProvider(provider) },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showSummaryProviderPicker) {
        SummaryProviderPickerDialog(
            providers = uiState.providers,
            selectedId = uiState.summaryProviderId,
            onSelect = { id ->
                viewModel.setSummaryProvider(id)
                showSummaryProviderPicker = false
            },
            onUseChatDefault = {
                viewModel.setSummaryProvider(null)
                showSummaryProviderPicker = false
            },
            onDismiss = { showSummaryProviderPicker = false },
        )
    }

    if (uiState.showPromptLogDialog) {
        val entry = uiState.lastPromptLog
        AlertDialog(
            onDismissRequest = viewModel::dismissPromptLog,
            title = { Text("最近 System Prompt") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (entry == null) {
                        Text("暂无记录。发送一条聊天后会在此显示最终拼装结果。")
                    } else {
                        Text(
                            text = "agent=${entry.agentId} · model=${entry.modelName ?: "-"} · " +
                                "sections=${entry.sectionIds.joinToString(",")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = entry.systemPrompt,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPromptLog) { Text("关闭") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDisplaySettingsSection(
    naturalChatPaceEnabled: Boolean,
    companionStyleEnabled: Boolean,
    chatMode: LingBanChatMode,
    responseControllerEnabled: Boolean,
    splitBubbleByNewline: Boolean,
    userNickname: String,
    proactiveEnabled: Boolean,
    proactiveIdleHours: Int,
    onNaturalChatPaceChange: (Boolean) -> Unit,
    onCompanionStyleChange: (Boolean) -> Unit,
    onChatModeChange: (LingBanChatMode) -> Unit,
    onResponseControllerChange: (Boolean) -> Unit,
    onSplitByNewlineChange: (Boolean) -> Unit,
    onNicknameChange: (String) -> Unit,
    onProactiveChange: (Boolean) -> Unit,
    onProactiveIdleHoursChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(text = "伴侣感", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = userNickname,
            onValueChange = onNicknameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("你的昵称（用于 {{nickname}}）") },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "LingBan 聊天模式",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = chatMode.shortDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LingBanChatMode.entries.forEach { mode ->
                FilterChip(
                    selected = chatMode == mode,
                    onClick = { onChatModeChange(mode) },
                    label = { Text(mode.displayName) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSwitchRow(
            title = "真人聊天基础约束",
            subtitle = "注入基础口语约束；具体松紧由上方聊天模式决定。",
            checked = companionStyleEnabled,
            onCheckedChange = onCompanionStyleChange,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSwitchRow(
            title = "Response Controller",
            subtitle = "按当前模式评估回复；不达标时自动重生成。Debug 包会显示评分。",
            checked = responseControllerEnabled,
            onCheckedChange = onResponseControllerChange,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSwitchRow(
            title = "模拟真实聊天节奏",
            subtitle = "长回复拆成多条气泡，并模拟正在输入。",
            checked = naturalChatPaceEnabled,
            onCheckedChange = onNaturalChatPaceChange,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSwitchRow(
            title = "按换行拆气泡",
            subtitle = "优先按模型自己的换行拆分；关闭则按句号拆分。",
            checked = splitBubbleByNewline,
            onCheckedChange = onSplitByNewlineChange,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsSwitchRow(
            title = "贴心主动关心",
            subtitle = "按早晚/饭点/深夜与日历，在合适时机主动发一句关心（通知点进会话）。",
            checked = proactiveEnabled,
            onCheckedChange = onProactiveChange,
        )
        if (proactiveEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "基础闲置阈值约 ${proactiveIdleHours} 小时；日程临近时可更早轻轻提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = proactiveIdleHours.toFloat(),
                onValueChange = { onProactiveIdleHoursChange(it.toInt()) },
                valueRange = 1f..24f,
                steps = 22,
            )
        }
    }
}

@Composable
private fun HomeLocationSection(
    hasHomeLocation: Boolean,
    homeRadiusMeters: Int,
    onSetHomeToCurrentLocation: (Int?) -> Unit,
    onClearHomeLocation: () -> Unit,
    onRadiusChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(text = "家位置", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "设置家位置后，到家触发欢迎问候（需要开启「贴心主动关心」并授予定位权限）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasHomeLocation) {
            Text(
                text = "尚未设置。点击下方按钮用当前位置设置「家」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { onSetHomeToCurrentLocation(null) }) {
                Text("用当前位置设置为家")
            }
            return
        }

        Text(text = "触发半径：${homeRadiusMeters} 米", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = homeRadiusMeters.toFloat(),
            onValueChange = { onRadiusChange(it.toInt()) },
            valueRange = 100f..1500f,
            steps = 28,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row {
            TextButton(onClick = { onSetHomeToCurrentLocation(homeRadiusMeters) }) {
                Text("更新为当前位置")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onClearHomeLocation) {
                Text("清除家位置")
            }
        }
    }
}

@Composable
private fun ToolSettingsSummaryCard(
    settings: com.agent.chat.data.settings.ToolSettings,
    onClick: () -> Unit,
) {
    val enabledCount = listOf(
        settings.memoryEnabled, settings.timeEnabled, settings.batteryEnabled,
        settings.deviceEnabled, settings.calendarEnabled, settings.alarmEnabled,
        settings.locationEnabled, settings.appUsageEnabled, settings.notificationEnabled,
        settings.musicEnabled, settings.smsEnabled, settings.screenStateEnabled,
        settings.screenContentEnabled,
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(text = "AI 能力授权", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "已开启 $enabledCount 项能力，点击管理 AI 可使用的工具与权限",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolSettingsSection(
    settings: com.agent.chat.data.settings.ToolSettings,
    onUpdate: ((com.agent.chat.data.settings.ToolSettings) -> com.agent.chat.data.settings.ToolSettings) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    fun hasPermissions(vararg perms: String) = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val hasLocationPerm = hasPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val hasCalendarPerm = hasPermissions(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )
    val hasSmsPerm = hasPermissions(Manifest.permission.READ_SMS)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(text = "工具权限", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "控制 AI 可以调用哪些能力。开启后，AI 会在合适时机自动使用对应工具。敏感能力默认关闭，需手动授权。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        ToolSwitch("记忆读写", "跨对话记住你的偏好与关系", settings.memoryEnabled) {
            onUpdate { it.copy(memoryEnabled = it.memoryEnabled.not()) }
        }
        ToolSwitch("当前时间", null, settings.timeEnabled) {
            onUpdate { it.copy(timeEnabled = it.timeEnabled.not()) }
        }
        ToolSwitch("电量", null, settings.batteryEnabled) {
            onUpdate { it.copy(batteryEnabled = it.batteryEnabled.not()) }
        }
        ToolSwitch("设备信息", null, settings.deviceEnabled) {
            onUpdate { it.copy(deviceEnabled = it.deviceEnabled.not()) }
        }
        ToolSwitch(
            title = "日历",
            subtitle = "读写系统日程",
            checked = settings.calendarEnabled,
            systemPermGranted = if (settings.calendarEnabled) hasCalendarPerm else null,
        ) {
            val turningOn = !settings.calendarEnabled
            onUpdate { it.copy(calendarEnabled = !it.calendarEnabled) }
            if (turningOn && !hasCalendarPerm) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    ),
                )
            }
        }
        ToolSwitch("闹钟", "调起系统闹钟", settings.alarmEnabled) {
            onUpdate { it.copy(alarmEnabled = it.alarmEnabled.not()) }
        }
        ToolSwitch(
            title = "定位",
            subtitle = "让 AI 知道你在哪，可据此关心你（如到家问候、天气提醒）。默认关闭，开启后需授予系统位置权限。",
            checked = settings.locationEnabled,
            systemPermGranted = if (settings.locationEnabled) hasLocationPerm else null,
        ) {
            val turningOn = !settings.locationEnabled
            onUpdate { it.copy(locationEnabled = !it.locationEnabled) }
            if (turningOn && !hasLocationPerm) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                )
            }
        }
        ToolSwitch(
            title = "App 使用情况",
            subtitle = "AI 可了解你最近用了哪些 App，需跳转系统授予特殊权限",
            checked = settings.appUsageEnabled,
            systemPermGranted = null,
        ) {
            val turningOn = !settings.appUsageEnabled
            onUpdate { it.copy(appUsageEnabled = it.appUsageEnabled.not()) }
            if (turningOn) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        ToolSwitch(
            title = "通知感知",
            subtitle = "AI 可感知收到哪些通知（不含正文）。需跳转系统开启通知访问权限。",
            checked = settings.notificationEnabled,
            systemPermGranted = null,
        ) {
            val turningOn = !settings.notificationEnabled
            onUpdate { it.copy(notificationEnabled = it.notificationEnabled.not()) }
            if (turningOn) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        ToolSwitch(
            title = "音乐控制",
            subtitle = "AI 可查看/控制当前播放的音乐。需通知访问权限。",
            checked = settings.musicEnabled,
            systemPermGranted = null,
        ) {
            val turningOn = !settings.musicEnabled
            onUpdate { it.copy(musicEnabled = it.musicEnabled.not()) }
            if (turningOn) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        ToolSwitch(
            title = "短信读取",
            subtitle = "AI 可读取最近短信摘要（如验证码提醒等）",
            checked = settings.smsEnabled,
            systemPermGranted = if (settings.smsEnabled) hasSmsPerm else null,
        ) {
            val turningOn = !settings.smsEnabled
            onUpdate { it.copy(smsEnabled = it.smsEnabled.not()) }
            if (turningOn && !hasSmsPerm) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS),
                )
            }
        }
        ToolSwitch("屏幕状态", "亮屏/息屏/锁屏状态", settings.screenStateEnabled) {
            onUpdate { it.copy(screenStateEnabled = it.screenStateEnabled.not()) }
        }
        ToolSwitch(
            title = "屏幕内容感知",
            subtitle = "AI 可读取你当前屏幕的文字内容。需在系统设置中开启无障碍服务。",
            checked = settings.screenContentEnabled,
            systemPermGranted = null,
        ) {
            val turningOn = !settings.screenContentEnabled
            onUpdate { it.copy(screenContentEnabled = it.screenContentEnabled.not()) }
            if (turningOn) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        ) {
            Text("查看系统应用权限详情")
        }
    }
}

@Composable
private fun InteractionPreferenceSection(
    preference: InteractionPreference,
    onRomanticChange: (Boolean) -> Unit,
    onFlirtingChange: (Boolean) -> Unit,
    onIntimateChange: (Boolean) -> Unit,
    onRoleplayChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(text = "互动边界", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "用户全局偏好，不属于角色人设。开关=允许上限，非每句必用；结合话题/情绪/关系由 Runtime 决定是否激活。开启后无内容限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSwitchRow(
            title = "浪漫对话",
            subtitle = "用户主动聊感情/喜欢/在一起时，可自然回应",
            checked = preference.romanticConversation,
            onCheckedChange = onRomanticChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsSwitchRow(
            title = "暧昧互动",
            subtitle = "用户主动暧昧/撩/昵称时，可适度接梗",
            checked = preference.flirting,
            onCheckedChange = onFlirtingChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsSwitchRow(
            title = "亲密对话",
            subtitle = "用户主动亲密话题时，可在用户尺度内回应",
            checked = preference.intimateConversation,
            onCheckedChange = onIntimateChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsSwitchRow(
            title = "角色扮演",
            subtitle = "用户主动要故事/剧情/RP 时可进入",
            checked = preference.roleplay,
            onCheckedChange = onRoleplayChange,
        )
    }
}

@Composable
private fun ToolSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    systemPermGranted: Boolean? = null,
    onToggle: () -> Unit,
) {
    Column {
        SettingsSwitchRow(
            title = title,
            subtitle = subtitle ?: "",
            checked = checked,
            onCheckedChange = { onToggle() },
        )
        if (checked && systemPermGranted == false) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 0.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "已开启但系统权限未授予，AI 将无法使用此功能。请点击下方「查看系统应用权限详情」授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MemorySettingsSection(
    providers: List<ProviderConfig>,
    summaryProviderId: String?,
    extractThreshold: Int,
    extractTokenEstimate: Int,
    promptMemoryTokenEstimate: Int,
    onPickSummaryProvider: () -> Unit,
    onClearSummaryProvider: () -> Unit,
    onThresholdChange: (Int) -> Unit,
) {
    var sliderValue by remember(extractThreshold) {
        mutableFloatStateOf(extractThreshold.toFloat())
    }
    val summaryLabel = summaryProviderId
        ?.let { id -> providers.find { it.id == id } }
        ?.let { "${it.name}（${it.modelName}）" }
        ?: "跟随主对话模型"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            text = "对话记忆",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "增量摘要：每次只发送「上次摘要 + 新增消息」，与主对话模型可解耦。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "摘要专用模型",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = summaryLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (summaryProviderId != null) {
                TextButton(onClick = onClearSummaryProvider) {
                    Text("跟随主对话")
                }
            }
            TextButton(
                onClick = onPickSummaryProvider,
                enabled = providers.isNotEmpty(),
            ) {
                Text("选择模型")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "摘要触发阈值：$extractThreshold 条消息",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "建议 15–50；过低会频繁调用摘要接口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onThresholdChange(sliderValue.toInt()) },
            valueRange = MemorySettingsStore.MIN_THRESHOLD.toFloat()..
                MemorySettingsStore.MAX_THRESHOLD.toFloat(),
            steps = MemorySettingsStore.MAX_THRESHOLD - MemorySettingsStore.MIN_THRESHOLD - 1,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "预估 Token 消耗",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "· 每次摘要请求约 $extractTokenEstimate Token" +
                "（上次摘要≤${MemorySettingsStore.SUMMARY_MAX_CHARS}字 + 新增约 $extractThreshold 条消息）\n" +
                "· 相关记忆按当前问题检索注入，上限约 ${MemorySettingsStore.PROMPT_MEMORY_MAX_TOKENS} tokens" +
                "（约 $promptMemoryTokenEstimate Token 量级），低相关条目不拼接\n" +
                "· 建议为摘要选用更便宜的模型（如 DeepSeek Flash / mini）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryProviderPickerDialog(
    providers: List<ProviderConfig>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onUseChatDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择摘要模型") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "该模型仅用于记忆摘要，不影响主对话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onUseChatDefault,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (selectedId == null) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("跟随主对话模型")
                }
                providers.forEach { provider ->
                    TextButton(
                        onClick = { onSelect(provider.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (provider.id == selectedId) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("${provider.name} · ${provider.modelName}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ProviderConfigItem(
    config: ProviderConfig,
    isTesting: Boolean,
    isSummaryProvider: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = config.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isSummaryProvider) {
                Text(
                    text = "摘要",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${config.modelName} · ${config.providerType.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = config.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Key: ${config.maskedApiKey()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            IconButton(onClick = onTest, enabled = !isTesting) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = "测试连接",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
