package com.agent.chat.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.data.provider.ProviderDefaults
import com.agent.chat.data.provider.ProviderPreset
import com.agent.chat.domain.model.ProviderConfig
import com.agent.chat.domain.model.ProviderType
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.ErrorSoftText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelApiScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
            providerType = uiState.editorProviderType,
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

    ModelApiListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onEditProvider = viewModel::openEditEditor,
        onTestProvider = viewModel::testProvider,
        onDeleteProvider = viewModel::deleteProvider,
        onOpenCreateEditor = viewModel::openCreateEditor,
        onOpenPresetEditor = viewModel::openPresetEditor,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelApiListScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onEditProvider: (ProviderConfig) -> Unit,
    onTestProvider: (ProviderConfig) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onOpenCreateEditor: () -> Unit,
    onOpenPresetEditor: (ProviderPreset) -> Unit,
) {
    val colors = AgentThemeColors
    var showPresets by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "模型与 API",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "配置密钥、接口地址与对话模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Security notice ──────────────────────────────────────────
            item {
                Text(
                    text = "API Key 使用系统加密存储，仅保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            // ─── My Providers ─────────────────────────────────────────────
            if (uiState.providers.isNotEmpty()) {
                item {
                    SectionHeader(title = "我的配置")
                }
                items(uiState.providers, key = { it.id }) { provider ->
                    ModelApiProviderCard(
                        config = provider,
                        isTesting = uiState.isTesting && uiState.testingProviderId == provider.id,
                        onClick = { onEditProvider(provider) },
                        onTest = { onTestProvider(provider) },
                        onDelete = { onDeleteProvider(provider.id) },
                    )
                }
            }

            // ─── Add custom ───────────────────────────────────────────────
            item {
                OutlinedButton(
                    onClick = onOpenCreateEditor,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("自定义添加")
                }
            }

            // ─── Presets section ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPresets = !showPresets }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "快速添加（选择服务商）",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (showPresets) "收起" else "展开",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = showPresets,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProviderDefaults.PRESETS.forEach { preset ->
                            val alreadyAdded = uiState.providers.any { p ->
                                p.baseUrl.trimEnd('/') == preset.baseUrl.trimEnd('/')
                            }
                            PresetChip(
                                preset = preset,
                                alreadyAdded = alreadyAdded,
                                onClick = { onOpenPresetEditor(preset) },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val colors = AgentThemeColors
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun PresetChip(
    preset: ProviderPreset,
    alreadyAdded: Boolean,
    onClick: () -> Unit,
) {
    val colors = AgentThemeColors
    val bg = if (alreadyAdded) colors.accent.copy(alpha = 0.12f) else colors.surface
    val border = if (alreadyAdded) colors.accent.copy(alpha = 0.4f) else colors.outline

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Provider type icon badge
        val typeColor = when (preset.providerType) {
            ProviderType.ANTHROPIC -> Color(0xFFD4532A)
            ProviderType.GOOGLE_GEMINI -> Color(0xFF4285F4)
            ProviderType.OPENAI_COMPATIBLE -> colors.accent
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(typeColor),
        )
        Text(
            text = preset.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (alreadyAdded) colors.accent else colors.textPrimary,
            fontWeight = if (alreadyAdded) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (alreadyAdded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已添加",
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
        }
        if (preset.supportsVision) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = "支持视觉",
                tint = colors.textSecondary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ModelApiProviderCard(
    config: ProviderConfig,
    isTesting: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AgentThemeColors
    val typeLabel = when (config.providerType) {
        ProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
        ProviderType.ANTHROPIC -> "Anthropic"
        ProviderType.GOOGLE_GEMINI -> "Google Gemini"
    }
    val typeColor = when (config.providerType) {
        ProviderType.ANTHROPIC -> Color(0xFFD4532A)
        ProviderType.GOOGLE_GEMINI -> Color(0xFF4285F4)
        ProviderType.OPENAI_COMPATIBLE -> colors.accent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Type indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(typeColor),
            )
            Text(
                text = config.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = typeColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(typeColor.copy(alpha = 0.1f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "模型：${config.modelName}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = config.baseUrl,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Feature badges
            if (config.supportsVision) {
                FeatureBadge("视觉", colors.accent)
            }
            if (config.supportsToolCalling) {
                FeatureBadge("工具调用", colors.accent.copy(alpha = 0.7f))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            }
            IconButton(onClick = onTest, enabled = !isTesting, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = "测试连接",
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FeatureBadge(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderEditorScreen(
    isEditing: Boolean,
    name: String,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    isTesting: Boolean,
    inlineError: String?,
    inlineDebugDetail: String?,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val colors = AgentThemeColors
    // Find matching preset for model suggestions
    val matchedPreset = remember(baseUrl) { ProviderDefaults.guessPreset(baseUrl) }
    var showModelSuggestions by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.textPrimary,
                    )
                }
                Text(
                    text = if (isEditing) "编辑配置" else "新增配置",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onConfirm) {
                    Text("保存", color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Provider type display
            val typeLabel = when (providerType) {
                ProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容协议"
                ProviderType.ANTHROPIC -> "Anthropic (Claude) 原生协议"
                ProviderType.GOOGLE_GEMINI -> "Google Gemini 原生协议"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "协议：$typeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

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
                supportingText = if (providerType == ProviderType.GOOGLE_GEMINI) {
                    { Text("例：https://generativelanguage.googleapis.com/v1beta/", style = MaterialTheme.typography.bodySmall) }
                } else null,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API Key") },
                isError = inlineError != null,
                visualTransformation = PasswordVisualTransformation(),
            )

            // Model name with suggestions
            ExposedDropdownMenuBox(
                expanded = showModelSuggestions,
                onExpandedChange = { showModelSuggestions = it },
            ) {
                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true,
                    label = { Text("Model Name") },
                    trailingIcon = {
                        if (!matchedPreset?.modelSuggestions.isNullOrEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showModelSuggestions)
                        }
                    },
                )
                if (!matchedPreset?.modelSuggestions.isNullOrEmpty()) {
                    ExposedDropdownMenu(
                        expanded = showModelSuggestions,
                        onDismissRequest = { showModelSuggestions = false },
                    ) {
                        matchedPreset!!.modelSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion, style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    onModelNameChange(suggestion)
                                    showModelSuggestions = false
                                },
                            )
                        }
                    }
                }
            }

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
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onTest,
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text("测试连接")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) {
                Text("保存配置")
            }
        }
    }
}
