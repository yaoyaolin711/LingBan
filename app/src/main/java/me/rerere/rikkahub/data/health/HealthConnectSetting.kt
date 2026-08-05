package me.rerere.rikkahub.data.health

import kotlinx.serialization.Serializable

/**
 * Health Connect 只读接入开关。
 * 数据仅用于伴侣上下文（睡眠/步数/心率摘要），不做医疗诊断。
 */
@Serializable
data class HealthConnectSetting(
    /** 是否将健康摘要注入伴侣对话上下文 */
    val enabled: Boolean = false,
    /** 注入今日步数 */
    val includeSteps: Boolean = true,
    /** 注入心率摘要 */
    val includeHeartRate: Boolean = true,
    /** 注入最近睡眠摘要 */
    val includeSleep: Boolean = true,
    /** 注入今日活动距离 / 卡路里（有则显示） */
    val includeActivityExtras: Boolean = true,
)
