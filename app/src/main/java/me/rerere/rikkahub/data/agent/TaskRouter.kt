package me.rerere.rikkahub.data.agent

/**
 * Routes a user utterance before Chat LLM / AgentRuntime.
 *
 * Local rules only — no LLM, no UI scan.
 */
enum class TaskRoute {
    /** Normal chat; PhoneControl tools may still be used by the LLM. */
    CHAT,

    /** Device task handled entirely by AgentRuntime. */
    DEVICE_TASK,

    /** Runtime executes device ops, then LLM summarizes for the user. */
    HYBRID,
}

data class TaskRouteDecision(
    val route: TaskRoute,
    val goal: String,
    val executionMode: ExecutionMode = ExecutionMode.RULE,
    val reason: String = "",
)

object TaskRouter {

    private val SUMMARY_HINT = Regex(
        """告诉我|告訴我|总结|總結|汇报|彙報|描述|看看|怎么样|怎麼樣|如何|说明|說明|说一下|說一下|报告|報告""",
    )

    private val PHONE_HINT = Regex(
        """打开|開啟|启动|啟動|open|launch|返回|后退|back|桌面|主屏|home|点击|點擊|click|按一下|滑动|滑動|swipe|输入|輸入|type|微信|支付宝|抖音|设置|短信""",
        RegexOption.IGNORE_CASE,
    )

    fun classify(
        text: String,
        phoneControlEnabled: Boolean,
    ): TaskRouteDecision {
        val goal = text.trim()
        if (!phoneControlEnabled || goal.isEmpty()) {
            return TaskRouteDecision(TaskRoute.CHAT, goal, reason = "phone_disabled_or_empty")
        }

        // Complex multi-step phone work stays on LLM + DeviceControlTools this phase.
        if (LocalRuleEngine.isComplexGoal(goal)) {
            return TaskRouteDecision(TaskRoute.CHAT, goal, reason = "complex_use_tools")
        }

        val rulePlan = LocalRuleEngine.tryPlan(
            TaskContext.of(goal = goal, state = TaskState(taskId = "route", goal = goal)),
        )
        val ruleExecutable = rulePlan != null &&
            rulePlan.actions.none { it.action == AgentAction.FAIL }

        if (!ruleExecutable) {
            return TaskRouteDecision(TaskRoute.CHAT, goal, reason = "not_rule_executable")
        }

        val wantsSummary = SUMMARY_HINT.containsMatchIn(goal) ||
            (PHONE_HINT.containsMatchIn(goal) && goal.length > 18 && goal.contains(Regex("[?？]")))

        return if (wantsSummary) {
            TaskRouteDecision(
                route = TaskRoute.HYBRID,
                goal = goal,
                executionMode = ExecutionMode.RULE,
                reason = "rule_plus_summary",
            )
        } else {
            TaskRouteDecision(
                route = TaskRoute.DEVICE_TASK,
                goal = goal,
                executionMode = ExecutionMode.RULE,
                reason = rulePlan?.reasoning ?: "rule",
            )
        }
    }
}
