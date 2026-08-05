package me.rerere.rikkahub.data.groupchat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class HardGateTest {
    private val a = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val b = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val members = listOf(
        GroupMember(assistantId = a, displayName = "Alice"),
        GroupMember(assistantId = b, displayName = "Bob"),
    )
    private val policy = HardGatePolicy(maxChainDepth = 2, maxSpeakersPerUserTurn = 3)

    @Test
    fun `speaker budget exhausted forces end`() {
        val floor = FloorState(speakersThisUserTurn = 3, active = true)
        val verdict = HardGate.evaluate(
            GroupScheduleContext(
                mode = GroupChatMode.FREE_DISCUSSION,
                members = members,
                floor = floor,
                policy = policy,
                afterUserMessage = false,
            )
        )
        assertFalse(verdict.allowScheduling)
        assertEquals("speaker_budget_exhausted", verdict.forcedEndReason)
    }

    @Test
    fun `chain depth exhausted forces end`() {
        val floor = FloorState(
            chainDepth = 2,
            speakersThisUserTurn = 2,
            lastSpeakerId = a,
            active = true,
        )
        val verdict = HardGate.evaluate(
            GroupScheduleContext(
                mode = GroupChatMode.FREE_DISCUSSION,
                members = members,
                floor = floor,
                policy = policy,
                afterUserMessage = false,
            )
        )
        assertFalse(verdict.allowScheduling)
        assertEquals("chain_depth_exhausted", verdict.forcedEndReason)
    }

    @Test
    fun `cooldown excludes last speaker unless mentioned`() {
        val floor = FloorState(lastSpeakerId = a, active = true, speakersThisUserTurn = 1)
        val withoutMention = HardGate.evaluate(
            GroupScheduleContext(
                mode = GroupChatMode.FREE_DISCUSSION,
                members = members,
                floor = floor,
                policy = policy,
                latestMentions = emptyList(),
                afterUserMessage = false,
            )
        )
        assertTrue(withoutMention.allowScheduling)
        assertFalse(a in withoutMention.candidates)
        assertTrue(b in withoutMention.candidates)

        val withMention = HardGate.evaluate(
            GroupScheduleContext(
                mode = GroupChatMode.FREE_DISCUSSION,
                members = members,
                floor = floor,
                policy = policy,
                latestMentions = listOf(a),
                afterUserMessage = false,
            )
        )
        assertTrue(a in withMention.candidates)
    }

    @Test
    fun `mention_first without mention blocks AI chain`() {
        val floor = FloorState(speakersThisUserTurn = 1, lastSpeakerId = a, active = true)
        val verdict = HardGate.evaluate(
            GroupScheduleContext(
                mode = GroupChatMode.MENTION_FIRST,
                members = members,
                floor = floor,
                policy = policy,
                latestMentions = emptyList(),
                afterUserMessage = false,
            )
        )
        assertFalse(verdict.allowScheduling)
        assertEquals("no_candidates", verdict.forcedEndReason)
    }

    @Test
    fun `validateDecision rejects speaker outside candidates`() {
        val verdict = HardGateVerdict(allowScheduling = true, candidates = listOf(b))
        val validated = HardGate.validateDecision(
            SchedulerDecision(action = SchedulerAction.SPEAK, speakerId = a),
            verdict,
        )
        assertEquals(SchedulerAction.END_ROUND, validated.action)
        assertNull(validated.speakerId)
    }

    @Test
    fun `paused floor never schedules`() {
        val verdict = HardGate.evaluate(
            GroupScheduleContext(
                mode = GroupChatMode.FREE_DISCUSSION,
                members = members,
                floor = FloorState(active = false),
                policy = policy,
                afterUserMessage = true,
            )
        )
        assertFalse(verdict.allowScheduling)
        assertEquals("paused", verdict.forcedEndReason)
    }
}

class SoftSchedulerParseTest {
    @Test
    fun `parses speak json`() {
        val id = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val decision = parseSchedulerDecisionJson(
            """{"action":"speak","speakerId":"$id","reason":"ok","addressedTo":[]}"""
        )
        assertEquals(SchedulerAction.SPEAK, decision?.action)
        assertEquals(id, decision?.speakerId)
    }

    @Test
    fun `illegal json returns null`() {
        assertNull(parseSchedulerDecisionJson("not json"))
        assertNull(parseSchedulerDecisionJson(""))
    }

    @Test
    fun `fenced json is accepted`() {
        val decision = parseSchedulerDecisionJson(
            """
            ```json
            {"action":"end_round","speakerId":null,"reason":"done"}
            ```
            """.trimIndent()
        )
        assertEquals(SchedulerAction.END_ROUND, decision?.action)
    }
}

class GroupChatOrchestratorTest {
    private val a = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val b = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val members = listOf(
        GroupMember(assistantId = a, displayName = "Alice"),
        GroupMember(assistantId = b, displayName = "Bob"),
    )

    @Test
    fun `mutual at chain stops at maxChainDepth`() = runBlocking {
        val policy = HardGatePolicy(maxChainDepth = 2, maxSpeakersPerUserTurn = 10)
        // Always pick the other assistant to simulate mutual @ looping intent
        val scheduler = object : SoftScheduler {
            override suspend fun decide(
                context: GroupScheduleContext,
                candidates: List<Uuid>,
                transcript: List<GroupTranscriptLine>,
            ): SchedulerDecision {
                val pick = candidates.firstOrNull { it != context.floor.lastSpeakerId }
                    ?: candidates.first()
                return SchedulerDecision(action = SchedulerAction.SPEAK, speakerId = pick, reason = "loop")
            }
        }
        val orchestrator = GroupChatOrchestrator(scheduler, policy)
        var speakCount = 0
        val floor = orchestrator.runTurn(
            mode = GroupChatMode.FREE_DISCUSSION,
            members = members,
            initialFloor = FloorState().resetForUserTurn(),
            initialMentions = emptyList(),
            transcriptProvider = { emptyList() },
            onSpeak = { speakerId ->
                speakCount++
                // Always @ the other to try to continue the chain
                listOf(if (speakerId == a) b else a)
            },
        )
        assertFalse(floor.active)
        assertTrue("expected finite speaks, got $speakCount", speakCount in 1..3)
        assertTrue(speakCount <= policy.maxSpeakersPerUserTurn)
    }

    @Test
    fun `invalid scheduler json path ends via hard gate validate`() = runBlocking {
        val scheduler = object : SoftScheduler {
            override suspend fun decide(
                context: GroupScheduleContext,
                candidates: List<Uuid>,
                transcript: List<GroupTranscriptLine>,
            ): SchedulerDecision {
                // Pretend LLM returned garbage that became an illegal speaker
                return SchedulerDecision(
                    action = SchedulerAction.SPEAK,
                    speakerId = Uuid.random(),
                    reason = "hallucinated",
                )
            }
        }
        val orchestrator = GroupChatOrchestrator(scheduler, HardGatePolicy.DEFAULT)
        var spoke = false
        val floor = orchestrator.runTurn(
            mode = GroupChatMode.FREE_DISCUSSION,
            members = members,
            initialFloor = FloorState().resetForUserTurn(),
            initialMentions = emptyList(),
            transcriptProvider = { emptyList() },
            onSpeak = {
                spoke = true
                emptyList()
            },
        )
        assertFalse(spoke)
        assertFalse(floor.active)
    }
}

class GroupMentionsTest {
    @Test
    fun `parses at display names`() {
        val a = Uuid.parse("11111111-1111-1111-1111-111111111111")
        val b = Uuid.parse("22222222-2222-2222-2222-222222222222")
        val members = listOf(
            GroupMember(assistantId = a, displayName = "Alice"),
            GroupMember(assistantId = b, displayName = "Bob"),
        )
        assertEquals(listOf(a), parseGroupMentions("hey @Alice what do you think?", members))
        assertEquals(listOf(a, b), parseGroupMentions("@Alice and @Bob", members))
        assertTrue(parseGroupMentions("no mentions", members).isEmpty())
    }
}
