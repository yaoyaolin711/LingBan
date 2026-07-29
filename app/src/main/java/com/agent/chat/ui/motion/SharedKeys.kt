package com.agent.chat.ui.motion

/** 跨页共享元素 key，保持克制、可预期。 */
object SharedKeys {
    const val AI_ORB = "shared_ai_orb"
    const val HOME_GREETING = "shared_home_greeting"

    fun agentAvatar(personaId: String) = "agent_avatar_$personaId"
    fun agentCard(personaId: String) = "agent_card_$personaId"
}
