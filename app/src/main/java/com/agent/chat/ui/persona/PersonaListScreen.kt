package com.agent.chat.ui.persona

import com.agent.chat.ui.theme.AgentThemeColors
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.domain.model.Persona
import com.agent.chat.ui.components.PersonaAvatar
import com.agent.chat.ui.memory.MemoryManageDialog
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaListScreen(
    onBackClick: () -> Unit,
    viewModel: PersonaListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val json = pendingExportJson ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charset.forName("UTF-8")))
            }
        }
        pendingExportJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val mime = context.contentResolver.getType(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: ByteArray(0)
            if (mime?.contains("png", ignoreCase = true) == true ||
                uri.toString().endsWith(".png", ignoreCase = true)
            ) {
                viewModel.importCharacterCard(bytes, mime)
            } else {
                val json = bytes.toString(Charset.forName("UTF-8"))
                viewModel.importPersonas(json)
            }
        }.onFailure { error ->
            viewModel.reportMessage("导入失败：${error.message ?: "无法读取文件"}")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportJson.collect { json ->
            pendingExportJson = json
            exportFileLauncher.launch("personas-export.json")
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeStatusMessage()
    }

    if (uiState.isEditorOpen) {
        BackHandler(onBack = viewModel::closeEditor)
        PersonaEditorScreen(
            isEditing = uiState.editingPersona != null,
            tab = uiState.editorTab,
            name = uiState.editorName,
            avatar = uiState.editorAvatar,
            systemPrompt = uiState.editorSystemPrompt,
            temperature = uiState.editorTemperature,
            description = uiState.editorDescription,
            openingLine = uiState.editorOpeningLine,
            presets = uiState.editorPresets,
            lorebook = uiState.editorLorebook,
            regexes = uiState.editorRegexes,
            onTabChange = viewModel::onEditorTabChange,
            onNameChange = viewModel::onEditorNameChange,
            onAvatarChange = viewModel::onEditorAvatarChange,
            onSystemPromptChange = viewModel::onEditorSystemPromptChange,
            onTemperatureChange = viewModel::onEditorTemperatureChange,
            onDescriptionChange = viewModel::onEditorDescriptionChange,
            onOpeningLineChange = viewModel::onEditorOpeningLineChange,
            onPresetsChange = viewModel::onEditorPresetsChange,
            onLorebookChange = viewModel::onEditorLorebookChange,
            onRegexesChange = viewModel::onEditorRegexesChange,
            onDismiss = viewModel::closeEditor,
            onConfirm = viewModel::saveEditor,
            onManageMemory = uiState.editingPersona?.let { persona ->
                {
                    viewModel.closeEditor()
                    viewModel.openMemoryManager(persona)
                }
            },
        )
        uiState.memoryPersona?.let { persona ->
            MemoryManageDialog(
                memories = uiState.memories,
                personaName = persona.name,
                onDismiss = viewModel::dismissMemoryManager,
                onDelete = viewModel::deleteMemory,
                extractThreshold = uiState.extractThreshold,
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("人设", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "自由文本 + 预设 / 世界书 / 正则",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "text/*",
                                "image/png",
                                "image/*",
                            ),
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "导入",
                        )
                    }
                    IconButton(onClick = viewModel::exportPersonas) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "导出",
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
                onClick = viewModel::openCreateChooser,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建人设")
            }
        },
    ) { innerPadding ->
        if (uiState.personas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "还没有人设",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "点右下角创建，或导入 SillyTavern 角色卡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.personas, key = { it.id }) { persona ->
                    PersonaListItem(
                        persona = persona,
                        onClick = { viewModel.openEditEditor(persona) },
                        onManageMemory = { viewModel.openMemoryManager(persona) },
                        onDelete = { viewModel.deletePersona(persona.id) },
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (uiState.showCreateChooser) {
        CreatePersonaChooserDialog(
            onDismiss = viewModel::dismissCreateChooser,
            onManual = viewModel::openCreateEditor,
            onSmartImport = viewModel::openSmartImport,
            onImportCard = {
                viewModel.dismissCreateChooser()
                importLauncher.launch(
                    arrayOf("application/json", "text/*", "image/png", "image/*"),
                )
            },
        )
    }

    if (uiState.showSmartImport) {
        SmartImportDialog(
            text = uiState.smartImportText,
            isParsing = uiState.isSmartImportParsing,
            onTextChange = viewModel::onSmartImportTextChange,
            onParse = viewModel::parseSmartImport,
            onDismiss = viewModel::dismissSmartImport,
        )
    }

    uiState.memoryPersona?.let { persona ->
        MemoryManageDialog(
            memories = uiState.memories,
            personaName = persona.name,
            onDismiss = viewModel::dismissMemoryManager,
            onDelete = viewModel::deleteMemory,
            extractThreshold = uiState.extractThreshold,
        )
    }
}

@Composable
private fun CreatePersonaChooserDialog(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onSmartImport: () -> Unit,
    onImportCard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建人设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "选择创建方式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChooserAction(
                    icon = Icons.Default.Edit,
                    title = "手动创建",
                    subtitle = "自己写 Prompt / 预设 / 世界书",
                    onClick = onManual,
                )
                ChooserAction(
                    icon = Icons.Default.AutoAwesome,
                    title = "智能导入",
                    subtitle = "粘贴设定文字，AI 帮你整理",
                    onClick = onSmartImport,
                )
                ChooserAction(
                    icon = Icons.Default.ContactPage,
                    title = "导入角色卡",
                    subtitle = "SillyTavern JSON / PNG",
                    onClick = onImportCard,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ChooserAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmartImportDialog(
    text: String,
    isParsing: Boolean,
    onTextChange: (String) -> Unit,
    onParse: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isParsing) onDismiss() },
        title = { Text("智能导入") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "粘贴角色设定或 SillyTavern JSON；普通文字会由 AI 整理成可编辑字段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    enabled = !isParsing,
                    placeholder = {
                        Text("用一句话描述想要的角色，例如：温柔、成熟、有幽默感的姐姐型 AI…")
                    },
                )
                if (isParsing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("解析中…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onParse,
                enabled = !isParsing && text.isNotBlank(),
            ) {
                Text(if (isParsing) "解析中" else "解析")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isParsing,
            ) { Text("取消") }
        },
    )
}

@Composable
private fun PersonaListItem(
    persona: Persona,
    onClick: () -> Unit,
    onManageMemory: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AgentThemeColors

    val badgeParts = buildList {
        if (persona.presetMessages.isNotEmpty()) add("预设 ${persona.presetMessages.size}")
        if (persona.lorebookEntries.isNotEmpty()) add("世界书 ${persona.lorebookEntries.size}")
        if (persona.outputRegexes.isNotEmpty()) add("正则 ${persona.outputRegexes.size}")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonaAvatar(
                name = persona.name,
                avatar = persona.avatar,
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = persona.description.ifBlank { persona.systemPrompt },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onManageMemory) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "记忆管理",
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
        if (badgeParts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                badgeParts.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
