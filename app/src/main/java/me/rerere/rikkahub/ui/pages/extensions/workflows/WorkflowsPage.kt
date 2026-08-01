package me.rerere.rikkahub.ui.pages.extensions.workflows

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Clipboard
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.WorkflowSquare01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.workflow.WORKFLOW_FILE_SUFFIX
import me.rerere.rikkahub.data.workflow.WorkflowDefinition
import me.rerere.rikkahub.data.workflow.WorkflowMatchType
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun WorkflowsPage() {
    val navController = LocalNavController.current
    val vm = koinViewModel<WorkflowsVM>()
    val workflows by vm.workflows.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var showImportSheet by remember { mutableStateOf(false) }
    var showPasteImport by remember { mutableStateOf(false) }
    var showFileConflict by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkflowDefinition?>(null) }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        showFileConflict = uri to (uri.lastPathSegment ?: "工作流")
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("工作流") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showImportSheet = true }) {
                        Icon(HugeIcons.FileImport, contentDescription = "导入")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate(Screen.WorkflowDetail("new"))
            }) {
                Icon(HugeIcons.Add01, contentDescription = "新建")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (workflows.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.WorkflowSquare01,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "还没有工作流",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "可以新建，或粘贴 JSON / 从文件导入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(workflows, key = { it.id.toString() }) { workflow ->
                WorkflowCard(
                    workflow = workflow,
                    onClick = { navController.navigate(Screen.WorkflowDetail(workflow.id.toString())) },
                    onDelete = { deleteTarget = workflow },
                    onExport = {
                        val json = vm.exportJson(workflow.id) ?: return@WorkflowCard
                        val dir = File(context.cacheDir, "share").apply { mkdirs() }
                        val safeName = workflow.name.ifBlank { workflow.id.toString() }
                            .replace(Regex("""[\\/:*?"<>|]"""), "_")
                        val file = File(dir, "$safeName$WORKFLOW_FILE_SUFFIX")
                        file.writeText(json)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, json)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "导出工作流"))
                    },
                )
            }
        }
    }

    if (showImportSheet) {
        WorkflowImportSheet(
            onDismiss = { showImportSheet = false },
            onPasteJson = {
                showImportSheet = false
                showPasteImport = true
            },
            onImportFromFile = {
                showImportSheet = false
                fileImportLauncher.launch(
                    arrayOf("application/json", "text/*", "application/octet-stream", "*/*")
                )
            },
        )
    }

    if (showPasteImport) {
        PasteWorkflowJsonDialog(
            onDismiss = { showPasteImport = false },
            onImport = { json, overwrite, forceNewId ->
                vm.importFromJsonText(json, overwrite, forceNewId) { ok, msg ->
                    if (ok) {
                        showPasteImport = false
                        toaster.show("已导入：$msg")
                    } else {
                        toaster.show("导入失败：$msg")
                    }
                }
            },
        )
    }

    showFileConflict?.let { (uri, label) ->
        AlertDialog(
            onDismissRequest = { showFileConflict = null },
            title = { Text("导入工作流") },
            text = {
                Text("即将导入「$label」。若 ID 已存在，请选择处理方式。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFileConflict = null
                    vm.importFromFile(context, uri, overwriteSameId = true, forceNewId = false) { ok, msg ->
                        toaster.show(if (ok) "已导入：$msg" else "导入失败：$msg")
                    }
                }) { Text("覆盖同 ID") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showFileConflict = null
                        vm.importFromFile(context, uri, overwriteSameId = false, forceNewId = true) { ok, msg ->
                            toaster.show(if (ok) "已导入为新 ID：$msg" else "导入失败：$msg")
                        }
                    }) { Text("生成新 ID") }
                    TextButton(onClick = { showFileConflict = null }) { Text("取消") }
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = "删除工作流",
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = {
            deleteTarget?.let { vm.delete(it.id) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text("确定删除「${deleteTarget?.name.orEmpty()}」？已绑定它的助手会自动去掉该开关。")
    }
}

@Composable
private fun WorkflowImportSheet(
    onDismiss: () -> Unit,
    onPasteJson: () -> Unit,
    onImportFromFile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "导入工作流",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("粘贴 JSON 文本") },
                supportingContent = { Text("从 QQ / 聊天直接复制粘贴，推荐手机使用") },
                leadingContent = { Icon(HugeIcons.Clipboard, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPasteJson),
            )
            ListItem(
                headlineContent = { Text("从文件导入") },
                supportingContent = { Text("选择 .workflow.json 或 .json 文件") },
                leadingContent = { Icon(HugeIcons.FileImport, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onImportFromFile),
            )
        }
    }
}

@Composable
private fun PasteWorkflowJsonDialog(
    onDismiss: () -> Unit,
    onImport: (json: String, overwriteSameId: Boolean, forceNewId: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴 JSON 导入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "把完整工作流 JSON 粘贴到下方，然后选择导入方式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    placeholder = { Text("{\n  \"schemaVersion\": 1,\n  ...\n}") },
                    minLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isBlank()) return@TextButton
                    onImport(text, true, false)
                },
                enabled = text.isNotBlank(),
            ) { Text("导入（覆盖同 ID）") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        if (text.isBlank()) return@TextButton
                        onImport(text, false, true)
                    },
                    enabled = text.isNotBlank(),
                ) { Text("导入为新 ID") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun WorkflowCard(
    workflow: WorkflowDefinition,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CustomColors.cardColorsOnSurfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(workflow.name.ifBlank { "未命名" }, style = MaterialTheme.typography.titleMedium)
                if (workflow.description.isNotBlank()) {
                    Text(
                        workflow.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    "${workflow.steps.size} 个步骤 · ${workflow.trigger.match.toZhLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(HugeIcons.MoreVertical, contentDescription = null)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("导出") },
                    onClick = {
                        menuExpanded = false
                        onExport()
                    },
                    leadingIcon = { Icon(HugeIcons.FileExport, null) },
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(HugeIcons.Delete01, null) },
                )
            }
        }
    }
}

internal fun WorkflowMatchType.toZhLabel(): String = when (this) {
    WorkflowMatchType.Always -> "始终触发"
    WorkflowMatchType.Keyword -> "关键词"
    WorkflowMatchType.Regex -> "正则"
}
