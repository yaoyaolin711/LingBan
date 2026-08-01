package me.rerere.rikkahub.data.workflow

import me.rerere.rikkahub.data.model.Assistant
import kotlin.uuid.Uuid

object WorkflowScheduler {

    fun resolveDomainPriority(
        assistant: Assistant,
        workflow: WorkflowDefinition,
        domain: WorkflowConflictDomain,
    ): WorkflowPriority {
        workflow.conflicts.with[domain]?.let { return it }
        // File defaultPriority overrides when not the schema default; otherwise use assistant setting.
        return if (workflow.conflicts.defaultPriority != WorkflowPriority.BuiltinFirst) {
            workflow.conflicts.defaultPriority
        } else {
            assistant.workflowPriority
        }
    }

    fun resolveEffectivePriority(
        assistant: Assistant,
        matched: List<WorkflowDefinition>,
        domain: WorkflowConflictDomain,
    ): WorkflowPriority {
        if (matched.isEmpty()) return assistant.workflowPriority
        val priorities = matched.map { resolveDomainPriority(assistant, it, domain) }
        return when {
            priorities.any { it == WorkflowPriority.WorkflowFirst } -> WorkflowPriority.WorkflowFirst
            priorities.any { it == WorkflowPriority.Coexist } -> WorkflowPriority.Coexist
            else -> WorkflowPriority.BuiltinFirst
        }
    }

    fun matchWorkflows(
        assistant: Assistant,
        all: List<WorkflowDefinition>,
        userText: String,
        forcedWorkflowId: Uuid? = null,
    ): List<WorkflowDefinition> {
        if (forcedWorkflowId != null) {
            return all.filter { it.id == forcedWorkflowId && it.id in assistant.enabledWorkflowIds }
                .ifEmpty { all.filter { it.id == forcedWorkflowId } }
        }
        if (assistant.enabledWorkflowIds.isEmpty()) return emptyList()
        val enabled = all.filter { it.id in assistant.enabledWorkflowIds }
        return enabled.filter { matchesTrigger(it, userText) }
    }

    fun matchesTrigger(workflow: WorkflowDefinition, userText: String): Boolean {
        if (workflow.trigger.mode != WorkflowTriggerMode.OnUserMessage) return false
        val text = userText.trim()
        return when (workflow.trigger.match) {
            WorkflowMatchType.Always -> true
            WorkflowMatchType.Keyword -> {
                if (text.isEmpty()) return false
                workflow.trigger.patterns.any { pattern ->
                    pattern.isNotBlank() && text.contains(pattern, ignoreCase = true)
                }
            }
            WorkflowMatchType.Regex -> {
                if (text.isEmpty()) return false
                workflow.trigger.patterns.any { pattern ->
                    runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }
                        .getOrDefault(false)
                }
            }
        }
    }

    /**
     * Build runtime contributions for the current turn.
     *
     * @param deviceRouteTaken true when TaskRouter already committed to a device task.
     */
    fun buildRuntimeBundle(
        assistant: Assistant,
        matched: List<WorkflowDefinition>,
        readSkillBody: (String) -> String?,
        deviceRouteTaken: Boolean,
    ): WorkflowRuntimeBundle {
        if (matched.isEmpty()) return WorkflowRuntimeBundle()

        val devicePriority = resolveEffectivePriority(
            assistant = assistant,
            matched = matched,
            domain = WorkflowConflictDomain.DeviceAgentRoute,
        )

        if (deviceRouteTaken && devicePriority == WorkflowPriority.BuiltinFirst) {
            return WorkflowRuntimeBundle(
                matchedWorkflowIds = matched.map { it.id },
                skipBecauseDeviceRoute = true,
            )
        }

        val injectionPriority = when (
            resolveEffectivePriority(assistant, matched, WorkflowConflictDomain.PromptInjection)
        ) {
            WorkflowPriority.WorkflowFirst -> 1_000
            WorkflowPriority.Coexist -> 0
            WorkflowPriority.BuiltinFirst -> -1_000
        }

        val injects = mutableListOf<WorkflowStep.InjectPrompt>()
        val skills = mutableListOf<Pair<String, String>>()
        val hints = linkedSetOf<String>()

        matched.forEach { wf ->
            wf.steps.forEach { step ->
                when (step) {
                    is WorkflowStep.InjectPrompt -> injects += step
                    is WorkflowStep.HintTools -> hints += step.toolNames.filter { it.isNotBlank() }
                    is WorkflowStep.RunSkill -> {
                        if (step.skillName in assistant.enabledSkills) {
                            readSkillBody(step.skillName)?.takeIf { it.isNotBlank() }?.let { body ->
                                skills += step.skillName to body
                            }
                        }
                    }
                }
            }
        }

        return WorkflowRuntimeBundle(
            matchedWorkflowIds = matched.map { it.id },
            injectPrompts = injects,
            skillInjections = skills,
            hintedToolNames = hints.toList(),
            injectionSortPriority = injectionPriority,
            skipBecauseDeviceRoute = false,
        )
    }
}
