package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid

/**
 * Applies matched workflow inject_prompt / run_skill contributions.
 *
 * inject_prompt is applied both after the system prompt and as a near-turn user message
 * (BOTTOM_OF_CHAT), because many models soft-ignore buried system instructions.
 */
object WorkflowPromptTransformer : InputMessageTransformer {
    private const val TAG = "WorkflowPrompt"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<me.rerere.ai.ui.UIMessage>,
    ): List<me.rerere.ai.ui.UIMessage> {
        val bundle = ctx.workflowBundle ?: return messages
        if (bundle.skipBecauseDeviceRoute) {
            Log.i(TAG, "skipBecauseDeviceRoute matched=${bundle.matchedWorkflowIds}")
            return messages
        }
        if (bundle.injectPrompts.isEmpty() && bundle.skillInjections.isEmpty()) {
            return messages
        }

        Log.i(
            TAG,
            "apply injects=${bundle.injectPrompts.size} skills=${bundle.skillInjections.size} " +
                "matched=${bundle.matchedWorkflowIds} priority=${bundle.injectionSortPriority}"
        )

        val injections = buildList {
            bundle.injectPrompts.forEach { step ->
                val body = wrapInjectBody(step.content)
                // Keep configured position (usually after_system)
                add(
                    PromptInjection.ModeInjection(
                        id = Uuid.random(),
                        name = "workflow_inject:${step.id}",
                        enabled = true,
                        priority = bundle.injectionSortPriority,
                        position = step.position,
                        content = body,
                        injectDepth = step.injectDepth,
                        role = MessageRole.USER,
                    )
                )
                // Also place near the latest turn so models reliably see it
                if (step.position != InjectionPosition.BOTTOM_OF_CHAT &&
                    step.position != InjectionPosition.AT_DEPTH
                ) {
                    add(
                        PromptInjection.ModeInjection(
                            id = Uuid.random(),
                            name = "workflow_inject_near:${step.id}",
                            enabled = true,
                            priority = bundle.injectionSortPriority,
                            position = InjectionPosition.BOTTOM_OF_CHAT,
                            content = body,
                            injectDepth = 4,
                            role = MessageRole.USER,
                        )
                    )
                }
            }
            bundle.skillInjections.forEach { (skillName, skillBody) ->
                val content = "Skill `$skillName` instructions (from workflow):\n\n$skillBody"
                add(
                    PromptInjection.ModeInjection(
                        id = Uuid.random(),
                        name = "workflow_skill:$skillName",
                        enabled = true,
                        priority = bundle.injectionSortPriority,
                        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
                        content = content,
                        injectDepth = 4,
                        role = MessageRole.USER,
                    )
                )
                add(
                    PromptInjection.ModeInjection(
                        id = Uuid.random(),
                        name = "workflow_skill_near:$skillName",
                        enabled = true,
                        priority = bundle.injectionSortPriority,
                        position = InjectionPosition.BOTTOM_OF_CHAT,
                        content = content,
                        injectDepth = 4,
                        role = MessageRole.USER,
                    )
                )
            }
        }
        return applyInjections(messages, injections.groupBy { it.position })
    }

    private fun wrapInjectBody(content: String): String = buildString {
        appendLine("【工作流强制指令 / MANDATORY WORKFLOW RULE】")
        appendLine("以下规则优先于普通风格偏好；必须遵守：")
        appendLine(content.trim())
    }
}
