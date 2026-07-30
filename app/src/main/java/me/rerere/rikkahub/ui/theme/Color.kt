package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Solace Rose Gold Luxury — brand palette (single source of truth).
 *
 * Jewelry / perfume / luxury-app tone. Never hardcode these in pages;
 * consume via [SolaceTheme.colorScheme].
 */
object SolacePalette {
    // —— Brand tokens (spec) ——
    val RoseGold = Color(0xFFB76E79)
    val LightRose = Color(0xFFE8B4B8)
    val Champagne = Color(0xFFF6E3DC)
    val Background = Color(0xFFFFF9F7)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF3A3030)
    val SecondaryText = Color(0xFF8F7777)
    val Accent = Color(0xFFD9A0A7)

    // —— Derived neutrals (from brand family only) ——
    val OnPrimary = Color(0xFFFFFFFF)
    val Outline = Color(0xFFD4B8B8)
    val OutlineVariant = Color(0xFFEEDFDF)
    val SurfaceDim = Color(0xFFEDE0E0)
    val SurfaceContainerLow = Color(0xFFFFF4F2)
    val SurfaceContainer = Color(0xFFFCEEEC)
    val SurfaceContainerHigh = Color(0xFFF8E6E4)
    val SurfaceContainerHighest = Color(0xFFF3DDDB)
    val ChampagneDeep = Color(0xFFE8D0C8)
    val RoseGoldDeep = Color(0xFF8A4A55)
    val TextMuted = Color(0xFF5C4A4A)
    val Scrim = Color(0xFF000000)

    // —— Dark velvet counterparts (same hue family) ——
    val DarkBackground = Color(0xFF161211)
    val DarkSurface = Color(0xFF1C1716)
    val DarkSurfaceLowest = Color(0xFF100D0C)
    val DarkSurfaceLow = Color(0xFF1E1918)
    val DarkSurfaceContainer = Color(0xFF221D1C)
    val DarkSurfaceHigh = Color(0xFF2C2423)
    val DarkSurfaceHighest = Color(0xFF372E2C)
    val DarkText = Color(0xFFF0E6E4)
    val DarkSecondaryText = Color(0xFFC4B0AE)
    val DarkPrimary = Color(0xFFE8B4B8)
    val DarkAccent = Color(0xFFD9A0A7)
    val DarkChampagne = Color(0xFF4A3C38)
    val DarkOutline = Color(0xFF8F7777)
    val DarkOutlineVariant = Color(0xFF3D342F)
    val DarkOnPrimary = Color(0xFF3A2024)
    val DarkRoseGoldDeep = Color(0xFF6B3A42)
    val DarkChampagneBlob = Color(0xFF9A7A70)
}

/**
 * Semantic Solace color scheme for UI consumption.
 * Prefer this over raw [SolacePalette] in composables.
 */
@Immutable
data class SolaceColorScheme(
    val primary: Color,
    val lightRose: Color,
    val champagne: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val onPrimary: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val orbCore: Color,
    val auroraTop: Color,
    val auroraMid: Color,
    val auroraBottom: Color,
    val auroraBlobPrimary: Color,
    val auroraBlobSecondary: Color,
    val auroraBlobChampagne: Color,
    val auroraBlobPearl: Color,
    val scrim: Color,
)

fun lightSolaceColorScheme(): SolaceColorScheme = SolaceColorScheme(
    primary = SolacePalette.RoseGold,
    lightRose = SolacePalette.LightRose,
    champagne = SolacePalette.Champagne,
    background = SolacePalette.Background,
    surface = SolacePalette.Surface,
    text = SolacePalette.Text,
    secondaryText = SolacePalette.SecondaryText,
    accent = SolacePalette.Accent,
    onPrimary = SolacePalette.OnPrimary,
    outline = SolacePalette.Outline,
    outlineVariant = SolacePalette.OutlineVariant,
    surfaceContainerLowest = SolacePalette.Surface,
    surfaceContainerLow = SolacePalette.SurfaceContainerLow,
    surfaceContainer = SolacePalette.SurfaceContainer,
    surfaceContainerHigh = SolacePalette.SurfaceContainerHigh,
    surfaceContainerHighest = SolacePalette.SurfaceContainerHighest,
    glassFill = SolacePalette.Surface.copy(alpha = 0.78f),
    glassBorder = SolacePalette.OutlineVariant.copy(alpha = 0.55f),
    orbCore = SolacePalette.Surface,
    auroraTop = SolacePalette.LightRose.copy(alpha = 0.55f),
    auroraMid = SolacePalette.Champagne,
    auroraBottom = SolacePalette.Background,
    auroraBlobPrimary = SolacePalette.LightRose,
    auroraBlobSecondary = SolacePalette.Accent,
    auroraBlobChampagne = SolacePalette.ChampagneDeep,
    auroraBlobPearl = SolacePalette.Champagne,
    scrim = SolacePalette.Scrim,
)

fun darkSolaceColorScheme(): SolaceColorScheme = SolaceColorScheme(
    primary = SolacePalette.DarkPrimary,
    lightRose = SolacePalette.LightRose,
    champagne = SolacePalette.DarkChampagne,
    background = SolacePalette.DarkBackground,
    surface = SolacePalette.DarkSurface,
    text = SolacePalette.DarkText,
    secondaryText = SolacePalette.DarkSecondaryText,
    accent = SolacePalette.DarkAccent,
    onPrimary = SolacePalette.DarkOnPrimary,
    outline = SolacePalette.DarkOutline,
    outlineVariant = SolacePalette.DarkOutlineVariant,
    surfaceContainerLowest = SolacePalette.DarkSurfaceLowest,
    surfaceContainerLow = SolacePalette.DarkSurfaceLow,
    surfaceContainer = SolacePalette.DarkSurfaceContainer,
    surfaceContainerHigh = SolacePalette.DarkSurfaceHigh,
    surfaceContainerHighest = SolacePalette.DarkSurfaceHighest,
    glassFill = SolacePalette.DarkSurface.copy(alpha = 0.78f),
    glassBorder = SolacePalette.DarkOutlineVariant.copy(alpha = 0.55f),
    orbCore = SolacePalette.DarkSurfaceHighest,
    auroraTop = SolacePalette.DarkRoseGoldDeep,
    auroraMid = SolacePalette.DarkSurface,
    auroraBottom = SolacePalette.DarkBackground,
    auroraBlobPrimary = SolacePalette.RoseGold,
    auroraBlobSecondary = SolacePalette.DarkAccent,
    auroraBlobChampagne = SolacePalette.DarkChampagneBlob,
    auroraBlobPearl = SolacePalette.DarkOutline,
    scrim = SolacePalette.Scrim,
)

/** Maps Solace tokens → Material3 [ColorScheme] (for Material components). */
fun SolaceColorScheme.toMaterialColorScheme(dark: Boolean): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = SolacePalette.DarkRoseGoldDeep,
            onPrimaryContainer = lightRose,
            secondary = secondaryText,
            onSecondary = onPrimary,
            secondaryContainer = champagne,
            onSecondaryContainer = text,
            tertiary = accent,
            onTertiary = onPrimary,
            tertiaryContainer = SolacePalette.DarkRoseGoldDeep,
            onTertiaryContainer = lightRose,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceContainerHigh,
            onSurfaceVariant = secondaryText,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            inverseSurface = text,
            inverseOnSurface = background,
            inversePrimary = SolacePalette.RoseGold,
            surfaceDim = background,
            surfaceBright = surfaceContainerHighest,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = lightRose,
            onPrimaryContainer = SolacePalette.RoseGoldDeep,
            secondary = secondaryText,
            onSecondary = onPrimary,
            secondaryContainer = champagne,
            onSecondaryContainer = text,
            tertiary = accent,
            onTertiary = onPrimary,
            tertiaryContainer = champagne,
            onTertiaryContainer = SolacePalette.TextMuted,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = champagne,
            onSurfaceVariant = secondaryText,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            inverseSurface = text,
            inverseOnSurface = background,
            inversePrimary = lightRose,
            surfaceDim = SolacePalette.SurfaceDim,
            surfaceBright = background,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
        )
    }
}

// region ExtendColors (semantic utility scales — still centralized here)

data class ExtendColors(
    val red1: Color,
    val red2: Color,
    val red3: Color,
    val red4: Color,
    val red5: Color,
    val red6: Color,
    val red7: Color,
    val red8: Color,
    val red9: Color,
    val red10: Color,
    val orange1: Color,
    val orange2: Color,
    val orange3: Color,
    val orange4: Color,
    val orange5: Color,
    val orange6: Color,
    val orange7: Color,
    val orange8: Color,
    val orange9: Color,
    val orange10: Color,
    val green1: Color,
    val green2: Color,
    val green3: Color,
    val green4: Color,
    val green5: Color,
    val green6: Color,
    val green7: Color,
    val green8: Color,
    val green9: Color,
    val green10: Color,
    val blue1: Color,
    val blue2: Color,
    val blue3: Color,
    val blue4: Color,
    val blue5: Color,
    val blue6: Color,
    val blue7: Color,
    val blue8: Color,
    val blue9: Color,
    val blue10: Color,
    val gray1: Color,
    val gray2: Color,
    val gray3: Color,
    val gray4: Color,
    val gray5: Color,
    val gray6: Color,
    val gray7: Color,
    val gray8: Color,
    val gray9: Color,
    val gray10: Color,
)

fun lightExtendColors(): ExtendColors = ExtendColors(
    red1 = Color(255, 236, 232),
    red2 = Color(253, 205, 197),
    red3 = Color(251, 172, 163),
    red4 = Color(249, 137, 129),
    red5 = Color(247, 101, 96),
    red6 = Color(245, 63, 63),
    red7 = Color(203, 39, 45),
    red8 = Color(161, 21, 30),
    red9 = Color(119, 8, 19),
    red10 = Color(77, 0, 10),
    orange1 = Color(255, 247, 232),
    orange2 = Color(255, 228, 186),
    orange3 = Color(255, 207, 139),
    orange4 = Color(255, 182, 93),
    orange5 = Color(255, 154, 46),
    orange6 = Color(255, 125, 0),
    orange7 = Color(210, 95, 0),
    orange8 = Color(166, 69, 0),
    orange9 = Color(121, 46, 0),
    orange10 = Color(77, 27, 0),
    green1 = Color(232, 255, 234),
    green2 = Color(175, 240, 181),
    green3 = Color(123, 225, 136),
    green4 = Color(76, 210, 99),
    green5 = Color(35, 195, 67),
    green6 = Color(0, 180, 42),
    green7 = Color(0, 154, 41),
    green8 = Color(0, 128, 38),
    green9 = Color(0, 102, 34),
    green10 = Color(0, 77, 28),
    blue1 = Color(232, 247, 255),
    blue2 = Color(195, 231, 254),
    blue3 = Color(159, 212, 253),
    blue4 = Color(123, 192, 252),
    blue5 = Color(87, 169, 251),
    blue6 = Color(52, 145, 250),
    blue7 = Color(32, 108, 207),
    blue8 = Color(17, 75, 163),
    blue9 = Color(6, 48, 120),
    blue10 = Color(0, 26, 77),
    gray1 = Color(247, 248, 250),
    gray2 = Color(242, 243, 245),
    gray3 = Color(229, 230, 235),
    gray4 = Color(201, 205, 212),
    gray5 = Color(169, 174, 184),
    gray6 = Color(134, 144, 156),
    gray7 = Color(107, 119, 133),
    gray8 = Color(78, 89, 105),
    gray9 = Color(39, 46, 59),
    gray10 = Color(29, 33, 41),
)

fun darkExtendColors(): ExtendColors = ExtendColors(
    red1 = Color(77, 0, 10),
    red2 = Color(119, 6, 17),
    red3 = Color(161, 22, 31),
    red4 = Color(203, 46, 52),
    red5 = Color(245, 78, 78),
    red6 = Color(247, 105, 101),
    red7 = Color(249, 141, 134),
    red8 = Color(251, 176, 167),
    red9 = Color(253, 209, 202),
    red10 = Color(255, 240, 236),
    orange1 = Color(77, 27, 0),
    orange2 = Color(121, 48, 4),
    orange3 = Color(166, 75, 10),
    orange4 = Color(210, 105, 19),
    orange5 = Color(255, 141, 31),
    orange6 = Color(255, 150, 38),
    orange7 = Color(255, 179, 87),
    orange8 = Color(255, 205, 135),
    orange9 = Color(255, 227, 184),
    orange10 = Color(255, 247, 232),
    green1 = Color(0, 77, 28),
    green2 = Color(4, 102, 37),
    green3 = Color(10, 128, 45),
    green4 = Color(18, 154, 55),
    green5 = Color(29, 180, 64),
    green6 = Color(39, 195, 70),
    green7 = Color(80, 210, 102),
    green8 = Color(126, 225, 139),
    green9 = Color(178, 240, 183),
    green10 = Color(235, 255, 236),
    blue1 = Color(0, 26, 77),
    blue2 = Color(5, 47, 120),
    blue3 = Color(19, 76, 163),
    blue4 = Color(41, 113, 207),
    blue5 = Color(70, 154, 250),
    blue6 = Color(90, 170, 251),
    blue7 = Color(125, 193, 252),
    blue8 = Color(161, 213, 253),
    blue9 = Color(198, 232, 254),
    blue10 = Color(234, 248, 255),
    gray1 = Color(23, 23, 26),
    gray2 = Color(46, 46, 48),
    gray3 = Color(72, 72, 73),
    gray4 = Color(95, 95, 96),
    gray5 = Color(120, 120, 122),
    gray6 = Color(146, 146, 147),
    gray7 = Color(171, 171, 172),
    gray8 = Color(197, 197, 197),
    gray9 = Color(223, 223, 223),
    gray10 = Color(246, 246, 246),
)

// endregion

object CustomColors {
    var black = false

    val topBarColors: TopAppBarColors
        @Composable get() {
            val colors = SolaceTheme.colorScheme
            return if (!LocalDarkMode.current) TopAppBarDefaults.topAppBarColors(
                containerColor = colors.background,
                scrolledContainerColor = colors.surfaceContainerLow.copy(alpha = 0.92f),
                titleContentColor = colors.text,
                navigationIconContentColor = colors.text,
                actionIconContentColor = colors.secondaryText,
            ) else TopAppBarDefaults.topAppBarColors()
        }

    val cardColors: CardColors
        @Composable get() = CardDefaults.cardColors(containerColor = SolaceTheme.colorScheme.surfaceContainerLow)

    val cardColorsOnSurfaceContainer: CardColors
        @Composable get() = CardDefaults.cardColors(containerColor = SolaceTheme.colorScheme.surfaceContainerLowest)

    val listItemColors: ListItemColors
        @Composable get() = ListItemDefaults.colors(containerColor = SolaceTheme.colorScheme.surfaceContainerLowest)
}
