package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.solace.CompanionAvatar
import me.rerere.rikkahub.ui.components.solace.CompanionAvatarSize
import me.rerere.rikkahub.ui.theme.SolaceTheme

/**
 * Empty-chat companion welcome — immersive, not a tool blank state.
 */
@Composable
fun CompanionChatWelcome(
    assistant: Assistant?,
    modifier: Modifier = Modifier,
) {
    val colors = SolaceTheme.colorScheme
    val typography = SolaceTheme.typography
    val name = assistant?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.assistant_page_default_assistant)
    val avatar = assistant?.avatar ?: Avatar.Dummy

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompanionAvatar(
            name = name,
            avatar = avatar,
            size = CompanionAvatarSize.Medium,
            showName = false,
            showHalo = false,
            breath = false,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.chat_page_companion_welcome_title, name),
            style = typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.chat_page_companion_welcome_subtitle),
            style = typography.bodyMedium,
            color = colors.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
