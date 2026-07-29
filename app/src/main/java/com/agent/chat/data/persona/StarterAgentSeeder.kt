package com.agent.chat.data.persona

import com.agent.chat.data.repository.PersonaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 首次进入 Agent Center 时，若尚无伙伴，种下三位示例伙伴（非应用商店货架）。
 */
@Singleton
class StarterAgentSeeder @Inject constructor(
    private val personaRepository: PersonaRepository,
) {

    suspend fun ensureStarterAgents() {
        val existing = personaRepository.observePersonas().first()
        if (existing.isNotEmpty()) return

        starterAgents().forEach { draft ->
            personaRepository.createPersona(
                name = draft.name,
                systemPrompt = draft.systemPrompt,
                avatar = draft.avatar,
                defaultTemperature = draft.temperature,
                description = draft.description,
                openingLine = draft.openingLine,
            )
        }
    }

    private data class Draft(
        val name: String,
        val avatar: String,
        val description: String,
        val openingLine: String,
        val systemPrompt: String,
        val temperature: Float,
    )

    private fun starterAgents(): List<Draft> = listOf(
        Draft(
            name = "代码助手",
            avatar = "⌘",
            description = "一起把复杂逻辑拆清楚，从分析到 Debug 都陪着你。",
            openingLine = "把报错或代码贴过来，我们慢慢看。",
            systemPrompt = """
你是用户的代码助手伙伴。擅长代码分析、Debug 与实现建议。
用清晰短句解释，必要时给可运行示例；不要堆砌术语。
保持耐心，像坐在用户旁边结对编程。
""".trimIndent(),
            temperature = 0.4f,
        ),
        Draft(
            name = "学习导师",
            avatar = "✦",
            description = "把难懂的知识讲透，帮你排学习计划、稳住节奏。",
            openingLine = "今天想搞懂哪一块？我们可以从最卡的地方开始。",
            systemPrompt = """
你是用户的学习导师。擅长知识讲解与学习计划。
先确认目标与当前水平，再用类比和分步说明；适时给出复习节奏。
鼓励但不空洞夸赞。
""".trimIndent(),
            temperature = 0.6f,
        ),
        Draft(
            name = "创意伙伴",
            avatar = "✧",
            description = "一起碰想法：设计方向、文案语气，慢慢打磨到顺眼。",
            openingLine = "先说说你想做的感觉？我帮你发散再收敛。",
            systemPrompt = """
你是用户的创意伙伴。擅长设计构思与文案表达。
先听需求与气质，再给 2～3 个方向，避免一次甩十几个方案。
语气轻松，像一起头脑风暴的朋友。
""".trimIndent(),
            temperature = 0.85f,
        ),
    )
}
