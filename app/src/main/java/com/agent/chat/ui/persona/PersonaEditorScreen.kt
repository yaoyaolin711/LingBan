package com.agent.chat.ui.persona

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agent.chat.domain.model.LorebookEntry
import com.agent.chat.domain.model.OutputRegex
import com.agent.chat.domain.model.PresetMessage
import com.agent.chat.ui.components.PersonaAvatar
import com.agent.chat.ui.theme.OutlineSubtle
import com.agent.chat.ui.theme.SurfaceCard
import com.agent.chat.ui.theme.SurfaceMuted

enum class PersonaEditorTab(val label: String) {
    BASIC("基础"),
    PROMPT("Prompt"),
    PRESET("预设对话"),
    LOREBOOK("世界书"),
    REGEX("正则"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditorScreen(
    isEditing: Boolean,
    tab: PersonaEditorTab,
    name: String,
    avatar: String,
    systemPrompt: String,
    temperature: String,
    description: String,
    openingLine: String,
    presets: List<PresetMessage>,
    lorebook: List<LorebookEntry>,
    regexes: List<OutputRegex>,
    onTabChange: (PersonaEditorTab) -> Unit,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpeningLineChange: (String) -> Unit,
    onPresetsChange: (List<PresetMessage>) -> Unit,
    onLorebookChange: (List<LorebookEntry>) -> Unit,
    onRegexesChange: (List<OutputRegex>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onManageMemory: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "编辑人设" else "新建人设",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (onManageMemory != null) {
                        IconButton(onClick = onManageMemory) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "记忆管理",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    TextButton(onClick = onConfirm) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            EditorTabRow(tab = tab, onTabChange = onTabChange)
            when (tab) {
                PersonaEditorTab.BASIC -> BasicTab(
                    name = name,
                    avatar = avatar,
                    description = description,
                    openingLine = openingLine,
                    temperature = temperature,
                    onNameChange = onNameChange,
                    onAvatarChange = onAvatarChange,
                    onDescriptionChange = onDescriptionChange,
                    onOpeningLineChange = onOpeningLineChange,
                    onTemperatureChange = onTemperatureChange,
                )
                PersonaEditorTab.PROMPT -> PromptTab(
                    systemPrompt = systemPrompt,
                    onSystemPromptChange = onSystemPromptChange,
                )
                PersonaEditorTab.PRESET -> PresetTab(
                    presets = presets,
                    onPresetsChange = onPresetsChange,
                )
                PersonaEditorTab.LOREBOOK -> LorebookTab(
                    entries = lorebook,
                    onEntriesChange = onLorebookChange,
                )
                PersonaEditorTab.REGEX -> RegexTab(
                    regexes = regexes,
                    onRegexesChange = onRegexesChange,
                )
            }
        }
    }
}

@Composable
private fun EditorTabRow(
    tab: PersonaEditorTab,
    onTabChange: (PersonaEditorTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PersonaEditorTab.entries.forEach { item ->
            val selected = item == tab
            FilterChip(
                selected = selected,
                onClick = { onTabChange(item) },
                label = { Text(item.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun BasicTab(
    name: String,
    avatar: String,
    description: String,
    openingLine: String,
    temperature: String,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOpeningLineChange: (String) -> Unit,
    onTemperatureChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditorCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaAvatar(name = name.ifBlank { "?" }, avatar = avatar, size = 56.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("头像预览", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "用 emoji 或短字符即可",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            EditorField(value = avatar, onValueChange = onAvatarChange, label = "头像", singleLine = true)
            EditorField(value = name, onValueChange = onNameChange, label = "名称", singleLine = true)
            EditorField(value = description, onValueChange = onDescriptionChange, label = "简介", maxLines = 3)
            EditorField(value = openingLine, onValueChange = onOpeningLineChange, label = "开场白", maxLines = 3)
            EditorField(
                value = temperature,
                onValueChange = onTemperatureChange,
                label = "Temperature (0~2)",
                singleLine = true,
                supporting = "越高越发散，角色扮演可试 0.8～1.1",
            )
        }
    }
}

@Composable
private fun PromptTab(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditorCard {
            Text("System Prompt", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "性格、口吻、关系、禁忌都写在这里。可用 {{char}} {{user}} {{nickname}} {{cur_time}}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                colors = editorFieldColors(),
                shape = RoundedCornerShape(14.dp),
            )
        }
        HintRow(
            "提示：先写清「你是谁 / 怎么说话 / 和用户什么关系」，再补细节。世界书留给按话题触发的设定。",
        )
    }
}

@Composable
private fun PresetTab(
    presets: List<PresetMessage>,
    onPresetsChange: (List<PresetMessage>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HintRow("预置几轮示范对话，模型会照这个语气开场。建议 2～6 条，交替用户/助手。")
        presets.forEachIndexed { index, item ->
            EditorCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RoleToggle(
                        isAssistant = item.role.equals(PresetMessage.ROLE_ASSISTANT, true),
                        onToggle = { assistant ->
                            val role = if (assistant) {
                                PresetMessage.ROLE_ASSISTANT
                            } else {
                                PresetMessage.ROLE_USER
                            }
                            onPresetsChange(
                                presets.toMutableList().also {
                                    it[index] = item.copy(role = role)
                                },
                            )
                        },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            onPresetsChange(presets.toMutableList().also { it.removeAt(index) })
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = item.content,
                    onValueChange = { value ->
                        onPresetsChange(
                            presets.toMutableList().also {
                                it[index] = item.copy(content = value)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text("示范内容") },
                    colors = editorFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        AddRowButton(text = "添加一条示范") {
            val nextRole = if (presets.lastOrNull()?.role.equals(PresetMessage.ROLE_USER, true)) {
                PresetMessage.ROLE_ASSISTANT
            } else {
                PresetMessage.ROLE_USER
            }
            onPresetsChange(presets + PresetMessage(role = nextRole, content = ""))
        }
    }
}

@Composable
private fun LorebookTab(
    entries: List<LorebookEntry>,
    onEntriesChange: (List<LorebookEntry>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HintRow("聊到关键词时才注入相关设定，避免 system 里塞满世界背景。关键词用逗号分隔。")
        entries.forEachIndexed { index, entry ->
            EditorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("条目 ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("启用", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { enabled ->
                            onEntriesChange(
                                entries.toMutableList().also {
                                    it[index] = entry.copy(enabled = enabled)
                                },
                            )
                        },
                    )
                    IconButton(
                        onClick = {
                            onEntriesChange(entries.toMutableList().also { it.removeAt(index) })
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
                EditorField(
                    value = entry.keys.joinToString(", "),
                    onValueChange = { value ->
                        val keys = value.split(',', '，', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onEntriesChange(
                            entries.toMutableList().also {
                                it[index] = entry.copy(keys = keys)
                            },
                        )
                    },
                    label = "关键词",
                    singleLine = true,
                    supporting = "例如：家乡, 妹妹, 那家咖啡店",
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = entry.content,
                    onValueChange = { value ->
                        onEntriesChange(
                            entries.toMutableList().also {
                                it[index] = entry.copy(content = value)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("注入内容") },
                    minLines = 3,
                    maxLines = 6,
                    colors = editorFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("区分大小写", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = entry.caseSensitive,
                        onCheckedChange = { cs ->
                            onEntriesChange(
                                entries.toMutableList().also {
                                    it[index] = entry.copy(caseSensitive = cs)
                                },
                            )
                        },
                    )
                }
            }
        }
        AddRowButton(text = "添加世界书条目") {
            onEntriesChange(
                entries + LorebookEntry(
                    id = "lore_${System.currentTimeMillis().toString(36)}",
                    keys = emptyList(),
                    content = "",
                ),
            )
        }
    }
}

@Composable
private fun RegexTab(
    regexes: List<OutputRegex>,
    onRegexesChange: (List<OutputRegex>) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HintRow("对助手最终回复做正则替换，适合压 AI 腔、统一口癖。写法同 Kotlin/Java Regex。")
        regexes.forEachIndexed { index, rule ->
            EditorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("规则 ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("启用", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { enabled ->
                            onRegexesChange(
                                regexes.toMutableList().also {
                                    it[index] = rule.copy(enabled = enabled)
                                },
                            )
                        },
                    )
                    IconButton(
                        onClick = {
                            onRegexesChange(regexes.toMutableList().also { it.removeAt(index) })
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
                EditorField(
                    value = rule.pattern,
                    onValueChange = { value ->
                        onRegexesChange(
                            regexes.toMutableList().also {
                                it[index] = rule.copy(pattern = value)
                            },
                        )
                    },
                    label = "匹配正则",
                    singleLine = true,
                    supporting = "例如：作为一名AI助手，?",
                )
                Spacer(modifier = Modifier.height(8.dp))
                EditorField(
                    value = rule.replacement,
                    onValueChange = { value ->
                        onRegexesChange(
                            regexes.toMutableList().also {
                                it[index] = rule.copy(replacement = value)
                            },
                        )
                    },
                    label = "替换为",
                    singleLine = true,
                    supporting = "可留空表示删除匹配内容",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("仅视觉改写", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后落库仍保留原文",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = rule.visualOnly,
                        onCheckedChange = { visual ->
                            onRegexesChange(
                                regexes.toMutableList().also {
                                    it[index] = rule.copy(visualOnly = visual)
                                },
                            )
                        },
                    )
                }
            }
        }
        AddRowButton(text = "添加正则规则") {
            onRegexesChange(
                regexes + OutputRegex(
                    id = "rx_${System.currentTimeMillis().toString(36)}",
                    pattern = "",
                    replacement = "",
                ),
            )
        }
    }
}

@Composable
private fun RoleToggle(
    isAssistant: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoleChip(selected = !isAssistant, label = "用户") { onToggle(false) }
        RoleChip(selected = isAssistant, label = "助手") { onToggle(true) }
    }
}

@Composable
private fun RoleChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else SurfaceMuted,
        label = "roleChip",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "roleChipFg",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EditorCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .border(1.dp, OutlineSubtle, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun HintRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun AddRowButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, OutlineSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 4,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = maxLines,
        supportingText = supporting?.let { { Text(it) } },
        colors = editorFieldColors(),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceMuted,
    unfocusedContainerColor = SurfaceMuted,
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
    unfocusedBorderColor = OutlineSubtle,
)
