package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme

/**
 * Solace companion warm theme — soft orange accent on warm cream surfaces.
 */
val SolaceThemePreset by lazy {
    PresetTheme(
        id = "solace",
        name = {
            Text(stringResource(R.string.theme_name_solace))
        },
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

private val primaryLight = Color(0xFFE8823A)
private val onPrimaryLight = Color(0xFFFFFFFF)
private val primaryContainerLight = Color(0xFFFFE4D1)
private val onPrimaryContainerLight = Color(0xFF5C2E0A)
private val secondaryLight = Color(0xFF8A7468)
private val onSecondaryLight = Color(0xFFFFFFFF)
private val secondaryContainerLight = Color(0xFFFFF0E6)
private val onSecondaryContainerLight = Color(0xFF3D2E26)
private val tertiaryLight = Color(0xFFB86B3A)
private val onTertiaryLight = Color(0xFFFFFFFF)
private val tertiaryContainerLight = Color(0xFFFFD7B5)
private val onTertiaryContainerLight = Color(0xFF3D220C)
private val errorLight = Color(0xFFD64545)
private val onErrorLight = Color(0xFFFFFFFF)
private val errorContainerLight = Color(0xFFFFF1EE)
private val onErrorContainerLight = Color(0xFF9A5A4A)
private val backgroundLight = Color(0xFFFFF7F1)
private val onBackgroundLight = Color(0xFF2A211C)
private val surfaceLight = Color(0xFFFFF7F1)
private val onSurfaceLight = Color(0xFF2A211C)
private val surfaceVariantLight = Color(0xFFFFF0E6)
private val onSurfaceVariantLight = Color(0xFF8A7468)
private val outlineLight = Color(0xFFC4A99A)
private val outlineVariantLight = Color(0xFFF0E0D4)
private val scrimLight = Color(0xFF000000)
private val inverseSurfaceLight = Color(0xFF2A211C)
private val inverseOnSurfaceLight = Color(0xFFFFF7F1)
private val inversePrimaryLight = Color(0xFFFFB074)
private val surfaceDimLight = Color(0xFFEDE0D4)
private val surfaceBrightLight = Color(0xFFFFF7F1)
private val surfaceContainerLowestLight = Color(0xFFFFFFFF)
private val surfaceContainerLowLight = Color(0xFFFFF3EA)
private val surfaceContainerLight = Color(0xFFFFF0E6)
private val surfaceContainerHighLight = Color(0xFFFFE8D9)
private val surfaceContainerHighestLight = Color(0xFFFFE4D1)

private val primaryDark = Color(0xFFFFB074)
private val onPrimaryDark = Color(0xFF4A2408)
private val primaryContainerDark = Color(0xFF8A4A1E)
private val onPrimaryContainerDark = Color(0xFFFFE4D1)
private val secondaryDark = Color(0xFFD4B8A8)
private val onSecondaryDark = Color(0xFF3A2C24)
private val secondaryContainerDark = Color(0xFF4A3A32)
private val onSecondaryContainerDark = Color(0xFFFFF0E6)
private val tertiaryDark = Color(0xFFFFD7B5)
private val onTertiaryDark = Color(0xFF3D220C)
private val tertiaryContainerDark = Color(0xFF6B3E22)
private val onTertiaryContainerDark = Color(0xFFFFE4D1)
private val errorDark = Color(0xFFFFB4AB)
private val onErrorDark = Color(0xFF690005)
private val errorContainerDark = Color(0xFF93000A)
private val onErrorContainerDark = Color(0xFFFFDAD6)
private val backgroundDark = Color(0xFF14110F)
private val onBackgroundDark = Color(0xFFEDE4DC)
private val surfaceDark = Color(0xFF14110F)
private val onSurfaceDark = Color(0xFFEDE4DC)
private val surfaceVariantDark = Color(0xFF2A241F)
private val onSurfaceVariantDark = Color(0xFFC4B0A4)
private val outlineDark = Color(0xFF8A7468)
private val outlineVariantDark = Color(0xFF3D342E)
private val scrimDark = Color(0xFF000000)
private val inverseSurfaceDark = Color(0xFFEDE4DC)
private val inverseOnSurfaceDark = Color(0xFF2A211C)
private val inversePrimaryDark = Color(0xFFE8823A)
private val surfaceDimDark = Color(0xFF14110F)
private val surfaceBrightDark = Color(0xFF3A322C)
private val surfaceContainerLowestDark = Color(0xFF0F0C0A)
private val surfaceContainerLowDark = Color(0xFF1E1A17)
private val surfaceContainerDark = Color(0xFF221E1A)
private val surfaceContainerHighDark = Color(0xFF2A241F)
private val surfaceContainerHighestDark = Color(0xFF352E28)

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)
