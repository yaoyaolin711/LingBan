package me.rerere.rikkahub.data.groupchat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

interface SoftScheduler {
    suspend fun decide(
        context: GroupScheduleContext,
        candidates: List<Uuid>,
        transcript: List<GroupTranscriptLine>,
    ): SchedulerDecision
}

@Serializable
data class GroupTranscriptLine(
    val speakerLabel: String,
    val text: String,
)

/**
 * Deterministic fallback / primary for mention-first and when LLM fails.
 * Prefer explicit mentions; otherwise pick first candidate after user message; else end.
 */
class RulesFallbackScheduler : SoftScheduler {
    override suspend fun decide(
        context: GroupScheduleContext,
        candidates: List<Uuid>,
        transcript: List<GroupTranscriptLine>,
    ): SchedulerDecision {
        if (candidates.isEmpty()) return SchedulerDecision.endRound("no_candidates")

        val mentions = context.latestMentions.filter { it in candidates }
        if (mentions.isNotEmpty()) {
            return SchedulerDecision(
                action = SchedulerAction.SPEAK,
                speakerId = mentions.first(),
                reason = "mentioned",
            )
        }

        if (context.afterUserMessage) {
            // Mention-first / free: one voluntary reply at most from rules
            return SchedulerDecision(
                action = SchedulerAction.SPEAK,
                speakerId = candidates.first(),
                reason = "rules_pick_first",
            )
        }

        // AI chain without mention: only in free discussion, and only if HardGate already allowed
        if (context.mode == GroupChatMode.FREE_DISCUSSION) {
            return SchedulerDecision(
                action = SchedulerAction.SPEAK,
                speakerId = candidates.first(),
                reason = "rules_free_chain",
            )
        }

        return SchedulerDecision.endRound("rules_end")
    }
}

private val schedulerJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
private data class SchedulerDecisionDto(
    val action: String = "end_round",
    val speakerId: String? = null,
    val reason: String = "",
    val addressedTo: List<String> = emptyList(),
)

fun parseSchedulerDecisionJson(raw: String): SchedulerDecision? {
    val trimmed = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val body = trimmed.substring(start, end + 1)
    return runCatching {
        val dto = schedulerJson.decodeFromString<SchedulerDecisionDto>(body)
        val action = when (dto.action.lowercase().replace('-', '_')) {
            "speak" -> SchedulerAction.SPEAK
            else -> SchedulerAction.END_ROUND
        }
        val speaker = dto.speakerId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        SchedulerDecision(
            action = action,
            speakerId = speaker,
            reason = dto.reason,
            addressedTo = dto.addressedTo,
        )
    }.getOrNull()
}

fun buildSchedulerPrompt(
    context: GroupScheduleContext,
    candidates: List<Uuid>,
    transcript: List<GroupTranscriptLine>,
): String {
    val memberLines = context.members.joinToString("\n") { m ->
        val mark = if (m.assistantId in candidates) "*" else " "
        "- [$mark] id=${m.assistantId} name=${m.displayName.ifBlank { m.assistantId.toString() }}"
    }
    val history = transcript.takeLast(12).joinToString("\n") { "${it.speakerLabel}: ${it.text}" }
    return """
You are the floor scheduler for a multi-assistant group chat.
Pick at most ONE next speaker from candidates marked *, or end the round.
Never invent ids. Prefer ending the round unless someone clearly should speak.
In mention_first mode without @, usually end after one reply.
In free_discussion, short AI chains are ok but do not continue forever.

Mode: ${context.mode.name}
Remaining speaker budget: ${context.floor.remainingSpeakerBudget(context.policy)}
Remaining chain depth: ${context.floor.remainingChainDepth(context.policy)}
After user message: ${context.afterUserMessage}

Members (* = eligible now):
$memberLines

Recent transcript:
$history

Respond with ONLY JSON:
{"action":"speak"|"end_round","speakerId":"<uuid or null>","reason":"short","addressedTo":[]}
""".trimIndent()
}
