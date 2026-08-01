package me.rerere.rikkahub.data.agent

import me.rerere.rikkahub.data.accessibility.UISnapshot

/**
 * Default verifier:
 * - Click: page change / expected text (new message) / loading gone
 * - Input: focused/editable EditText content matches
 * - Page: package / activity / UI node presence
 *
 * Expectations are read from [AgentAction.params] (and [AgentAction.target] when relevant).
 *
 * Supported params:
 * - expect_page_change=true|false
 * - expect_text / expect_message — text that should appear after action
 * - expect_loading_gone=true — loading/progress indicators should disappear
 * - expect_package / expect_activity
 * - expect_node_text / expect_view_id
 * - expect_edit_text — EditText value (TYPE_TEXT defaults to action.target)
 * - max_retries
 */
class DefaultActionVerifier : ActionVerifier {

    override suspend fun verify(ctx: VerifyContext): ActionVerification {
        val action = ctx.action
        when (action.action) {
            AgentAction.DONE -> return ActionVerification(
                status = VerificationStatus.SUCCESS,
                message = "DONE",
                attempt = ctx.attempt,
            )
            AgentAction.FAIL -> return ActionVerification(
                status = VerificationStatus.FAILED,
                message = ctx.executeResult.message.ifBlank { action.target },
                attempt = ctx.attempt,
            )
        }

        if (!ctx.executeResult.ok) {
            return ActionVerification(
                status = VerificationStatus.RETRY,
                message = "Execute failed: ${ctx.executeResult.message}",
                checks = listOf(CheckResult("execute_ok", false, ctx.executeResult.message)),
                attempt = ctx.attempt,
            )
        }

        val checks = when (action.action) {
            AgentAction.CLICK_NODE, AgentAction.CLICK_XY -> verifyClick(ctx)
            AgentAction.TYPE_TEXT -> verifyInput(ctx)
            AgentAction.OPEN_APP, AgentAction.WAIT_FOR_PAGE, AgentAction.GLOBAL -> verifyPage(ctx)
            AgentAction.SEE_SCREEN, AgentAction.DUMP_UI -> listOf(
                CheckResult("perceive", true, ctx.executeResult.observationSummary.orEmpty()),
            )
            AgentAction.WAIT_FOR_TEXT -> verifyTextPresent(
                ctx.after,
                action.target.ifBlank { action.params["text"].orEmpty() },
                "wait_text",
            ).let { listOf(it) }
            AgentAction.SWIPE -> verifySwipe(ctx)
            else -> verifyPage(ctx).ifEmpty {
                listOf(CheckResult("noop", true, "No specific checks for ${action.action}"))
            }
        }

        val allPassed = checks.all { it.passed }
        val status = when {
            allPassed -> VerificationStatus.SUCCESS
            ctx.attempt <= ctx.maxRetries -> VerificationStatus.RETRY
            else -> VerificationStatus.FAILED
        }
        return ActionVerification(
            status = status,
            message = summarize(checks, status),
            checks = checks,
            attempt = ctx.attempt,
        )
    }

    // region click

    private fun verifyClick(ctx: VerifyContext): List<CheckResult> {
        val p = ctx.action.params
        val checks = ArrayList<CheckResult>()

        val expectPageChange = p["expect_page_change"]?.toBooleanStrictOrNull()
        val expectText = p["expect_text"]?.ifBlank { null }
            ?: p["expect_message"]?.ifBlank { null }
        val expectLoadingGone = p["expect_loading_gone"]?.toBooleanStrictOrNull() == true

        val hasExplicit = expectPageChange != null || expectText != null || expectLoadingGone ||
            p.containsKey("expect_package") || p.containsKey("expect_activity") ||
            p.containsKey("expect_node_text") || p.containsKey("expect_view_id")

        if (expectPageChange == true || (!hasExplicit && expectPageChange != false)) {
            // Default click heuristic: page OR tree fingerprint should change.
            if (expectPageChange == true) {
                checks += CheckResult(
                    name = "page_changed",
                    passed = pageChanged(ctx.before, ctx.after),
                    detail = "before=${ctx.before.page} after=${ctx.after.page}",
                )
            } else if (!hasExplicit) {
                checks += CheckResult(
                    name = "ui_changed",
                    passed = pageChanged(ctx.before, ctx.after) || uiFingerprint(ctx.before) != uiFingerprint(ctx.after),
                    detail = "page ${ctx.before.page}→${ctx.after.page} nodes ${ctx.before.nodeCount}→${ctx.after.nodeCount}",
                )
            }
        }

        if (expectText != null) {
            checks += verifyTextPresent(ctx.after, expectText, "new_message_or_text")
        }

        if (expectLoadingGone) {
            checks += CheckResult(
                name = "loading_gone",
                passed = !hasLoadingIndicator(ctx.after),
                detail = if (hasLoadingIndicator(ctx.after)) "loading still visible" else "no loading",
            )
        }

        checks += verifyPageExpectations(ctx)

        if (checks.isEmpty()) {
            checks += CheckResult("execute_ok", true, "click with no extra expects")
        }
        return checks
    }

    // endregion

    // region input

    private fun verifyInput(ctx: VerifyContext): List<CheckResult> {
        val expected = ctx.action.params["expect_edit_text"]
            ?.ifBlank { null }
            ?: ctx.action.target.ifBlank { ctx.action.params["text"].orEmpty() }
        if (expected.isBlank()) {
            return listOf(CheckResult("edit_text", true, "no expected text"))
        }
        val matched = findEditableText(ctx.after).any { value ->
            value.contains(expected) || expected.contains(value) && value.isNotBlank()
        }
        return listOf(
            CheckResult(
                name = "edit_text_match",
                passed = matched,
                detail = "expected='$expected' found=${findEditableText(ctx.after).take(3)}",
            )
        ) + verifyPageExpectations(ctx)
    }

    // endregion

    // region page

    private fun verifyPage(ctx: VerifyContext): List<CheckResult> {
        val checks = verifyPageExpectations(ctx).toMutableList()
        if (ctx.action.action == AgentAction.OPEN_APP) {
            val pkg = ctx.action.target.ifBlank { ctx.action.params["package"].orEmpty() }
            if (pkg.isNotBlank() && checks.none { it.name == "package" }) {
                checks += CheckResult(
                    name = "package",
                    passed = ctx.after.packageName.equals(pkg, ignoreCase = true),
                    detail = "expected=$pkg actual=${ctx.after.packageName}",
                )
            }
        }
        if (checks.isEmpty()) {
            checks += CheckResult(
                name = "page_ok",
                passed = ctx.after.packageName.isNotBlank() || ctx.after.nodeCount >= 0,
                detail = "pkg=${ctx.after.packageName} page=${ctx.after.page}",
            )
        }
        return checks
    }

    private fun verifyPageExpectations(ctx: VerifyContext): List<CheckResult> {
        val p = ctx.action.params
        val checks = ArrayList<CheckResult>()
        p["expect_package"]?.takeIf { it.isNotBlank() }?.let { pkg ->
            checks += CheckResult(
                name = "package",
                passed = ctx.after.packageName.equals(pkg, ignoreCase = true),
                detail = "expected=$pkg actual=${ctx.after.packageName}",
            )
        }
        p["expect_activity"]?.takeIf { it.isNotBlank() }?.let { act ->
            checks += CheckResult(
                name = "activity",
                passed = ctx.after.page.equals(act, ignoreCase = true) ||
                    ctx.after.page.endsWith(act, ignoreCase = true),
                detail = "expected=$act actual=${ctx.after.page}",
            )
        }
        p["expect_node_text"]?.takeIf { it.isNotBlank() }?.let { text ->
            checks += verifyTextPresent(ctx.after, text, "node_text")
        }
        p["expect_view_id"]?.takeIf { it.isNotBlank() }?.let { id ->
            val found = ctx.after.flattenNodes().any { n ->
                n.viewId == id || n.viewId.endsWith("/$id") || n.viewId.substringAfterLast('/') == id
            }
            checks += CheckResult("view_id", found, "id=$id")
        }
        return checks
    }

    private fun verifySwipe(ctx: VerifyContext): List<CheckResult> {
        val expectText = ctx.action.params["expect_text"]
        return if (!expectText.isNullOrBlank()) {
            listOf(verifyTextPresent(ctx.after, expectText, "after_swipe_text"))
        } else {
            listOf(
                CheckResult(
                    name = "ui_changed_or_ok",
                    passed = uiFingerprint(ctx.before) != uiFingerprint(ctx.after) || ctx.executeResult.ok,
                    detail = "nodes ${ctx.before.nodeCount}→${ctx.after.nodeCount}",
                )
            )
        }
    }

    // endregion

    // region helpers

    private fun verifyTextPresent(snapshot: UISnapshot, text: String, name: String): CheckResult {
        if (text.isBlank()) return CheckResult(name, true, "empty text")
        val found = snapshot.flattenNodes().any { n ->
            n.text.contains(text, ignoreCase = true) ||
                n.contentDescription.contains(text, ignoreCase = true)
        }
        return CheckResult(name, found, "text='$text'")
    }

    private fun pageChanged(before: UISnapshot, after: UISnapshot): Boolean {
        if (before.page.isNotBlank() && after.page.isNotBlank() && before.page != after.page) return true
        if (before.packageName.isNotBlank() && after.packageName.isNotBlank() &&
            !before.packageName.equals(after.packageName, ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private fun uiFingerprint(snapshot: UISnapshot): String {
        val nodes = snapshot.flattenNodes().take(40)
        return buildString {
            append(snapshot.page).append('|').append(snapshot.packageName).append('|')
            append(snapshot.nodeCount).append('|')
            nodes.forEach { n ->
                append(n.nodeId).append(':')
                append(n.text.take(20)).append(':')
                append(n.viewId.substringAfterLast('/')).append(';')
            }
        }
    }

    private fun hasLoadingIndicator(snapshot: UISnapshot): Boolean {
        val keywords = listOf(
            "loading", "progress", "请稍候", "加载中", "正在加载", "稍等",
        )
        return snapshot.flattenNodes().any { n ->
            val cls = n.className
            if (cls.contains("ProgressBar", ignoreCase = true) ||
                cls.contains("ProgressIndicator", ignoreCase = true) ||
                cls.contains("CircularProgress", ignoreCase = true) ||
                cls.contains("Loading", ignoreCase = true)
            ) {
                return@any true
            }
            val label = (n.text + " " + n.contentDescription).lowercase()
            keywords.any { label.contains(it.lowercase()) }
        }
    }

    private fun findEditableText(snapshot: UISnapshot): List<String> {
        return snapshot.flattenNodes()
            .filter { it.editable || it.focused || it.className.contains("EditText", ignoreCase = true) }
            .map { it.text }
            .filter { it.isNotBlank() }
    }

    private fun summarize(checks: List<CheckResult>, status: VerificationStatus): String {
        val failed = checks.filter { !it.passed }.joinToString { "${it.name}:${it.detail}" }
        return when (status) {
            VerificationStatus.SUCCESS -> "checks_ok=${checks.size}"
            VerificationStatus.RETRY -> "retry: $failed"
            VerificationStatus.FAILED -> "failed: $failed"
        }
    }

    // endregion
}

/** Always-SUCCESS verifier for tests / passthrough. */
class PassThroughActionVerifier : ActionVerifier {
    override suspend fun verify(ctx: VerifyContext): ActionVerification {
        if (ctx.action.action == AgentAction.FAIL || !ctx.executeResult.ok) {
            return ActionVerification(
                status = if (ctx.attempt <= ctx.maxRetries && ctx.action.action != AgentAction.FAIL) {
                    VerificationStatus.RETRY
                } else {
                    VerificationStatus.FAILED
                },
                message = ctx.executeResult.message,
                attempt = ctx.attempt,
            )
        }
        return ActionVerification(
            status = VerificationStatus.SUCCESS,
            message = "passthrough",
            attempt = ctx.attempt,
        )
    }
}
