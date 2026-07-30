package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R

private val base = Typography()

/**
 * Solace Rose Gold Luxury typography.
 * Soft hierarchy, airy titles — jewelry / perfume brand restraint.
 */
val SolaceTypography: Typography = Typography(
    displayLarge = base.displayLarge.copy(
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = base.displayMedium.copy(
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = base.displaySmall.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    headlineLarge = base.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    titleLarge = base.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = base.titleSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = base.bodyLarge.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = base.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = base.bodySmall.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = base.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = base.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.35.sp,
    ),
    labelSmall = base.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)

/** Alias kept for MaterialTheme / existing call sites. */
val Typography: Typography = SolaceTypography

@OptIn(ExperimentalTextApi::class)
val JetbrainsMono = FontFamily(
    Font(
        resId = R.font.jetbrains_mono,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(FontWeight.Normal.weight),
        )
    )
)
