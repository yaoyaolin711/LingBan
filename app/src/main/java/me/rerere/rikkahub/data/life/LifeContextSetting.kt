package me.rerere.rikkahub.data.life

import kotlinx.serialization.Serializable

/**
 * 作息感知（生活画像 V1）：根据屏幕使用估计过夜休息窗，注入伴侣上下文。
 */
@Serializable
data class LifeContextSetting(
    /** 是否估计休息窗并注入伴侣对话 / 早安文案 */
    val enabled: Boolean = false,
)
