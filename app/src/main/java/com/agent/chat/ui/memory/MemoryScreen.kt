package com.agent.chat.ui.memory

import com.agent.chat.ui.theme.AgentThemeColors
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.MemoryCategory

@Composable
fun MemoryScreen(
    onBackClick: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    MemoryScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onSelectCategory = viewModel::selectCategory,
        onToggleExpand = viewModel::toggleExpand,
        onEdit = viewModel::openEdit,
        onDelete = viewModel::deleteMemory,
        onToggleBlocked = viewModel::toggleBlocked,
        onEditPortrait = viewModel::openPortraitEditor,
        onDismissPortrait = viewModel::dismissPortraitEditor,
        onSavePortrait = viewModel::savePortrait,
        onDismissEdit = viewModel::dismissEdit,
        onSaveEdit = viewModel::saveEdit,
    )
}

@Composable
fun MemoryScreenContent(
    uiState: MemoryUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onSelectCategory: (MemoryCategory) -> Unit,
    onToggleExpand: (String) -> Unit,
    onEdit: (Memory) -> Unit,
    onDelete: (String) -> Unit,
    onToggleBlocked: (Memory) -> Unit,
    onEditPortrait: () -> Unit,
    onDismissPortrait: () -> Unit,
    onSavePortrait: (String, String, String, String) -> Unit,
    onDismissEdit: () -> Unit,
    onSaveEdit: (String, MemoryCategory) -> Unit,
) {
    val colors = AgentThemeColors

    val categoryAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.entranceReady) {
        if (uiState.entranceReady) {
            categoryAlpha.animateTo(1f, tween(420, delayMillis = 120, easing = FastOutSlowInEasing))
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
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
                        text = "我的记忆",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "灵伴真正了解你的地方",
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "portrait") {
                UserPortraitCard(
                    portrait = uiState.portrait,
                    visible = uiState.entranceReady,
                    onEditClick = onEditPortrait,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            item(key = "categories") {
                Text(
                    text = "记忆分类",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .graphicsLayer { alpha = categoryAlpha.value }
                        .padding(bottom = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .graphicsLayer { alpha = categoryAlpha.value },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MemoryCategory.entries.forEach { category ->
                        CategoryChip(
                            label = category.displayName,
                            count = uiState.countOf(category),
                            selected = uiState.selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            val filtered = uiState.filtered
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    EmptyMemoryState(category = uiState.selectedCategory)
                }
            } else {
                itemsIndexed(
                    items = filtered,
                    key = { _, memory -> memory.id },
                ) { index, memory ->
                    MemoryTimelineItem(
                        memory = memory,
                        expanded = uiState.expandedId == memory.id,
                        index = index,
                        visible = uiState.entranceReady,
                        onToggle = { onToggleExpand(memory.id) },
                        onEdit = { onEdit(memory) },
                        onDelete = { onDelete(memory.id) },
                        onToggleBlocked = { onToggleBlocked(memory) },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (uiState.editingPortrait) {
        PortraitEditDialog(
            portrait = uiState.portrait,
            onDismiss = onDismissPortrait,
            onSave = onSavePortrait,
        )
    }

    uiState.editingMemory?.let { memory ->
        MemoryEditDialog(
            memory = memory,
            onDismiss = onDismissEdit,
            onSave = onSaveEdit,
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AgentThemeColors

    val bg = if (selected) colors.surfaceSelected else colors.surface
    val border = if (selected) colors.accent.copy(alpha = 0.35f) else colors.outline
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun EmptyMemoryState(category: MemoryCategory) {
    val colors = AgentThemeColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有${category.displayName}",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "多聊几次，灵伴会把关于你的事轻轻记下来。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun PortraitEditDialog(
    portrait: UserPortraitUi,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    val colors = AgentThemeColors

    var name by remember(portrait) { mutableStateOf(portrait.name) }
    var interest by remember(portrait) { mutableStateOf(portrait.interest) }
    var occupation by remember(portrait) { mutableStateOf(portrait.occupation) }
    var goal by remember(portrait) { mutableStateOf(portrait.goal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("编辑画像", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MemoryField(label = "姓名", value = name, onValueChange = { name = it })
                MemoryField(label = "兴趣", value = interest, onValueChange = { interest = it })
                MemoryField(label = "职业", value = occupation, onValueChange = { occupation = it })
                MemoryField(label = "目标", value = goal, onValueChange = { goal = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, interest, occupation, goal) }) {
                Text("保存", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun MemoryEditDialog(
    memory: Memory,
    onDismiss: () -> Unit,
    onSave: (String, MemoryCategory) -> Unit,
) {
    val colors = AgentThemeColors

    var content by remember(memory.id) { mutableStateOf(memory.content) }
    var category by remember(memory.id) { mutableStateOf(memory.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("编辑记忆", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MemoryField(
                    label = "内容",
                    value = content,
                    onValueChange = { content = it },
                    singleLine = false,
                )
                Text("分类", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        val selected = category == cat
                        Text(
                            text = cat.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) colors.accent else colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.surfaceSelected else colors.surfaceMuted)
                                .clickable { category = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content, category) },
                enabled = content.isNotBlank(),
            ) {
                Text("保存", color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun MemoryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
) {
    val colors = AgentThemeColors

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent.copy(alpha = 0.5f),
            unfocusedBorderColor = colors.outline,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedLabelColor = colors.textSecondary,
            unfocusedLabelColor = colors.textSecondary,
        ),
    )
}
