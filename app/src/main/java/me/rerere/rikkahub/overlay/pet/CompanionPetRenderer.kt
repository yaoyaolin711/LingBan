package me.rerere.rikkahub.overlay.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.model.Avatar

/** 悬浮窗气泡展示上限（汉字约数），完整文案仍可进会话/通知。 */
const val COMPANION_BUBBLE_MAX_CHARS = 40

/**
 * 陪伴悬浮头像：伴侣头像 + 旁侧短气泡。无白底卡片、无像素宠。
 */
class CompanionPetRenderer : PetRenderer {
    @Composable
    override fun Content(
        state: CompanionPetState,
        onClick: () -> Unit,
        besidePet: @Composable () -> Unit,
    ) {
        val speaking = state.bubbleText.isNotBlank()
        val glow = emotionGlowColor(state.emotion)
        val motion = rememberCompanionAvatarMotion(emotion = state.emotion, speaking = speaking)
        val bubbleBg = Color.Black.copy(alpha = 0.58f)
        val onBubble = Color.White.copy(alpha = 0.95f)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onClick),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 3.dp)
                            .size(width = 34.dp, height = 8.dp)
                            .graphicsLayer {
                                translationY = motion.bobY * 0.3f
                                alpha = 0.3f
                            },
                    ) {
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                translationY = motion.bobY
                                rotationZ = motion.swayDeg
                                scaleX = motion.scale
                                scaleY = motion.scale
                            },
                    ) {
                        Canvas(modifier = Modifier.size(58.dp)) {
                            val r = size.minDimension / 2f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glow.copy(alpha = if (speaking) 0.4f else 0.24f),
                                        glow.copy(alpha = 0.08f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = r,
                                ),
                                radius = r,
                            )
                        }
                        CompanionAvatarFace(
                            name = state.assistantName.ifBlank { "Solace" },
                            avatar = state.avatar,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
            }

            besidePet()

            AnimatedVisibility(
                visible = speaking,
                enter = fadeIn(tween(180)) +
                    slideInHorizontally(tween(220)) { it / 3 } +
                    scaleIn(initialScale = 0.88f, animationSpec = tween(220)),
                exit = fadeOut(tween(140)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp),
                ) {
                    Canvas(modifier = Modifier.size(width = 8.dp, height = 12.dp)) {
                        val path = Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(0f, size.height / 2f)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(path, color = bubbleBg)
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 56.dp, max = 148.dp)
                            .background(bubbleBg, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = state.bubbleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = onBubble,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionAvatarFace(
    name: String,
    avatar: Avatar,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clip(CircleShape),
    ) {
        when (avatar) {
            is Avatar.Image -> {
                val context = LocalContext.current
                val density = LocalDensity.current
                val px = with(density) { 92.dp.roundToPx() }
                val request = remember(avatar.url, px) {
                    ImageRequest.Builder(context)
                        .data(avatar.url)
                        .size(px)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            is Avatar.Emoji -> {
                BasicText(
                    text = avatar.content,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = 28.sp,
                    ),
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        lineHeight = 0.9.em,
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(4.dp),
                )
            }
            is Avatar.Dummy -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF5C6BC0)),
                ) {
                    BasicText(
                        text = name.take(1).ifBlank { "S" }.uppercase(),
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 14.sp,
                            maxFontSize = 26.sp,
                        ),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

fun emotionGlowColor(emotion: CompanionEmotionState): Color = when (emotion) {
    CompanionEmotionState.CALM -> Color(0xFF90CAF9)
    CompanionEmotionState.WARM -> Color(0xFFFFAB91)
    CompanionEmotionState.PLAYFUL -> Color(0xFFF48FB1)
    CompanionEmotionState.CONCERNED -> Color(0xFFB0BEC5)
}

@Composable
private fun rememberCompanionAvatarMotion(
    emotion: CompanionEmotionState,
    speaking: Boolean,
): CompanionAvatarMotion {
    val transition = rememberInfiniteTransition(label = "companion_avatar")
    val bobMs = when {
        speaking -> 520
        emotion == CompanionEmotionState.PLAYFUL -> 700
        emotion == CompanionEmotionState.CONCERNED -> 1400
        else -> 1300
    }
    val bobAmp = when {
        speaking -> 4f
        emotion == CompanionEmotionState.PLAYFUL -> 4f
        emotion == CompanionEmotionState.CONCERNED -> 1.5f
        else -> 2.5f
    }
    val swayAmp = when {
        speaking -> 2.5f
        emotion == CompanionEmotionState.PLAYFUL -> 4f
        else -> 1.5f
    }
    val bob by transition.animateFloat(
        initialValue = -bobAmp,
        targetValue = bobAmp,
        animationSpec = infiniteRepeatable(
            animation = tween(bobMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    val sway by transition.animateFloat(
        initialValue = -swayAmp,
        targetValue = swayAmp,
        animationSpec = infiniteRepeatable(
            animation = tween((bobMs * 1.15f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )
    val scale by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = if (speaking) 1.04f else 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(bobMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    return CompanionAvatarMotion(bobY = bob, swayDeg = sway, scale = scale)
}

private data class CompanionAvatarMotion(
    val bobY: Float,
    val swayDeg: Float,
    val scale: Float,
)
