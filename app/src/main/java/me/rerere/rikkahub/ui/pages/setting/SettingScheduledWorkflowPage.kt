package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowRule
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowTargetAssistant
import me.rerere.rikkahub.data.workflow.ScheduledWorkflowTimeMode
import me.rerere.rikkahub.data.workflow.WorkflowDefinition
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SettingScheduledWorkflowPage(vm: ScheduledWorkflowVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val workflows by vm.workflows.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editingRule by remember { mutableStateOf<ScheduledWorkflowRule?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("定时工作流") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            val defaultWorkflow = workflows.firstOrNull() ?: return@IconButton
                            editingRule = ScheduledWorkflowRule(
                                name = "新定时规则",
                                workflowId = defaultWorkflow.id,
                            )
                        }
                    ) {
                        Icon(HugeIcons.Add01, contentDescription = "新增规则")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                    item(
                        headlineContent = { Text("统一由后台调度器轮询执行") },
                        supportingContent = {
                            Text("到点后会在配置该工作流的助手里按优先级串行触发，避免和普通消息触发链路混在一起。")
                        },
                    )
                }
            }

            item {
                val rules = settings.scheduledWorkflows.sortedBy { it.name.lowercase() }
                if (rules.isEmpty()) {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        item(
                            headlineContent = { Text("还没有定时规则") },
                            supportingContent = {
                                Text("点击右上角 + 创建。请先在助手扩展里启用对应工作流，再回来配置触发时间和助手优先级。")
                            },
                        )
                    }
                } else {
                    CardGroup(modifier = Modifier.padding(horizontal = 8.dp)) {
                        rules.forEach { rule ->
                            val workflow = workflows.firstOrNull { it.id == rule.workflowId }
                            val assistantNames = rule.activeAssistantIds().mapNotNull { id ->
                                settings.assistants.firstOrNull { it.id == id }?.name?.ifBlank { null }
                                    ?: settings.assistants.firstOrNull { it.id == id }?.id?.toString()
                            }
                            item(
                                headlineContent = { Text(rule.name.ifBlank { "未命名规则" }) },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(workflow?.name ?: "工作流缺失")
                                            append(" · ")
                                            append(rule.timeMode.toLabel())
                                            append(" ")
                                            append(rule.timeText())
                                            append(" · ")
                                            append(if (rule.enabled) "已启用" else "已停用")
                                            if (assistantNames.isNotEmpty()) {
                                                append("\n助手顺序：")
                                                append(assistantNames.joinToString(" -> "))
                                            }
                                        }
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { editingRule = rule }) {
                                            Icon(HugeIcons.Tick02, contentDescription = "编辑")
                                        }
                                        IconButton(onClick = { vm.deleteRule(rule.id) }) {
                                            Icon(HugeIcons.Delete01, contentDescription = "删除")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editingRule?.let { initial ->
        ScheduledWorkflowEditorDialog(
            initial = initial,
            workflows = workflows,
            assistants = settings.assistants,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                vm.saveRule(updated) { ok, msg ->
                    if (ok) {
                        toaster.show("已保存：$msg")
                        editingRule = null
                    } else {
                        toaster.show(msg)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledWorkflowEditorDialog(
    initial: ScheduledWorkflowRule,
    workflows: List<WorkflowDefinition>,
    assistants: List<Assistant>,
    onDismiss: () -> Unit,
    onSave: (ScheduledWorkflowRule) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var workflowId by remember(initial) { mutableStateOf(initial.workflowId) }
    var timeMode by remember(initial) { mutableStateOf(initial.timeMode) }
    var hourText by remember(initial) { mutableStateOf(initial.hour.toString()) }
    var minuteText by remember(initial) { mutableStateOf(initial.minute.toString()) }
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var workflowExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }

    val eligibleAssistants = remember(workflowId, assistants) {
        assistants.filter { workflowId in it.enabledWorkflowIds }
    }
    var selectedTargets by remember(initial, eligibleAssistants) {
        mutableStateOf(
            initial.targets
                .filter { target -> eligibleAssistants.any { it.id == target.assistantId } }
                .ifEmpty { eligibleAssistants.map { ScheduledWorkflowTargetAssistant(it.id, true) } }
        )
    }
    var priority by remember(initial, eligibleAssistants) {
        mutableStateOf(
            initial.assistantPriority
                .filter { id -> eligibleAssistants.any { it.id == id } }
                .ifEmpty { selectedTargets.map { it.assistantId } }
        )
    }

    fun toggleAssistant(assistantId: Uuid, checked: Boolean) {
        selectedTargets = if (checked) {
            if (selectedTargets.any { it.assistantId == assistantId }) selectedTargets
            else selectedTargets + ScheduledWorkflowTargetAssistant(assistantId, enabled = true)
        } else {
            selectedTargets.filterNot { it.assistantId == assistantId }
        }
        priority = priority.filter { id -> selectedTargets.any { it.assistantId == id } || id == assistantId }
        if (checked && assistantId !in priority) {
            priority = priority + assistantId
        }
        if (!checked) {
            priority = priority.filter { it != assistantId }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val workflow = workflows.firstOrNull { it.id == workflowId } ?: return@TextButton
                    val validAssistantIds = assistants.filter { workflow.id in it.enabledWorkflowIds }.map { it.id }.toSet()
                    val targets = selectedTargets.filter { it.assistantId in validAssistantIds }
                    val rule = initial.copy(
                        name = name.trim(),
                        description = description.trim(),
                        workflowId = workflow.id,
                        targets = targets,
                        assistantPriority = priority.filter { it in validAssistantIds },
                        timeMode = timeMode,
                        hour = hourText.toIntOrNull() ?: initial.hour,
                        minute = minuteText.toIntOrNull() ?: initial.minute,
                        enabled = enabled,
                    )
                    onSave(rule)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("编辑定时规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                ExposedDropdownMenuBox(
                    expanded = workflowExpanded,
                    onExpandedChange = { workflowExpanded = it },
                ) {
                    val selectedWorkflow = workflows.firstOrNull { it.id == workflowId }
                    OutlinedTextField(
                        value = selectedWorkflow?.name ?: "请选择工作流",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("工作流") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = workflowExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = workflowExpanded,
                        onDismissRequest = { workflowExpanded = false },
                    ) {
                        workflows.forEach { workflow ->
                            DropdownMenuItem(
                                text = { Text(workflow.name.ifBlank { workflow.id.toString() }) },
                                onClick = {
                                    workflowId = workflow.id
                                    workflowExpanded = false
                                    val nextEligible = assistants.filter { workflow.id in it.enabledWorkflowIds }
                                    selectedTargets = nextEligible.map {
                                        ScheduledWorkflowTargetAssistant(it.id, enabled = true)
                                    }
                                    priority = nextEligible.map { it.id }
                                }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = timeMode.toLabel(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("时间模式") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                    ) {
                        ScheduledWorkflowTimeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.toLabel()) },
                                onClick = {
                                    timeMode = mode
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        label = { Text("分钟") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("启用规则")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("目标助手（仅显示已启用该工作流的助手）")
                if (eligibleAssistants.isEmpty()) {
                    Text("还没有任何助手启用这个工作流。请先到助手扩展里绑定后再设置。")
                } else {
                    eligibleAssistants.forEach { assistant ->
                        val checked = selectedTargets.any { it.assistantId == assistant.id }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { toggleAssistant(assistant.id, it) },
                                )
                                Text(assistant.name.ifBlank { assistant.id.toString() })
                            }
                            Row {
                                IconButton(
                                    onClick = { priority = priority.moveAssistant(assistant.id, up = true) },
                                    enabled = checked,
                                ) {
                                    Icon(HugeIcons.ArrowUp01, contentDescription = "上移")
                                }
                                IconButton(
                                    onClick = { priority = priority.moveAssistant(assistant.id, up = false) },
                                    enabled = checked,
                                ) {
                                    Icon(HugeIcons.ArrowDown01, contentDescription = "下移")
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

private fun ScheduledWorkflowTimeMode.toLabel(): String = when (this) {
    ScheduledWorkflowTimeMode.DAILY_AT_TIME -> "每天"
    ScheduledWorkflowTimeMode.WEEKDAYS_AT_TIME -> "工作日"
}

private fun ScheduledWorkflowRule.timeText(): String = "%02d:%02d".format(hour, minute)

private fun List<Uuid>.moveAssistant(target: Uuid, up: Boolean): List<Uuid> {
    val index = indexOf(target)
    if (index < 0) return this
    val swap = if (up) index - 1 else index + 1
    if (swap !in indices) return this
    val list = toMutableList()
    val tmp = list[index]
    list[index] = list[swap]
    list[swap] = tmp
    return list
}
