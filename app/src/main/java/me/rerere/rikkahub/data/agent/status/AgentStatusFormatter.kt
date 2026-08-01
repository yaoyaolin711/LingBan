package me.rerere.rikkahub.data.agent.status

import me.rerere.rikkahub.data.agent.AgentAction
import me.rerere.rikkahub.data.agent.AgentPhase
import me.rerere.rikkahub.data.agent.AgentState

/**
 * Single source of UI status copy for Runtime-driven surfaces (TaskBall, Chat progress).
 * Input is always [AgentState] — do not format raw Runtime process events here.
 */
object AgentStatusFormatter {

    fun format(state: AgentState): String {
        val app = shortAppLabel(state.currentPackage)
        val actionLabel = state.lastAction?.let { statusForAction(it.action) }
        return when (state.phase) {
            AgentPhase.SUCCESS -> "已完成"
            AgentPhase.FAILED -> "已停止"
            AgentPhase.PERCEIVING -> withApp("正在感知界面…", app)
            AgentPhase.PLANNING -> withApp("正在规划操作…", app)
            AgentPhase.EXECUTING -> when {
                actionLabel != null && app != null -> "$actionLabel · $app"
                actionLabel != null -> actionLabel
                app != null -> "正在执行 · $app"
                else -> "正在执行…"
            }
            AgentPhase.VERIFYING -> withApp("正在验证结果…", app)
            AgentPhase.IDLE -> when {
                app != null -> "当前：$app"
                state.goal.isNotBlank() -> "准备执行：${state.goal}"
                else -> "任务进行中"
            }
        }
    }

    fun statusForAction(action: String): String = when (action) {
        AgentAction.OPEN_APP -> "正在打开应用…"
        AgentAction.CLICK_NODE, AgentAction.CLICK_XY -> "正在点击…"
        AgentAction.TYPE_TEXT -> "正在输入…"
        AgentAction.SWIPE -> "正在滑动…"
        AgentAction.GLOBAL -> "正在系统操作…"
        AgentAction.SEE_SCREEN, AgentAction.DUMP_UI -> "正在查看屏幕…"
        AgentAction.WAIT_FOR_TEXT, AgentAction.WAIT_FOR_PAGE -> "正在等待界面…"
        else -> "正在执行…"
    }

    fun shortAppLabel(packageName: String): String? {
        if (packageName.isBlank()) return null
        return packageName.substringAfterLast('.').ifBlank { packageName }
    }

    private fun withApp(base: String, app: String?): String =
        if (app.isNullOrBlank()) base else "$base · $app"
}
