package me.rerere.rikkahub.data.workflow

import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class WorkflowSchedulerTest {

    private val alwaysWorkflow = WorkflowDefinition(
        id = Uuid.parse("11111111-1111-1111-1111-111111111111"),
        name = "always",
        trigger = WorkflowTrigger(match = WorkflowMatchType.Always),
        steps = listOf(
            WorkflowStep.InjectPrompt(id = "s1", content = "hello"),
            WorkflowStep.HintTools(id = "s2", toolNames = listOf("search_web")),
        ),
    )

    private val keywordWorkflow = WorkflowDefinition(
        id = Uuid.parse("22222222-2222-2222-2222-222222222222"),
        name = "keyword",
        trigger = WorkflowTrigger(
            match = WorkflowMatchType.Keyword,
            patterns = listOf("brief", "简报"),
        ),
        steps = listOf(WorkflowStep.InjectPrompt(id = "k1", content = "brief")),
    )

    @Test
    fun matchWorkflows_returnsEmpty_whenAssistantHasNoBindings() {
        val assistant = Assistant(enabledWorkflowIds = emptySet())
        val matched = WorkflowScheduler.matchWorkflows(
            assistant = assistant,
            all = listOf(alwaysWorkflow),
            userText = "anything",
        )
        assertTrue(matched.isEmpty())
    }

    @Test
    fun matchWorkflows_filtersByEnabledIds() {
        val assistant = Assistant(enabledWorkflowIds = setOf(keywordWorkflow.id))
        val matched = WorkflowScheduler.matchWorkflows(
            assistant = assistant,
            all = listOf(alwaysWorkflow, keywordWorkflow),
            userText = "please give me a brief",
        )
        assertEquals(listOf(keywordWorkflow.id), matched.map { it.id })
    }

    @Test
    fun matchesTrigger_keywordAndRegex() {
        assertTrue(WorkflowScheduler.matchesTrigger(keywordWorkflow, "今日简报"))
        assertFalse(WorkflowScheduler.matchesTrigger(keywordWorkflow, "hello world"))

        val regexWf = keywordWorkflow.copy(
            trigger = WorkflowTrigger(
                match = WorkflowMatchType.Regex,
                patterns = listOf("""^brief\s+\d+$"""),
            )
        )
        assertTrue(WorkflowScheduler.matchesTrigger(regexWf, "brief 12"))
        assertFalse(WorkflowScheduler.matchesTrigger(regexWf, "brief"))
    }

    @Test
    fun buildRuntimeBundle_skipsWhenDeviceRouteTaken_andBuiltinFirst() {
        val assistant = Assistant(
            enabledWorkflowIds = setOf(alwaysWorkflow.id),
            workflowPriority = WorkflowPriority.BuiltinFirst,
        )
        val bundle = WorkflowScheduler.buildRuntimeBundle(
            assistant = assistant,
            matched = listOf(alwaysWorkflow),
            readSkillBody = { null },
            deviceRouteTaken = true,
        )
        assertTrue(bundle.skipBecauseDeviceRoute)
        assertTrue(bundle.injectPrompts.isEmpty())
        assertTrue(bundle.hintedToolNames.isEmpty())
    }

    @Test
    fun buildRuntimeBundle_appliesSteps_whenChatRoute() {
        val assistant = Assistant(
            enabledWorkflowIds = setOf(alwaysWorkflow.id),
            workflowPriority = WorkflowPriority.WorkflowFirst,
            enabledSkills = setOf("daily-brief"),
        )
        val withSkill = alwaysWorkflow.copy(
            steps = alwaysWorkflow.steps + WorkflowStep.RunSkill(id = "s3", skillName = "daily-brief"),
        )
        val bundle = WorkflowScheduler.buildRuntimeBundle(
            assistant = assistant,
            matched = listOf(withSkill),
            readSkillBody = { name -> if (name == "daily-brief") "# Daily brief body" else null },
            deviceRouteTaken = false,
        )
        assertFalse(bundle.skipBecauseDeviceRoute)
        assertEquals(1, bundle.injectPrompts.size)
        assertEquals(listOf("search_web"), bundle.hintedToolNames)
        assertEquals(1, bundle.skillInjections.size)
        assertEquals(1_000, bundle.injectionSortPriority)
    }

    @Test
    fun resolveDomainPriority_withOverrideBeatsAssistantDefault() {
        val assistant = Assistant(workflowPriority = WorkflowPriority.BuiltinFirst)
        val wf = alwaysWorkflow.copy(
            conflicts = WorkflowConflicts(
                defaultPriority = WorkflowPriority.BuiltinFirst,
                with = mapOf(WorkflowConflictDomain.DeviceAgentRoute to WorkflowPriority.WorkflowFirst),
            )
        )
        assertEquals(
            WorkflowPriority.WorkflowFirst,
            WorkflowScheduler.resolveDomainPriority(assistant, wf, WorkflowConflictDomain.DeviceAgentRoute),
        )
        assertEquals(
            WorkflowPriority.BuiltinFirst,
            WorkflowScheduler.resolveDomainPriority(assistant, wf, WorkflowConflictDomain.PromptInjection),
        )
    }

    @Test
    fun forcedWorkflowIgnoresMatchPatterns() {
        val assistant = Assistant(enabledWorkflowIds = setOf(keywordWorkflow.id))
        val matched = WorkflowScheduler.matchWorkflows(
            assistant = assistant,
            all = listOf(keywordWorkflow),
            userText = "no match here",
            forcedWorkflowId = keywordWorkflow.id,
        )
        assertEquals(1, matched.size)
    }
}

class WorkflowSchemaValidationTest {

    @Test
    fun validateSchema_acceptsValidDefinition() {
        val def = WorkflowDefinition(
            name = "ok",
            steps = listOf(
                WorkflowStep.InjectPrompt(content = "x", position = InjectionPosition.AFTER_SYSTEM_PROMPT),
                WorkflowStep.HintTools(toolNames = listOf("search_web")),
                WorkflowStep.RunSkill(skillName = "skill-a"),
            ),
        )
        validateWorkflowSchema(def)
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateSchema_rejectsBlankInjectContent() {
        validateWorkflowSchema(
            WorkflowDefinition(
                name = "bad",
                steps = listOf(WorkflowStep.InjectPrompt(content = "  ")),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateSchema_rejectsKeywordWithoutPatterns() {
        validateWorkflowSchema(
            WorkflowDefinition(
                name = "bad",
                trigger = WorkflowTrigger(match = WorkflowMatchType.Keyword, patterns = emptyList()),
                steps = listOf(WorkflowStep.InjectPrompt(content = "x")),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateSchema_rejectsUnsupportedSchemaVersion() {
        validateWorkflowSchema(
            WorkflowDefinition(
                schemaVersion = 99,
                name = "bad",
                steps = listOf(WorkflowStep.InjectPrompt(content = "x")),
            )
        )
    }
}
