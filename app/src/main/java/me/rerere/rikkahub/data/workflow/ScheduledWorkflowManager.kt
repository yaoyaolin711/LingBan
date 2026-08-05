package me.rerere.rikkahub.data.workflow

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

class ScheduledWorkflowManager(
    private val settingsStore: SettingsStore,
    private val workflowManager: WorkflowManager,
    private val scheduler: ScheduledWorkflowScheduler,
) {
    fun listRules(settings: Settings = settingsStore.settingsFlow.value): List<ScheduledWorkflowRule> =
        settings.scheduledWorkflows.map { it.normalized() }.sortedBy { it.name.lowercase() }

    fun getRule(id: Uuid, settings: Settings = settingsStore.settingsFlow.value): ScheduledWorkflowRule? =
        listRules(settings).firstOrNull { it.id == id }

    fun getEligibleAssistants(
        workflowId: Uuid,
        settings: Settings = settingsStore.settingsFlow.value,
    ): List<Assistant> = settings.assistants.filter { workflowId in it.enabledWorkflowIds }

    suspend fun saveRule(rule: ScheduledWorkflowRule): ScheduledWorkflowRule {
        val normalized = normalizeAgainstSettings(rule, settingsStore.settingsFlow.value)
        ScheduledWorkflowRules.validate(normalized)
        settingsStore.update { settings ->
            val updated = settings.scheduledWorkflows.filterNot { it.id == normalized.id } + normalized
            settings.copy(scheduledWorkflows = updated)
        }
        syncScheduler()
        return normalized
    }

    suspend fun deleteRule(id: Uuid): Boolean {
        var changed = false
        settingsStore.update { settings ->
            val updated = settings.scheduledWorkflows.filterNot {
                val remove = it.id == id
                if (remove) changed = true
                remove
            }
            settings.copy(scheduledWorkflows = updated)
        }
        syncScheduler()
        return changed
    }

    suspend fun pruneInvalidRules() {
        settingsStore.update { settings ->
            val workflowIds = workflowManager.listWorkflows().mapTo(HashSet()) { it.id }
            val updated = settings.scheduledWorkflows.mapNotNull { rule ->
                if (rule.workflowId !in workflowIds) return@mapNotNull null
                val normalized = normalizeAgainstSettings(rule, settings)
                normalized.takeIf { it.activeAssistantIds().isNotEmpty() }
            }
            settings.copy(scheduledWorkflows = updated)
        }
        syncScheduler()
    }

    suspend fun syncScheduler() {
        scheduler.sync(settingsStore.settingsFlow.value.scheduledWorkflows.any { it.enabled })
    }

    private fun normalizeAgainstSettings(
        rule: ScheduledWorkflowRule,
        settings: Settings,
    ): ScheduledWorkflowRule {
        val eligible = getEligibleAssistants(rule.workflowId, settings)
            .mapTo(LinkedHashSet()) { it.id }
        val normalizedTargets = buildList {
            rule.targets.forEach { target ->
                if (target.assistantId in eligible) add(target)
            }
        }
        val expandedTargets = if (normalizedTargets.isEmpty()) {
            eligible.map { ScheduledWorkflowTargetAssistant(assistantId = it, enabled = true) }
        } else {
            normalizedTargets
        }
        val sanitized = rule.copy(targets = expandedTargets).normalized()
        return sanitized.copy(
            assistantPriority = sanitized.assistantPriority.filter { it in eligible }
                .ifEmpty { expandedTargets.map { it.assistantId } }
        ).normalized()
    }
}
