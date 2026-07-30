package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberCurrentColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

val LocalSolaceColorScheme = staticCompositionLocalOf { lightSolaceColorScheme() }
val LocalSolaceShapes = staticCompositionLocalOf { SolaceShapesDefault }
val LocalSolaceAnimation = staticCompositionLocalOf { SolaceAnimationDefault }

/**
 * Solace global design system entry point.
 *
 * Use for all brand UI:
 * - [colorScheme] — Rose Gold Luxury colors (never hardcode in pages)
 * - [typography] — Solace type scale
 * - [shapes] — soft luxury corners
 * - [animation] — restrained motion
 */
object SolaceTheme {
    val colorScheme: SolaceColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalSolaceColorScheme.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes: SolaceShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalSolaceShapes.current

    val animation: SolaceAnimation
        @Composable
        @ReadOnlyComposable
        get() = LocalSolaceAnimation.current
}

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    colorMode: ColorMode = rememberCurrentColorMode(),
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    val solaceColors = remember(darkTheme) {
        if (darkTheme) darkSolaceColorScheme() else lightSolaceColorScheme()
    }

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        settings.themeId == "solace" || settings.themeId.isBlank() -> {
            // Default Solace brand → Material scheme from the same tokens
            solaceColors.toMaterialColorScheme(dark = darkTheme)
        }
        else -> {
            val theme = findThemeById(settings.themeId, settings.customThemes)
                ?: findPresetTheme(settings.themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme, solaceColors) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = SolacePalette.Scrim,
                surface = SolacePalette.Scrim,
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalSolaceColorScheme provides solaceColors,
        LocalSolaceShapes provides SolaceShapesDefault,
        LocalSolaceAnimation provides SolaceAnimationDefault,
        LocalOverscrollFactory provides null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorSchemeConverted,
            typography = SolaceTypography,
            shapes = SolaceMaterialShapes,
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
