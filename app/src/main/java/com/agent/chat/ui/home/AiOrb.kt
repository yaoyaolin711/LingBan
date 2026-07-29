package com.agent.chat.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agent.chat.ui.motion.scaleClickable
import com.agent.chat.ui.theme.OrbCore
import com.agent.chat.ui.theme.OrbDeep
import com.agent.chat.ui.theme.OrbHalo
import com.agent.chat.ui.theme.OrbMid
import com.agent.chat.ui.theme.OrbParticle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 空间感 AI 生命体（非机器人头像）。
 * Canvas 自绘，动画用 InfiniteTransition，目标 60fps。
 */
@Composable
fun AiOrb(
    state: AiOrbState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = 220.dp,
    entranceScale: Float = 1f,
    entranceAlpha: Float = 1f,
) {
    val infinite = rememberInfiniteTransition(label = "ai_orb")

    val breath = infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val thinkSpin = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "think_spin",
    )

    val speakPulse = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "speak_pulse",
    )

    val listenPulse = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "listen_pulse",
    )

    val particlePhase = infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_phase",
    )

    val scale = when (state) {
        AiOrbState.Idle -> breath.value
        AiOrbState.Listening -> 1f + 0.03f * sin(listenPulse.value * PI.toFloat())
        AiOrbState.Thinking -> 1f + 0.02f * sin(particlePhase.value * 2f)
        AiOrbState.Speaking -> 1f + 0.04f * sin(speakPulse.value * PI.toFloat())
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale * entranceScale
                scaleY = scale * entranceScale
                alpha = entranceAlpha
            }
            .then(
                if (onClick != null) {
                    Modifier.scaleClickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val baseR = this.size.minDimension * 0.28f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OrbHalo.copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = baseR * 2.4f,
                ),
                radius = baseR * 2.4f,
                center = Offset(cx, cy),
            )

            when (state) {
                AiOrbState.Idle -> drawHaloRings(cx, cy, baseR, alphaScale = 0.55f)
                AiOrbState.Listening -> {
                    drawListeningRipples(cx, cy, baseR, listenPulse.value)
                    drawHaloRings(cx, cy, baseR, alphaScale = 0.5f)
                }
                AiOrbState.Thinking -> {
                    drawParticles(
                        cx = cx,
                        cy = cy,
                        baseR = baseR,
                        phase = particlePhase.value,
                        spinDeg = thinkSpin.value,
                    )
                    drawHaloRings(cx, cy, baseR, alphaScale = 0.4f)
                }
                AiOrbState.Speaking -> {
                    drawSpeakingRings(cx, cy, baseR, speakPulse.value)
                    drawHaloRings(cx, cy, baseR, alphaScale = 0.7f)
                }
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OrbCore,
                        OrbMid,
                        OrbDeep.copy(alpha = 0.92f),
                    ),
                    center = Offset(cx - baseR * 0.18f, cy - baseR * 0.22f),
                    radius = baseR * 1.15f,
                ),
                radius = baseR,
                center = Offset(cx, cy),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                    center = Offset(cx - baseR * 0.28f, cy - baseR * 0.32f),
                    radius = baseR * 0.55f,
                ),
                radius = baseR * 0.55f,
                center = Offset(cx - baseR * 0.22f, cy - baseR * 0.28f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHaloRings(
    cx: Float,
    cy: Float,
    baseR: Float,
    alphaScale: Float,
) {
    drawCircle(
        color = OrbDeep.copy(alpha = 0.18f * alphaScale),
        radius = baseR * 1.35f,
        center = Offset(cx, cy),
        style = Stroke(width = baseR * 0.04f),
    )
    drawCircle(
        color = OrbMid.copy(alpha = 0.22f * alphaScale),
        radius = baseR * 1.55f,
        center = Offset(cx, cy),
        style = Stroke(width = baseR * 0.025f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawListeningRipples(
    cx: Float,
    cy: Float,
    baseR: Float,
    pulse: Float,
) {
    for (i in 0..3) {
        val t = ((pulse + i / 4f) % 1f)
        val r = baseR * (1.2f + t * 1.35f)
        val alpha = (1f - t) * 0.28f
        drawCircle(
            color = OrbMid.copy(alpha = alpha),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = baseR * 0.03f * (1f - t * 0.4f)),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpeakingRings(
    cx: Float,
    cy: Float,
    baseR: Float,
    pulse: Float,
) {
    for (i in 0..2) {
        val t = ((pulse + i / 3f) % 1f)
        val r = baseR * (1.4f + t * 1.1f)
        val alpha = (1f - t) * 0.35f
        drawCircle(
            color = OrbDeep.copy(alpha = alpha),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = baseR * 0.035f * (1f - t * 0.5f)),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(
    cx: Float,
    cy: Float,
    baseR: Float,
    phase: Float,
    spinDeg: Float,
) {
    val count = 18
    val spinRad = Math.toRadians(spinDeg.toDouble()).toFloat()
    for (i in 0 until count) {
        val angle = (i / count.toFloat()) * (2f * PI.toFloat()) + phase * 0.35f + spinRad
        val orbit = baseR * (1.55f + 0.25f * sin(phase + i))
        val x = cx + cos(angle) * orbit
        val y = cy + sin(angle) * orbit
        val pr = baseR * (0.045f + 0.02f * ((i % 3) / 2f))
        drawCircle(
            color = OrbParticle.copy(alpha = 0.35f + 0.35f * ((i % 4) / 3f)),
            radius = pr,
            center = Offset(x, y),
        )
    }
}
