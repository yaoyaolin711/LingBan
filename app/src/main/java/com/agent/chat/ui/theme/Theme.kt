package com.agent.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ChatColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = SurfaceSelected,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = Color.White,
    background = AppBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = Danger,
    onError = Color.White,
)

@Composable
fun AgentChatTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ChatColorScheme,
        typography = Typography,
        content = content,
    )
}
