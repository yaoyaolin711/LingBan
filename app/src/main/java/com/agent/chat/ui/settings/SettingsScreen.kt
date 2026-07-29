package com.agent.chat.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.data.memory.MemorySettingsStore
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.ui.theme.ErrorSoftText
import com.agent.chat.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSummaryProviderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatusMessage()
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
                    splitBubbleByNewline = uiState.splitBubbleByNewline,
                    userNickname = uiState.userNickname,
                    proactiveEnabled = uiState.proactiveEnabled,
                    proactiveIdleHours = uiState.proactiveIdleHours,
                    onNaturalChatPaceChange = viewModel::setNaturalChatPaceEnabled,
                    onCompanionStyleChange = viewModel::setCompanionStyleEnabled,
                    onSplitByNewlineChange = viewModel::setSplitBubbleByNewline,
                    onNicknameChange = viewModel::setUserNickname,
                    onProactiveChange = viewModel::setProactiveEnabled,
                    onProactiveIdleHoursChange = viewModel::setProactiveIdleHours,
                )
            }

            item {
                ToolSettingsSection(
                    settings = uiState.toolSettings,
                    onUpdate = viewModel::updateToolSettings,
                )
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
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
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

    if (uiState.isEditorOpen) {
        ProviderEditorDialog(
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
            onDismiss = viewModel::closeEditor,
            onConfirm = viewModel::saveEditor,
        )
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
}

@Composable
private fun ChatDisplaySettingsSection(
    naturalChatPaceEnabled: Boolean,
    companionStyleEnabled: Boolean,
    splitBubbleByNewline: Boolean,
    userNickname: String,
    proactiveEnabled: Boolean,
    proactiveIdleHours: Int,
    onNaturalChatPaceChange: (Boolean) -> Unit,
    onCompanionStyleChange: (Boolean) -> Unit,
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
        SettingsSwitchRow(
            title = "口语伴侣风格",
            subtitle = "自动注入短句、口语、少列表等风格层，让回复更像真人聊天。",
            checked = companionStyleEnabled,
            onCheckedChange = onCompanionStyleChange,
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
            title = "闲置主动问候",
            subtitle = "一段时间没聊时，可能发一句自然关心（通知点进会话）。",
            checked = proactiveEnabled,
            onCheckedChange = onProactiveChange,
        )
        if (proactiveEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "闲置约 ${proactiveIdleHours} 小时后可能主动找你",
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
private fun ToolSettingsSection(
    settings: com.agent.chat.data.settings.ToolSettings,
    onUpdate: ((com.agent.chat.data.settings.ToolSettings) -> com.agent.chat.data.settings.ToolSettings) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(text = "工具权限", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "开启后，模型可在对话中调用对应能力。敏感能力默认关闭。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        ToolSwitch("记忆读写", "跨对话记住偏好与关系", settings.memoryEnabled) {
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
        ToolSwitch("日历", "读写系统日程", settings.calendarEnabled) {
            onUpdate { it.copy(calendarEnabled = it.calendarEnabled.not()) }
            if (!settings.calendarEnabled) {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_CALENDAR,
                        android.Manifest.permission.WRITE_CALENDAR,
                    ),
                )
            }
        }
        ToolSwitch("闹钟", "调起系统闹钟", settings.alarmEnabled) {
            onUpdate { it.copy(alarmEnabled = it.alarmEnabled.not()) }
        }
        ToolSwitch("定位", "粗略位置，默认关", settings.locationEnabled) {
            onUpdate { it.copy(locationEnabled = it.locationEnabled.not()) }
            if (!settings.locationEnabled) {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                )
            }
        }
        ToolSwitch("App 使用情况", "需特殊权限，默认关", settings.appUsageEnabled) {
            onUpdate { it.copy(appUsageEnabled = it.appUsageEnabled.not()) }
            if (!settings.appUsageEnabled) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
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
            Text("打开系统应用权限页")
        }
    }
}

@Composable
private fun ToolSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    SettingsSwitchRow(
        title = title,
        subtitle = subtitle ?: "",
        checked = checked,
        onCheckedChange = { onToggle() },
    )
    Spacer(modifier = Modifier.height(6.dp))
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
                "· 注入对话的记忆上限 ${MemorySettingsStore.PROMPT_MEMORY_MAX_CHARS} 字" +
                "（约 $promptMemoryTokenEstimate Token），超出部分留在库中不拼接\n" +
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

@Composable
private fun ProviderEditorDialog(
    isEditing: Boolean,
    name: String,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    isTesting: Boolean,
    inlineError: String?,
    inlineDebugDetail: String?,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑 Provider" else "新增 Provider") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "类型：OpenAI Compatible",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名称") },
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL") },
                    isError = inlineError != null,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key") },
                    isError = inlineError != null,
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model Name") },
                )
                if (!inlineError.isNullOrBlank()) {
                    Text(
                        text = inlineError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorSoftText,
                    )
                    if (!inlineDebugDetail.isNullOrBlank()) {
                        Text(
                            text = inlineDebugDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                TextButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text("测试连接")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
