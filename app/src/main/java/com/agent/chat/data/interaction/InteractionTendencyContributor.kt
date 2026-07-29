package com.agent.chat.data.interaction

import com.agent.chat.data.runtime.BehaviorSignalSpace
import com.agent.chat.domain.model.InteractionTendencyWeights

/**
 * 将互动倾向权重叠加到 Runtime 信号空间（倾向增强，非强制覆盖）。
 */
internal object InteractionTendencyContributor {

    fun apply(space: BehaviorSignalSpace, weights: InteractionTendencyWeights): BehaviorSignalSpace {
        if (!weights.anyActive() && weights.romantic == 0f && weights.flirting == 0f &&
            weights.intimate == 0f && weights.roleplay == 0f
        ) {
            return space
        }

        val boost = BehaviorSignalSpace(
            toneWarm = weights.romantic * 0.22f + weights.intimate * 0.12f,
            toneCaring = weights.romantic * 0.12f,
            tonePlayful = weights.flirting * 0.28f,
            emotionWarm = weights.romantic * 0.18f + weights.intimate * 0.15f,
            emotionExpressive = weights.intimate * 0.2f,
            focusRoleplay = weights.roleplay * 0.55f,
            humor = 0.5f + weights.flirting * 0.15f,
        )

        return space.addScaled(boost, 1f).clamped()
    }
}
