package me.rerere.rikkahub.data.companion.policy

/**
 * 陪伴模式可主动下发的设备目标白名单。
 * 仅 RULE 模式可执行的短目标；禁止自由文案 / LLM 规划。
 */
object CompanionDeviceGoals {
    /** 对应 LocalRuleEngine HOME → global home */
    const val GO_HOME = "回到桌面"

    val ALLOWED: Set<String> = setOf(GO_HOME)

    fun isAllowed(goal: String): Boolean = goal.trim() in ALLOWED
}
