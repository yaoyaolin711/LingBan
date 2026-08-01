package me.rerere.rikkahub.data.agent

/**
 * Fast local rules — no LLM, no full tree required for most cases.
 */
object LocalRuleEngine {

    private val APP_ALIASES: List<Pair<Regex, String>> = listOf(
        Regex("微信|wechat|weixin") to "com.tencent.mm",
        Regex("qq(?![a-z])|手机qq") to "com.tencent.mobileqq",
        Regex("支付宝|alipay") to "com.eg.android.AlipayGphone",
        Regex("抖音|tiktok|douyin") to "com.ss.android.ugc.aweme",
        Regex("淘宝|taobao") to "com.taobao.taobao",
        Regex("浏览器|chrome") to "com.android.chrome",
        Regex("设置|settings") to "com.android.settings",
        Regex("短信|信息|messages?") to "com.android.mms",
        Regex("电话|拨号|dialer") to "com.android.dialer",
        Regex("相机|camera") to "com.android.camera",
        Regex("相册|图库|gallery|photos") to "com.android.gallery3d",
        Regex("地图|map") to "com.google.android.apps.maps",
        Regex("网易云|云音乐") to "com.netease.cloudmusic",
        Regex("哔哩哔哩|bilibili|b站") to "tv.danmaku.bili",
    )

    private val OPEN_APP = Regex(
        """^(?:请|请帮我|幫我|帮我|麻烦|麻煩)?\s*(打开|開啟|启动|啟動|open|launch|start)\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val BACK = Regex("""^(返回|后退|back)$""", RegexOption.IGNORE_CASE)
    private val HOME = Regex("""^(回到?桌面|主屏|home)$""", RegexOption.IGNORE_CASE)
    private val CLICK = Regex(
        """^(点击|點擊|按|点一下|click)\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    /** 打开微信然后点击搜索 / 打开支付宝，点击扫一扫 */
    private val OPEN_THEN_CLICK = Regex(
        """^(?:请|请帮我|幫我|帮我|麻烦|麻煩)?\s*(?:打开|開啟|启动|啟動|open|launch)\s*(.+?)\s*(?:，|,|然后|然後|并|並且|再|之后|之後)\s*(?:点击|點擊|按|点一下|click)\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    /** 打开微信点搜索 */
    private val OPEN_THEN_CLICK_SHORT = Regex(
        """^(?:请|请帮我|幫我|帮我|麻烦|麻煩)?\s*(?:打开|開啟|启动|啟動|open|launch)\s*(.+?)\s*(?:点击|點擊|按|点一下|click)\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val COMPLEX = Regex(
        """发送|傳送|转发|搜[索尋]|登录|登錄|填写|連續|然后|然後|之后|之後|并|並且|和.+说|給.+發|给.+发|播放|听歌|我喜欢|喜欢的|歌单|下单|支付|关注|点赞|收藏|切换|关闭""",
    )
    /** Soft suffixes that may trail an app name without adding intent (打开网易云音乐). */
    private val APP_NAME_NOISE = Regex(
        """[\s　]*(音乐|app|应用|软件|客户端|程式)?[\s　]*""",
        RegexOption.IGNORE_CASE,
    )

    fun isComplexGoal(goal: String): Boolean {
        val g = goal.trim()
        if (g.isEmpty()) return false
        // Compound open→click is handled by local multi-step rules
        if (OPEN_THEN_CLICK.containsMatchIn(g) || OPEN_THEN_CLICK_SHORT.containsMatchIn(g)) {
            val m = OPEN_THEN_CLICK.find(g) ?: OPEN_THEN_CLICK_SHORT.find(g)
            val clickTarget = m?.groupValues?.getOrNull(2).orEmpty()
            if (!COMPLEX.containsMatchIn(clickTarget) && clickTarget.length <= 24) return false
        }
        // Pure "打开微信" / "打开网易云音乐" is simple; "打开网易云播放我喜欢的" is complex.
        if (isPureOpenAppRequest(g)) return false
        if (BACK.matches(g) || HOME.matches(g)) return false
        if (CLICK.containsMatchIn(g) && !COMPLEX.containsMatchIn(g) &&
            CLICK.matchEntire(g) != null
        ) {
            return false
        }
        // Open + residual intent (播放/发消息/…) must not be treated as one-shot open.
        OPEN_APP.find(g)?.let { m ->
            val name = m.groupValues.getOrElse(2) { "" }.trim()
            if (resolvePackage(name) != null && !isPureOpenAppName(name)) return true
        }
        return COMPLEX.containsMatchIn(g) || g.length > 24 || g.contains("，") || g.contains(",")
    }

    /** True when planning can proceed without a full UI tree dump. */
    fun canPlanWithoutFullTree(goal: String): Boolean {
        val g = goal.trim()
        return isPureOpenAppRequest(g) || BACK.matches(g) || HOME.matches(g)
    }

    /**
     * After a successful action, may the Runtime end the task immediately?
     * Only for pure open / back / home — never for "打开X并做Y".
     */
    fun isTerminalOneShotGoal(goal: String): Boolean {
        val g = goal.trim()
        return isPureOpenAppRequest(g) || BACK.matches(g) || HOME.matches(g)
    }

    /** "打开微信" / "打开网易云音乐" — entire goal is just launching an app. */
    fun isPureOpenAppRequest(goal: String): Boolean {
        val g = goal.trim()
        val m = OPEN_APP.matchEntire(g) ?: return false
        val name = m.groupValues.getOrElse(2) { "" }.trim()
        return resolvePackage(name) != null && isPureOpenAppName(name)
    }

    /**
     * Remainder after stripping known app aliases should be empty / soft noise only.
     * "网易云音乐" → pure; "网易云播放我喜欢的音乐" → not pure.
     */
    fun isPureOpenAppName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith("com.")) {
            return name.matches(Regex("""com\.[a-zA-Z0-9_.]+"""))
        }
        var rest = name
        var matched = false
        for ((regex, _) in APP_ALIASES) {
            if (regex.containsMatchIn(rest)) {
                matched = true
                rest = rest.replace(regex, " ")
            }
        }
        if (!matched) return false
        rest = APP_NAME_NOISE.replace(rest, "").trim()
        // Drop leftover soft words repeatedly
        rest = rest.replace(Regex("""^(音乐|app|应用|软件|客户端)+$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return rest.isEmpty()
    }

    fun tryPlan(context: TaskContext): ActionPlan? {
        val goal = context.goal.trim()
        if (goal.isEmpty()) {
            return ActionPlan(
                actions = listOf(AgentAction(AgentAction.FAIL, target = "empty_goal")),
                reasoning = "rule:empty_goal",
            )
        }

        BACK.matchEntire(goal)?.let {
            return ActionPlan(
                actions = listOf(AgentAction(AgentAction.GLOBAL, target = "back")),
                reasoning = "rule:back",
            )
        }
        HOME.matchEntire(goal)?.let {
            return ActionPlan(
                actions = listOf(AgentAction(AgentAction.GLOBAL, target = "home")),
                reasoning = "rule:home",
            )
        }

        // Multi-step: open app → wait → click
        (OPEN_THEN_CLICK.find(goal) ?: OPEN_THEN_CLICK_SHORT.find(goal))?.let { m ->
            val appName = m.groupValues.getOrElse(1) { "" }.trim()
            val clickTarget = m.groupValues.getOrElse(2) { "" }.trim()
                .trim('"', '“', '”', '\'')
            val pkg = resolvePackage(appName)
            if (pkg != null && clickTarget.isNotBlank() && !COMPLEX.containsMatchIn(clickTarget)) {
                return ActionPlan(
                    actions = listOf(
                        AgentAction(
                            action = AgentAction.OPEN_APP,
                            target = pkg,
                            params = mapOf("alias" to appName),
                        ),
                        AgentAction(
                            action = AgentAction.WAIT_FOR_PAGE,
                            target = pkg,
                            params = mapOf("package" to pkg, "timeout_ms" to "4000"),
                        ),
                        AgentAction(
                            action = AgentAction.CLICK_NODE,
                            target = clickTarget,
                        ),
                    ),
                    reasoning = "rule:open_then_click:$pkg->$clickTarget",
                )
            }
        }

        // Pure open only — never swallow "打开网易云播放我喜欢的音乐" as open-only.
        if (isPureOpenAppRequest(goal)) {
            val name = OPEN_APP.matchEntire(goal)!!.groupValues[2].trim()
            val pkg = resolvePackage(name)!!
            return ActionPlan(
                actions = listOf(
                    AgentAction(
                        action = AgentAction.OPEN_APP,
                        target = pkg,
                        params = mapOf("alias" to name),
                    )
                ),
                reasoning = "rule:open_app:$pkg",
            )
        }

        // Direct package in goal: open com.xxx (exact-ish)
        Regex("""^(?:打开|open)\s+(com\.[a-zA-Z0-9_.]+)\s*$""", RegexOption.IGNORE_CASE)
            .find(goal)?.let { m ->
                val pkg = m.groupValues[1]
                return ActionPlan(
                    actions = listOf(AgentAction(AgentAction.OPEN_APP, target = pkg)),
                    reasoning = "rule:open_package:$pkg",
                )
            }

        CLICK.matchEntire(goal)?.let { m ->
            val target = m.groupValues.getOrElse(2) { "" }.trim()
                .trim('"', '“', '”', '\'')
            if (target.isNotBlank()) {
                val fromObs = context.observation?.fusedElements
                    ?.firstOrNull {
                        it.text.contains(target, ignoreCase = true) ||
                            it.contentDescription.contains(target, ignoreCase = true)
                    }
                return ActionPlan(
                    actions = listOf(
                        AgentAction(
                            action = AgentAction.CLICK_NODE,
                            target = target,
                            params = buildMap {
                                if (fromObs != null) {
                                    put("x", fromObs.x.toString())
                                    put("y", fromObs.y.toString())
                                    if (fromObs.viewId.isNotBlank()) put("view_id", fromObs.viewId)
                                }
                            },
                        )
                    ),
                    reasoning = if (fromObs != null) "rule:click_matched" else "rule:click_text",
                )
            }
        }

        return null
    }

    fun resolvePackage(alias: String): String? {
        if (alias.isBlank()) return null
        if (alias.startsWith("com.")) {
            val pkg = alias.trim().substringBefore(' ')
            return pkg.takeIf { it.matches(Regex("""com\.[a-zA-Z0-9_.]+""")) }
        }
        val lower = alias.lowercase()
        for ((regex, pkg) in APP_ALIASES) {
            if (regex.containsMatchIn(lower)) return pkg
        }
        return null
    }
}
