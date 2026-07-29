package com.agent.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.chat.domain.model.Persona
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.CardElevation

data class PersonaPickerItem(
    val id: String?,
    val name: String,
    val avatar: String,
    val description: String,
)

/**
 * 克制的人设卡片列表：选中态 = 左侧细强调色竖线 + 浅色背景。
 * @param scrollable 在固定高度容器内需要滚动时设为 true
 */
@Composable
fun PersonaPickerList(
    personas: List<Persona>,
    selectedPersonaId: String?,
    onPersonaSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    includeNoneOption: Boolean = true,
    scrollable: Boolean = false,
) {
    val items = remember(personas, includeNoneOption) {
        buildList {
            if (includeNoneOption) {
                add(
                    PersonaPickerItem(
                        id = null,
                        name = "自由对话",
                        avatar = "◇",
                        description = "不绑定人设，直接和模型聊天",
                    ),
                )
            }
            personas.forEach { persona ->
                add(
                    PersonaPickerItem(
                        id = persona.id,
                        name = persona.name,
                        avatar = persona.avatar,
                        description = persona.description.ifBlank { persona.systemPrompt.take(64) },
                    ),
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            PersonaPickerRow(
                item = item,
                selected = item.id == selectedPersonaId,
                onClick = { onPersonaSelected(item.id) },
            )
        }
    }
}

@Composable
private fun PersonaPickerRow(
    item: PersonaPickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AgentThemeColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.surfaceSelected else colors.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = CardElevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent),
            )
            Spacer(modifier = Modifier.width(14.dp))
            PersonaAvatar(
                name = item.name,
                avatar = item.avatar,
                size = 44.dp,
                highlighted = selected,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** @deprecated 保留旧名，内部转发到列表选择器 */
@Deprecated("Use PersonaPickerList", ReplaceWith("PersonaPickerList(personas, selectedPersonaId, onPersonaSelected, modifier, includeNoneOption)"))
@Composable
fun PersonaHorizontalPager(
    personas: List<Persona>,
    selectedPersonaId: String?,
    onPersonaSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    includeNoneOption: Boolean = true,
) {
    PersonaPickerList(
        personas = personas,
        selectedPersonaId = selectedPersonaId,
        onPersonaSelected = onPersonaSelected,
        modifier = modifier,
        includeNoneOption = includeNoneOption,
    )
}
