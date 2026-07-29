package com.agent.chat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 标题级衬线：系统 Serif（Georgia / Noto Serif 等） */
private val TitleFont = FontFamily.Serif
private val BodyFont = FontFamily.SansSerif

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = TitleFont,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = (28 * 1.35).sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = TitleFont,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = (22 * 1.4).sp,
        letterSpacing = (-0.15).sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFont,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = (18 * 1.45).sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = (16 * 1.5).sp,
        color = TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = (16 * 1.55).sp,
        color = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = (14 * 1.55).sp,
        color = TextPrimary,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = (12 * 1.5).sp,
        color = TextSecondary,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = (14 * 1.45).sp,
        color = TextPrimary,
    ),
)
