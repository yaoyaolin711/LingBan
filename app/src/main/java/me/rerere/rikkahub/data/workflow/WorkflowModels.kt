package me.rerere.rikkahub.data.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.model.InjectionPosition
import kotlin.uuid.Uuid

const val WORKFLOW_SCHEMA_VERSION = 1
const val WORKFLOW_FILE_SUFFIX = ".workflow.json"

@Serializable
enum class WorkflowPriority {
    @SerialName("builtin_first")
    BuiltinFirst,

    @SerialName("workflow_first")
    WorkflowFirst,

    @SerialName("coexist")
    Coexist,
}

@Serializable
enum class WorkflowTriggerMode {
    @SerialName("on_user_message")
    OnUserMessage,
}

@Serializable
enum class WorkflowMatchType {
    @SerialName("always")
    Always,

    @SerialName("keyword")
    Keyword,

    @SerialName("regex")
    Regex,
}

@Serializable
enum class WorkflowConflictDomain {
    @SerialName("device_agent_route")
    DeviceAgentRoute,

    @SerialName("prompt_injection")
    PromptInjection,

    @SerialName("auto_context_summary")
    AutoContextSummary,

    @SerialName("memory_tools")
    MemoryTools,
}

@Serializable
data class WorkflowTrigger(
    val mode: WorkflowTriggerMode = WorkflowTriggerMode.OnUserMessage,
    val match: WorkflowMatchType = WorkflowMatchType.Always,
    val patterns: List<String> = emptyList(),
    val manualRunnable: Boolean = true,
)

@Serializable
data class WorkflowConflicts(
    val defaultPriority: WorkflowPriority = WorkflowPriority.BuiltinFirst,
    val with: Map<WorkflowConflictDomain, WorkflowPriority> = emptyMap(),
)

@Serializable
sealed class WorkflowStep {
    abstract val id: String

    @Serializable
    @SerialName("inject_prompt")
    data class InjectPrompt(
        override val id: String = Uuid.random().toString(),
        val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        val content: String = "",
        val injectDepth: Int = 4,
    ) : WorkflowStep()

    @Serializable
    @SerialName("hint_tools")
    data class HintTools(
        override val id: String = Uuid.random().toString(),
        val toolNames: List<String> = emptyList(),
    ) : WorkflowStep()

    @Serializable
    @SerialName("run_skill")
    data class RunSkill(
        override val id: String = Uuid.random().toString(),
        val skillName: String = "",
    ) : WorkflowStep()
}

@Serializable
data class WorkflowDefinition(
    val schemaVersion: Int = WORKFLOW_SCHEMA_VERSION,
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val author: String = "",
    val trigger: WorkflowTrigger = WorkflowTrigger(),
    val steps: List<WorkflowStep> = emptyList(),
    val conflicts: WorkflowConflicts = WorkflowConflicts(),
)

/** Resolved contributions for one generation turn. */
data class WorkflowRuntimeBundle(
    val matchedWorkflowIds: List<Uuid> = emptyList(),
    val injectPrompts: List<WorkflowStep.InjectPrompt> = emptyList(),
    val skillInjections: List<Pair<String, String>> = emptyList(), // skillName to body
    val hintedToolNames: List<String> = emptyList(),
    val injectionSortPriority: Int = 0,
    val skipBecauseDeviceRoute: Boolean = false,
)
