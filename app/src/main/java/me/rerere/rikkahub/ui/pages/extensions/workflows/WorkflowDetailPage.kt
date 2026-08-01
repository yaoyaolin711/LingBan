package me.rerere.rikkahub.ui.pages.extensions.workflows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.workflow.WorkflowConflicts
import me.rerere.rikkahub.data.workflow.WorkflowDefinition
import me.rerere.rikkahub.data.workflow.WorkflowMatchType
import me.rerere.rikkahub.data.workflow.WorkflowPriority
import me.rerere.rikkahub.data.workflow.WorkflowStep
import me.rerere.rikkahub.data.workflow.WorkflowTrigger
import me.rerere.rikkahub.data.workflow.WorkflowTriggerMode
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowDetailPage(workflowId: String) {
    val vm = koinViewModel<WorkflowsVM>()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val initial = remember(workflowId) {
        if (workflowId == "new") {
            WorkflowDefinition(name = "新工作流")
        } else {
            runCatching { Uuid.parse(workflowId) }.getOrNull()?.let { vm.get(it) }
                ?: WorkflowDefinition(name = "新工作流")
        }
    }

    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var author by remember { mutableStateOf(initial.author) }
    var matchType by remember { mutableStateOf(initial.trigger.match) }
    var patternsText by remember { mutableStateOf(initial.trigger.patterns.joinToString("\n")) }
    var defaultPriority by remember { mutableStateOf(initial.conflicts.defaultPriority) }
    var steps by remember {
        mutableStateOf(
            initial.steps.ifEmpty {
                listOf(WorkflowStep.InjectPrompt(content = "请按本工作流的指引处理用户请求。"))
            }
        )
    }
    val id = remember { initial.id }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (workflowId == "new") "新建工作流" else "编辑工作流") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = {
                        val definition = WorkflowDefinition(
                            id = id,
                            name = name.trim(),
                            description = description.trim(),
                            author = author.trim(),
                            trigger = WorkflowTrigger(
                                mode = WorkflowTriggerMode.OnUserMessage,
                                match = matchType,
                                patterns = patternsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                manualRunnable = true,
                            ),
                            steps = steps,
                            conflicts = WorkflowConflicts(defaultPriority = defaultPriority),
                        )
                        vm.save(definition) { ok, msg ->
                            if (ok) {
                                toaster.show("已保存")
                                navController.popBackStack()
                            } else {
                                toaster.show(msg)
                            }
                        }
                    }) {
                        Icon(HugeIcons.Tick02, contentDescription = "保存")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
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
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("作者（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("触发条件", style = MaterialTheme.typography.titleSmall)
            EnumDropdown(
                label = "匹配方式",
                selected = matchType,
                options = WorkflowMatchType.entries,
                labelOf = { it.toZhLabel() },
                onSelected = { matchType = it },
            )
            if (matchType != WorkflowMatchType.Always) {
                OutlinedTextField(
                    value = patternsText,
                    onValueChange = { patternsText = it },
                    label = { Text("匹配内容（每行一条）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }

            EnumDropdown(
                label = "默认冲突优先级",
                selected = defaultPriority,
                options = WorkflowPriority.entries,
                labelOf = { it.toZhLabel() },
                onSelected = { defaultPriority = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("步骤", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = {
                    steps = steps + WorkflowStep.InjectPrompt(content = "")
                }) {
                    Icon(HugeIcons.Add01, contentDescription = null)
                    Text("添加步骤")
                }
            }

            steps.forEachIndexed { index, step ->
                StepEditor(
                    index = index,
                    step = step,
                    onChange = { updated ->
                        steps = steps.toMutableList().also { it[index] = updated }
                    },
                    onDelete = {
                        steps = steps.toMutableList().also { it.removeAt(index) }
                    },
                    onChangeType = { type ->
                        val newStep: WorkflowStep = when (type) {
                            "inject_prompt" -> WorkflowStep.InjectPrompt(
                                id = step.id,
                                content = (step as? WorkflowStep.InjectPrompt)?.content.orEmpty(),
                            )
                            "hint_tools" -> WorkflowStep.HintTools(
                                id = step.id,
                                toolNames = (step as? WorkflowStep.HintTools)?.toolNames
                                    ?: listOf("search_web"),
                            )
                            else -> WorkflowStep.RunSkill(
                                id = step.id,
                                skillName = (step as? WorkflowStep.RunSkill)?.skillName.orEmpty(),
                            )
                        }
                        steps = steps.toMutableList().also { it[index] = newStep }
                    },
                )
            }
        }
    }
}

@Composable
private fun StepEditor(
    index: Int,
    step: WorkflowStep,
    onChange: (WorkflowStep) -> Unit,
    onDelete: () -> Unit,
    onChangeType: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("步骤 ${index + 1}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onDelete) {
                Icon(HugeIcons.Delete01, contentDescription = "删除步骤")
            }
        }
        val type = when (step) {
            is WorkflowStep.InjectPrompt -> "inject_prompt"
            is WorkflowStep.HintTools -> "hint_tools"
            is WorkflowStep.RunSkill -> "run_skill"
        }
        EnumDropdown(
            label = "类型",
            selected = type,
            options = listOf("inject_prompt", "hint_tools", "run_skill"),
            labelOf = {
                when (it) {
                    "inject_prompt" -> "注入提示词"
                    "hint_tools" -> "提示优先工具"
                    "run_skill" -> "运行 Skill"
                    else -> it
                }
            },
            onSelected = onChangeType,
        )
        when (step) {
            is WorkflowStep.InjectPrompt -> {
                EnumDropdown(
                    label = "注入位置",
                    selected = step.position,
                    options = InjectionPosition.entries,
                    labelOf = { it.toZhLabel() },
                    onSelected = { onChange(step.copy(position = it)) },
                )
                OutlinedTextField(
                    value = step.content,
                    onValueChange = { onChange(step.copy(content = it)) },
                    label = { Text("提示词内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
            is WorkflowStep.HintTools -> {
                OutlinedTextField(
                    value = step.toolNames.joinToString(", "),
                    onValueChange = {
                        onChange(
                            step.copy(
                                toolNames = it.split(',', '，', '\n')
                                    .map { name -> name.trim() }
                                    .filter { name -> name.isNotEmpty() },
                            )
                        )
                    },
                    label = { Text("工具名（逗号分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is WorkflowStep.RunSkill -> {
                OutlinedTextField(
                    value = step.skillName,
                    onValueChange = { onChange(step.copy(skillName = it)) },
                    label = { Text("Skill 名称（助手需已启用）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun WorkflowPriority.toZhLabel(): String = when (this) {
    WorkflowPriority.BuiltinFirst -> "内置优先"
    WorkflowPriority.WorkflowFirst -> "工作流优先"
    WorkflowPriority.Coexist -> "共存"
}

private fun InjectionPosition.toZhLabel(): String = when (this) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT -> "系统提示词之前"
    InjectionPosition.AFTER_SYSTEM_PROMPT -> "系统提示词之后"
    InjectionPosition.TOP_OF_CHAT -> "对话最开头"
    InjectionPosition.BOTTOM_OF_CHAT -> "最新消息之前"
    InjectionPosition.AT_DEPTH -> "指定深度"
}
