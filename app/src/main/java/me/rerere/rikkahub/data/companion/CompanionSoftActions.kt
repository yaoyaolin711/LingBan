package me.rerere.rikkahub.data.companion

import android.util.Log
import me.rerere.rikkahub.data.agent.AgentManager
import me.rerere.rikkahub.data.agent.ExecutionMode
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.companion.policy.CompanionDeviceGoals
import me.rerere.rikkahub.data.companion.policy.ProactiveAction
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.device.CompanionIntervention
import me.rerere.rikkahub.overlay.pet.CompanionPetHost
import me.rerere.rikkahub.service.SolaceAccessibilityService

private const val TAG = "CompanionSoftActions"

/**
 * 陪伴动作执行器：与 DeviceAssist / PhoneControl 同源原语，
 * **不**走 GenerationHandler / LLM tool-loop。
 *
 * - SoftNotify / OpenSolace → [CompanionIntervention]
 * - DeviceTask → [AgentManager] RULE 白名单目标，失败则降级 followUp
 */
class CompanionSoftActions(
    private val intervention: CompanionIntervention,
    private val petHost: CompanionPetHost,
    private val settingsStore: SettingsStore,
    private val agentManagerLazy: Lazy<AgentManager>,
) {

    suspend fun execute(action: ProactiveAction) {
        when (action) {
            is ProactiveAction.None -> Unit
            is ProactiveAction.SoftNotify -> executeSoftNotify(action)
            is ProactiveAction.OpenSolace -> executeOpenSolace(action)
            is ProactiveAction.DeviceTask -> executeDeviceTask(action)
        }
    }

    private fun executeSoftNotify(action: ProactiveAction.SoftNotify) {
        Log.d(TAG, "notify_user: ${action.title}")
        petHost.updateEmotion(action.emotion, statusText = action.title)
        petHost.showBubble(action.content)
        intervention.notifyUser(
            title = action.title,
            content = action.content,
        )
    }

    private suspend fun executeOpenSolace(action: ProactiveAction.OpenSolace) {
        Log.d(TAG, "open_solace: ${action.title} usageCare=${action.isUsageCare}")
        petHost.updateEmotion(action.emotion, statusText = action.title)
        val message = if (action.isUsageCare) {
            intervention.generateCareMessage(
                appName = action.appName.orEmpty(),
                packageName = action.packageName.orEmpty(),
                continuousMinutes = action.continuousMinutes,
                emotion = action.emotion,
            )
        } else {
            val reason = action.reason ?: run {
                Log.w(TAG, "OpenSolace missing reason for non-usage-care; skip")
                return
            }
            intervention.generateProactiveMessage(
                reason = reason,
                emotion = action.emotion,
            )
        }
        val conversationId = intervention.openSolaceWithMessage(
            message = message,
            title = action.title,
            useFullScreenIntent = action.useFullScreenIntent,
        )
        petHost.showBubble(message, conversationId = conversationId)
    }

    private suspend fun executeDeviceTask(action: ProactiveAction.DeviceTask) {
        petHost.updateEmotion(action.emotion, statusText = "控机中")
        val ran = runCatching { tryRunDeviceGoal(action.goal) }
            .onFailure { Log.w(TAG, "device task failed: ${action.goal}", it) }
            .getOrDefault(false)
        Log.i(TAG, "device task goal=${action.goal} ran=$ran → followUp")
        // Always deliver companion message / notify after attempt
        when (val follow = action.followUp) {
            is ProactiveAction.DeviceTask -> {
                // Nested device tasks not allowed — flatten to soft notify
                Log.w(TAG, "nested DeviceTask ignored")
            }
            else -> execute(follow)
        }
    }

    /**
     * @return true if RULE device goal was submitted successfully
     */
    private suspend fun tryRunDeviceGoal(goal: String): Boolean {
        val trimmed = goal.trim()
        if (!CompanionDeviceGoals.isAllowed(trimmed)) {
            Log.w(TAG, "goal not in whitelist: $trimmed")
            return false
        }
        val assistant = settingsStore.settingsFlow.value.getCurrentAssistant()
        if (!assistant.localTools.contains(LocalToolOption.PhoneControl)) {
            Log.d(TAG, "PhoneControl not enabled; skip device")
            return false
        }
        if (SolaceAccessibilityService.instance == null) {
            Log.d(TAG, "accessibility not connected; skip device")
            return false
        }
        val manager = runCatching { agentManagerLazy.value }
            .onFailure { Log.e(TAG, "AgentManager unavailable", it) }
            .getOrNull() ?: return false
        if (manager.isDeviceBusy) {
            Log.d(TAG, "device busy; skip")
            return false
        }
        val result = manager.submitDeviceTask(
            goal = trimmed,
            mode = ExecutionMode.RULE,
        )
        Log.i(TAG, "submitDeviceTask success=${result.success} summary=${result.summary}")
        return result.success
    }
}
