package com.agent.chat.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    Light,
    Dark,
}

@Immutable
data class AgentColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceSelected: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val outline: Color,
    val bubbleUser: Color,
    val bubbleAssistant: Color,
    val atmosphere: Color,
    val isDark: Boolean,
)

val LightAgentColors = AgentColors(
    background = Color(0xFFFFF7F1),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFFFF0E6),
    surfaceSelected = Color(0xFFFFE4D1),
    textPrimary = Color(0xFF2A211C),
    textSecondary = Color(0xFF8A7468),
    accent = Color(0xFFE8823A),
    accentSoft = Color(0xFFFFB074),
    outline = Color(0xFFF0E0D4),
    bubbleUser = Color(0xFFFFD7B5),
    bubbleAssistant = Color(0xFFFFFFFF),
    atmosphere = Color(0xFFFFF3EA),
    isDark = false,
)

val DarkAgentColors = AgentColors(
    background = Color(0xFF14110F),
    surface = Color(0xFF1E1A17),
    surfaceMuted = Color(0xFF2A241F),
    surfaceSelected = Color(0xFF3A2E24),
    textPrimary = Color(0xFFF5EDE6),
    textSecondary = Color(0xFFB5A499),
    accent = Color(0xFFFF9B57),
    accentSoft = Color(0xFFE8823A),
    outline = Color(0xFF3D342C),
    bubbleUser = Color(0xFF4A3424),
    bubbleAssistant = Color(0xFF1E1A17),
    atmosphere = Color(0xFF1A1512),
    isDark = true,
)

val LocalAgentColors = staticCompositionLocalOf { LightAgentColors }

val AgentThemeColors: AgentColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAgentColors.current

private fun AgentColors.toLightScheme(): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = surfaceSelected,
    onPrimaryContainer = textPrimary,
    secondary = textSecondary,
    onSecondary = Color.White,
    tertiary = accentSoft,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceMuted,
    onSurfaceVariant = textSecondary,
    outline = outline,
    error = Danger,
    onError = Color.White,
)

private fun AgentColors.toDarkScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF2A1810),
    primaryContainer = surfaceSelected,
    onPrimaryContainer = textPrimary,
    secondary = textSecondary,
    onSecondary = textPrimary,
    tertiary = accentSoft,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceMuted,
    onSurfaceVariant = textSecondary,
    outline = outline,
    error = Danger,
    onError = Color.White,
)

@Composable
fun AgentChatTheme(
    themeMode: AppThemeMode = AppThemeMode.Light,
    content: @Composable () -> Unit,
) {
    val target = if (themeMode == AppThemeMode.Dark) DarkAgentColors else LightAgentColors
    // Material Motion "Emphasized" easing for theme switch — feels more physical
    val emphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val animSpec = tween<Color>(durationMillis = 380, easing = emphasizedEasing)

    val background by animateColorAsState(target.background, animSpec, label = "bg")
    val surface by animateColorAsState(target.surface, animSpec, label = "surface")
    val surfaceMuted by animateColorAsState(target.surfaceMuted, animSpec, label = "muted")
    val surfaceSelected by animateColorAsState(target.surfaceSelected, animSpec, label = "selected")
    val textPrimary by animateColorAsState(target.textPrimary, animSpec, label = "text1")
    val textSecondary by animateColorAsState(target.textSecondary, animSpec, label = "text2")
    val accent by animateColorAsState(target.accent, animSpec, label = "accent")
    val accentSoft by animateColorAsState(target.accentSoft, animSpec, label = "accentSoft")
    val outline by animateColorAsState(target.outline, animSpec, label = "outline")
    val bubbleUser by animateColorAsState(target.bubbleUser, animSpec, label = "bubbleUser")
    val bubbleAssistant by animateColorAsState(target.bubbleAssistant, animSpec, label = "bubbleAsst")
    val atmosphere by animateColorAsState(target.atmosphere, animSpec, label = "atmosphere")

    val colors = AgentColors(
        background = background,
        surface = surface,
        surfaceMuted = surfaceMuted,
        surfaceSelected = surfaceSelected,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        accent = accent,
        accentSoft = accentSoft,
        outline = outline,
        bubbleUser = bubbleUser,
        bubbleAssistant = bubbleAssistant,
        atmosphere = atmosphere,
        isDark = target.isDark,
    )

    CompositionLocalProvider(LocalAgentColors provides colors) {
        MaterialTheme(
            colorScheme = if (colors.isDark) colors.toDarkScheme() else colors.toLightScheme(),
            typography = Typography,
            content = content,
        )
    }
}
