package me.rerere.rikkahub.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.PresetTheme
import me.rerere.rikkahub.ui.theme.darkSolaceColorScheme
import me.rerere.rikkahub.ui.theme.lightSolaceColorScheme
import me.rerere.rikkahub.ui.theme.toMaterialColorScheme

/**
 * Solace Rose Gold Luxury preset — colors live only in [me.rerere.rikkahub.ui.theme.Color].
 */
val SolaceThemePreset by lazy {
    PresetTheme(
        id = "solace",
        name = {
            Text(stringResource(R.string.theme_name_solace))
        },
        standardLight = lightSolaceColorScheme().toMaterialColorScheme(dark = false),
        standardDark = darkSolaceColorScheme().toMaterialColorScheme(dark = true),
    )
}
