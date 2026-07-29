package com.agent.chat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 克制浅色空间 — 冷灰白基调 */
val AppBg = Color(0xFFEDEEF0)
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceMuted = Color(0xFFF3F4F6) // 用户气泡等轻分区
val SurfaceSelected = Color(0xFFF0F2FA) // 选中人设浅高亮
val TextPrimary = Color(0xFF1A1A1E)
val TextSecondary = Color(0xFF6B6B72)
val Accent = Color(0xFF4A5FD9) // 低饱和靛蓝，克制使用
val OutlineSubtle = Color(0xFFE5E6EA)
val Danger = Color(0xFFD64545)
/** 克制的错误态：浅玫瑰底 + 柔和描边，避免刺眼大红 */
val ErrorSoftBg = Color(0xFFF8EEEE)
val ErrorSoftBorder = Color(0xFFD4A5A5)
val ErrorSoftText = Color(0xFF8F5A5A)

val BubbleUser = SurfaceMuted
val BubbleAssistant = SurfaceCard

/** 极轻阴影，近似 0 1px 3px rgba(0,0,0,0.06) */
val CardElevation = 1.5.dp
val BubbleElevation = 1.dp
