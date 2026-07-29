package com.agent.chat.ui.persona

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
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
            val json = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }.orEmpty()
            viewModel.importPersonas(json)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("人设", style = MaterialTheme.typography.titleLarge)
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
                        importLauncher.launch(arrayOf("application/json", "text/*"))
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
                Text(
                    text = "还没有人设，点右下角创建",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.personas, key = { it.id }) { persona ->
                    PersonaListItem(
                        persona = persona,
                        onClick = { viewModel.openEditEditor(persona) },
                        onManageMemory = { viewModel.openMemoryManager(persona) },
                        onDelete = { viewModel.deletePersona(persona.id) },
                    )
                }
            }
        }
    }

    if (uiState.showCreateChooser) {
        CreatePersonaChooserDialog(
            onDismiss = viewModel::dismissCreateChooser,
            onManual = viewModel::openCreateEditor,
            onSmartImport = viewModel::openSmartImport,
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

    if (uiState.isEditorOpen) {
        PersonaEditorDialog(
            isEditing = uiState.editingPersona != null,
            name = uiState.editorName,
            avatar = uiState.editorAvatar,
            systemPrompt = uiState.editorSystemPrompt,
            temperature = uiState.editorTemperature,
            description = uiState.editorDescription,
            openingLine = uiState.editorOpeningLine,
            onNameChange = viewModel::onEditorNameChange,
            onAvatarChange = viewModel::onEditorAvatarChange,
            onSystemPromptChange = viewModel::onEditorSystemPromptChange,
            onTemperatureChange = viewModel::onEditorTemperatureChange,
            onDescriptionChange = viewModel::onEditorDescriptionChange,
            onOpeningLineChange = viewModel::onEditorOpeningLineChange,
            onDismiss = viewModel::closeEditor,
            onConfirm = viewModel::saveEditor,
            onManageMemory = uiState.editingPersona?.let { persona ->
                {
                    viewModel.closeEditor()
                    viewModel.openMemoryManager(persona)
                }
            },
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建人设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "选择创建方式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onManual,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("手动创建")
                }
                TextButton(
                    onClick = onSmartImport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("智能导入")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
                    text = "粘贴角色设定后，AI 会整理成可编辑的人设字段，确认后再保存。",
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
                        Text(
                            "粘贴任何人设描述文字，比如角色的性格、说话方式、背景故事，AI会自动帮你整理",
                        )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonaAvatar(
            name = persona.name,
            avatar = persona.avatar,
            size = 44.dp,
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
}

@Composable
private fun PersonaEditorDialog(
    isEditing: Boolean,
    name: String,
    avatar: String,
    systemPrompt: String,
    temperature: String,
    description: String,
    openingLine: String,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpeningLineChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onManageMemory: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑人设" else "确认人设") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名称") },
                )
                OutlinedTextField(
                    value = avatar,
                    onValueChange = onAvatarChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("头像（emoji 或短字符）") },
                    supportingText = {
                        Text("文本导入默认用占位头像，可自行修改")
                    },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述") },
                    maxLines = 2,
                )
                OutlinedTextField(
                    value = openingLine,
                    onValueChange = onOpeningLineChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("开场白") },
                    maxLines = 2,
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = onTemperatureChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Temperature (0~2)") },
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = onSystemPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("System Prompt") },
                    minLines = 4,
                    maxLines = 8,
                )
                if (onManageMemory != null) {
                    TextButton(onClick = onManageMemory) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("记忆管理")
                    }
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
