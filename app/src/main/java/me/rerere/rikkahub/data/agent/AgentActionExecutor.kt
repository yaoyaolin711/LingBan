package me.rerere.rikkahub.data.agent

import android.util.Log
import me.rerere.rikkahub.data.accessibility.PerceptionLevel
import me.rerere.rikkahub.data.accessibility.PerceptionRequest
import me.rerere.rikkahub.data.accessibility.TieredPerceptionEngine
import me.rerere.rikkahub.data.accessibility.UISnapshot
import me.rerere.rikkahub.data.agent.capability.AgentCapability

/**
 * Executes [AgentAction]s via [AgentCapability] → [PhoneControlCore].
 * Does not replace DeviceControlTools OCR packaging; SEE_SCREEN stays L1-light here.
 */
interface AgentActionExecutor {
    fun perceive(): UISnapshot = perceive(maxNodes = 120)

    fun perceive(maxNodes: Int): UISnapshot

    fun perceiveLight(): UISnapshot = perceive(maxNodes = 48)

    suspend fun execute(action: AgentAction): ActionExecuteResult

    fun verify(
        goal: String,
        snapshot: UISnapshot,
        lastAction: AgentAction?,
        lastResult: ActionExecuteResult?,
    ): VerifyResult
}

class AccessibilityAgentActionExecutor(
    private val capability: AgentCapability,
) : AgentActionExecutor {

    companion object {
        private const val TAG = "AgentActionExec"
    }

    override fun perceive(): UISnapshot = perceive(maxNodes = 120)

    override fun perceive(maxNodes: Int): UISnapshot = capability.perceive(maxNodes)

    override fun perceiveLight(): UISnapshot = capability.perceiveLight()

    override suspend fun execute(action: AgentAction): ActionExecuteResult {
        return when (action.action) {
            AgentAction.DONE -> ActionExecuteResult(ok = true, message = "DONE")
            AgentAction.FAIL -> ActionExecuteResult(
                ok = false,
                message = action.target.ifBlank { "FAIL" },
            )

            AgentAction.DUMP_UI -> {
                val snap = perceive()
                ActionExecuteResult(
                    ok = true,
                    message = "dumped ${snap.nodeCount} nodes page=${snap.page}",
                    observationSummary = "page=${snap.page} pkg=${snap.packageName} nodes=${snap.nodeCount}",
                )
            }

            AgentAction.SEE_SCREEN -> {
                val waitMs = action.params["wait_ms"]?.toLongOrNull() ?: 0L
                if (waitMs > 0) {
                    capability.seeScreenLight(waitMs)
                }
                val forceOcr = action.params["ocr_mode"]?.equals("force", true) == true
                val engine = TieredPerceptionEngine.instance
                if (engine != null) {
                    val result = engine.observe(
                        PerceptionRequest(
                            maxLevel = if (forceOcr) PerceptionLevel.L2_OCR else PerceptionLevel.L1_A11Y,
                            afterAction = false,
                        )
                    )
                    ActionExecuteResult(
                        ok = true,
                        message = "tiered ${result.level} cache=${result.fromCache} reason=${result.reason}",
                        observationSummary = "level=${result.level} hash=${result.treeHash} " +
                            "fused=${result.observation.fusedElements.size} fromCache=${result.fromCache}",
                    )
                } else {
                    val light = capability.seeScreenLight(0L)
                    ActionExecuteResult(
                        ok = light.ok,
                        message = light.message,
                        observationSummary = light.message,
                    )
                }
            }

            AgentAction.CLICK_NODE -> {
                val text = action.target.ifBlank { action.params["text"].orEmpty() }
                val viewId = action.params["view_id"].orEmpty()
                val x = action.params["x"]?.toIntOrNull()
                val y = action.params["y"]?.toIntOrNull()
                val result = when {
                    viewId.isNotBlank() -> {
                        val byId = capability.click(viewId = viewId)
                        if (!byId.ok && x != null && y != null) capability.click(x = x, y = y) else byId
                    }
                    text.isNotBlank() -> {
                        val byText = capability.click(text = text)
                        if (!byText.ok && x != null && y != null) capability.click(x = x, y = y) else byText
                    }
                    x != null && y != null -> capability.click(x = x, y = y)
                    else -> return ActionExecuteResult(false, "CLICK_NODE requires target text, view_id, or x+y")
                }
                ActionExecuteResult(result.ok, result.message)
            }

            AgentAction.CLICK_XY -> {
                val x = action.params["x"]?.toIntOrNull()
                    ?: return ActionExecuteResult(false, "CLICK_XY requires x")
                val y = action.params["y"]?.toIntOrNull()
                    ?: return ActionExecuteResult(false, "CLICK_XY requires y")
                val result = capability.click(x = x, y = y)
                ActionExecuteResult(result.ok, result.message)
            }

            AgentAction.TYPE_TEXT -> {
                val text = action.target.ifBlank { action.params["text"].orEmpty() }
                if (text.isBlank()) return ActionExecuteResult(false, "TYPE_TEXT requires text")
                val append = action.params["append"]?.toBooleanStrictOrNull() == true
                val result = capability.type(text, append)
                ActionExecuteResult(result.ok, result.message)
            }

            AgentAction.SWIPE -> {
                val x1 = action.params["x1"]?.toIntOrNull()
                val y1 = action.params["y1"]?.toIntOrNull()
                val x2 = action.params["x2"]?.toIntOrNull()
                val y2 = action.params["y2"]?.toIntOrNull()
                if (x1 == null || y1 == null || x2 == null || y2 == null) {
                    return ActionExecuteResult(false, "SWIPE requires x1,y1,x2,y2")
                }
                val duration = action.params["duration_ms"]?.toLongOrNull() ?: 300L
                val result = capability.swipe(x1, y1, x2, y2, duration)
                ActionExecuteResult(result.ok, result.message)
            }

            AgentAction.GLOBAL -> {
                val name = action.target.ifBlank { action.params["name"].orEmpty() }
                if (name.isBlank()) return ActionExecuteResult(false, "GLOBAL requires target name")
                val result = capability.global(name)
                ActionExecuteResult(result.ok, result.message)
            }

            AgentAction.OPEN_APP -> {
                val pkg = action.target.ifBlank { action.params["package"].orEmpty() }
                if (pkg.isBlank()) return ActionExecuteResult(false, "OPEN_APP requires package")
                val result = capability.openApp(packageName = pkg, waitMs = 2500L)
                ActionExecuteResult(result.ok, result.message.ifBlank { "Launched $pkg" })
            }

            AgentAction.WAIT_FOR_TEXT -> {
                val text = action.target.ifBlank { action.params["text"].orEmpty() }
                if (text.isBlank()) return ActionExecuteResult(false, "WAIT_FOR_TEXT requires text")
                val timeout = action.params["timeout_ms"]?.toLongOrNull() ?: 5_000L
                val result = capability.waitForText(text, timeout)
                ActionExecuteResult(result.ok, result.message)
            }

            AgentAction.WAIT_FOR_PAGE -> {
                val timeout = action.params["timeout_ms"]?.toLongOrNull() ?: 5_000L
                val result = capability.waitForPage(
                    packageName = action.params["package"],
                    activityName = action.target.ifBlank { null } ?: action.params["activity"],
                    timeoutMs = timeout,
                )
                ActionExecuteResult(result.ok, result.message)
            }

            else -> {
                Log.w(TAG, "Unknown action ${action.action}")
                ActionExecuteResult(false, "Unknown action: ${action.action}")
            }
        }
    }

    override fun verify(
        goal: String,
        snapshot: UISnapshot,
        lastAction: AgentAction?,
        lastResult: ActionExecuteResult?,
    ): VerifyResult {
        when (lastAction?.action) {
            AgentAction.DONE -> return VerifyResult(success = true, message = "Planner signaled DONE")
            AgentAction.FAIL -> return VerifyResult(
                success = false,
                continueLoop = false,
                message = lastResult?.message ?: "Planner signaled FAIL",
            )
        }
        if (lastResult?.ok == false) {
            return VerifyResult(
                success = false,
                continueLoop = true,
                message = lastResult.message,
            )
        }
        return VerifyResult(
            success = false,
            continueLoop = true,
            message = "Continue: goal not verified yet",
        )
    }
}
