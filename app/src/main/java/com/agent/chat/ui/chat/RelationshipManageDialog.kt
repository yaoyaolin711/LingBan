package com.agent.chat.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.chat.domain.model.InteractionStyle
import com.agent.chat.domain.model.RelationshipProfile
import com.agent.chat.domain.model.RelationshipType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RelationshipManageDialog(
    profile: RelationshipProfile,
    onDismiss: () -> Unit,
    onSave: (RelationshipProfile) -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(profile) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关系设定") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "独立于角色人设：决定你和 AI 是什么关系、如何互动。高亲密等级不会被限制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("关系类型", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RelationshipType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.relationshipType == type,
                            onClick = { draft = draft.copy(relationshipType = type) },
                            label = { Text(type.displayName) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("互动风格", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InteractionStyle.entries.forEach { style ->
                        FilterChip(
                            selected = draft.interactionStyle == style,
                            onClick = { draft = draft.copy(interactionStyle = style) },
                            label = { Text(style.displayName) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                RelationshipSlider(
                    label = "亲密度",
                    value = draft.intimacyLevel,
                    onValueChange = { draft = draft.copy(intimacyLevel = it) },
                )
                RelationshipSlider(
                    label = "情感表达",
                    value = draft.affectionLevel,
                    onValueChange = { draft = draft.copy(affectionLevel = it) },
                )
                RelationshipSlider(
                    label = "主动性",
                    value = draft.initiativeLevel,
                    onValueChange = { draft = draft.copy(initiativeLevel = it) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun RelationshipSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label：$value",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
        )
    }
}
