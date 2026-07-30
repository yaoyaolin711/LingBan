package me.rerere.rikkahub.data.device

import kotlinx.serialization.Serializable

/**
 * 伴侣使用关怀 / 主动聊天 / 本机设备控制相关全局设置.
 */
@Serializable
data class CompanionAssistSetting(
    /** 是否启用后台监测前台服务（使用关怀） */
    val monitorEnabled: Boolean = false,
    /** 连续使用同一目标 App 超过该分钟数则干预 */
    val thresholdMinutes: Int = 30,
    /** 同一包名干预冷却(分钟) */
    val cooldownMinutes: Int = 45,
    /** 轮询间隔(秒) */
    val pollIntervalSeconds: Int = 45,
    /** 干预/主动文案是否走 LLM 生成; 失败则用默认文案 */
    val useLlmMessage: Boolean = true,
    /** 是否允许高级 device_shell (需 Shizuku) */
    val enableAdvancedShell: Boolean = false,
    /**
     * 监测的包名列表. 空列表表示监测所有非桌面第三方 App
     * (仍排除本 App 与 launcher).
     */
    val monitoredPackages: List<String> = DEFAULT_MONITORED_PACKAGES,

    // ---- 主动找用户聊天 ----

    /** 是否启用人设主动聊天（早安/晚安/沉默唤醒） */
    val proactiveChatEnabled: Boolean = false,
    /** 用户超过该小时未对话则主动找人（沉默唤醒） */
    val silenceHours: Int = 6,
    /** 两次主动聊天的最小间隔(分钟) */
    val proactiveCooldownMinutes: Int = 180,
    /** 早安问候 */
    val morningGreetingEnabled: Boolean = true,
    /** 早安触发小时（本地时区，0-23） */
    val morningHour: Int = 9,
    /** 晚安/晚间问候 */
    val eveningGreetingEnabled: Boolean = true,
    /** 晚间触发小时 */
    val eveningHour: Int = 21,
    /** 上次主动聊天时间 epoch ms（持久化防重启刷屏） */
    val lastProactiveAtEpochMs: Long = 0L,
    /** 上次早安日期 yyyy-MM-dd */
    val lastMorningDate: String = "",
    /** 上次晚间日期 yyyy-MM-dd */
    val lastEveningDate: String = "",
) {
    /** 监测或主动聊天任一开启时需要跑前台服务 */
    val needsForegroundService: Boolean
        get() = monitorEnabled || proactiveChatEnabled

    companion object {
        val DEFAULT_MONITORED_PACKAGES: List<String> = listOf(
            "com.ss.android.ugc.aweme", // 抖音
            "com.ss.android.ugc.aweme.lite", // 抖音极速版
            "com.ss.android.ugc.live", // 抖音火山版等
            "com.smile.gifmaker", // 快手
            "com.kuaishou.nebula", // 快手极速版
            "tv.danmaku.bili", // 哔哩哔哩
            "com.google.android.youtube",
            "com.tencent.qqlive",
            "com.qiyi.video",
            "com.youku.phone",
        )
    }
}

enum class ProactiveChatReason {
    MORNING,
    EVENING,
    SILENCE,
}
