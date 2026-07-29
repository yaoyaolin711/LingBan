package com.agent.chat.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.agent.chat.domain.model.ExpressionProfile
import com.agent.chat.domain.model.SentenceLength

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpressionManageDialog(
    profile: ExpressionProfile,
    customized: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExpressionProfile) -> Unit,
    onApplyRecommended: () -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(profile) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表达风格") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (customized) {
                        "当前为自定义表达风格。控制「怎么说」，不限制有没有情感。"
                    } else {
                        "当前跟随关系推荐默认值。你可自定义或点「恢复推荐」。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExpressionSlider("真人感", draft.naturalness) {
                    draft = draft.copy(naturalness = it)
                }
                ExpressionSlider("戏剧化", draft.dramaticLevel) {
                    draft = draft.copy(dramaticLevel = it)
                }
                ExpressionSlider("文学化", draft.poeticLevel) {
                    draft = draft.copy(poeticLevel = it)
                }
                ExpressionSlider("幽默", draft.humorLevel) {
                    draft = draft.copy(humorLevel = it)
                }
                ExpressionSlider("Emoji", draft.emojiLevel) {
                    draft = draft.copy(emojiLevel = it)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("句长", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SentenceLength.entries.forEach { length ->
                        FilterChip(
                            selected = draft.sentenceLength == length,
                            onClick = { draft = draft.copy(sentenceLength = length) },
                            label = {
                                Text(
                                    when (length) {
                                        SentenceLength.SHORT -> "短"
                                        SentenceLength.MEDIUM -> "中"
                                        SentenceLength.LONG -> "长"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onApplyRecommended) {
                Text("恢复推荐")
            }
        },
    )
}

@Composable
private fun ExpressionSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "$label：$value", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99,
        )
    }
}
