package com.agent.chat.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.chat.domain.model.Conversation
import com.agent.chat.domain.model.Memory
import com.agent.chat.domain.model.Persona
import com.agent.chat.ui.motion.ScrollEdgeFade
import com.agent.chat.ui.motion.SharedKeys
import com.agent.chat.ui.motion.parallaxOffset
import com.agent.chat.ui.motion.scaleClickable
import com.agent.chat.ui.theme.AgentChatTheme
import com.agent.chat.ui.theme.AgentThemeColors
import com.agent.chat.ui.theme.OrbDeep
import com.agent.chat.ui.theme.TextPrimary
import com.agent.chat.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.HomeScreen(
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenConversation: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAgentCenterClick: () -> Unit,
    onMemoryClick: () -> Unit = onAgentCenterClick,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showEntrance by viewModel.showEntrance.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        launch { viewModel.openConversationId.collect(onOpenConversation) }
        launch { viewModel.statusMessage.collect { snackbarHostState.showSnackbar(it) } }
        launch { viewModel.navigateAgentCenter.collect { onAgentCenterClick() } }
        launch { viewModel.navigateMemory.collect { onMemoryClick() } }
    }

    HomeScreenContent(
        uiState = uiState,
        showEntrance = showEntrance,
        snackbarHostState = snackbarHostState,
        animatedVisibilityScope = animatedVisibilityScope,
        onSettingsClick = onSettingsClick,
        onOrbClick = viewModel::onEnterChat,
        onStartExplore = viewModel::onStartExplore,
        onRecentChatClick = viewModel::onEnterChat,
        onRecentMemoryClick = viewModel::onRecentMemoryClick,
        onRecommendedAgentClick = viewModel::onRecommendedAgentClick,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.HomeScreenContent(
    uiState: HomeUiState,
    showEntrance: Boolean,
    snackbarHostState: SnackbarHostState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSettingsClick: () -> Unit,
    onOrbClick: () -> Unit,
    onStartExplore: () -> Unit,
    onRecentChatClick: () -> Unit,
    onRecentMemoryClick: () -> Unit,
    onRecommendedAgentClick: () -> Unit,
) {
    val orbScale = remember { Animatable(0.86f) }
    val orbAlpha = remember { Animatable(0f) }
    val greetingAlpha = remember { Animatable(0f) }
    val greetingOffset = remember { Animatable(18f) }
    val cardsVisible = remember { androidx.compose.runtime.mutableStateOf(false) }
    val scroll = rememberScrollState()

    LaunchedEffect(showEntrance) {
        if (!showEntrance) return@LaunchedEffect
        launch { orbAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        launch { orbScale.animateTo(1f, tween(780, easing = FastOutSlowInEasing)) }
        launch {
            kotlinx.coroutines.delay(280)
            greetingAlpha.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            greetingOffset.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
        launch {
            kotlinx.coroutines.delay(420)
            cardsVisible.value = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        val theme = AgentThemeColors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            theme.atmosphere,
                            theme.background,
                            theme.background,
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                OrbDeep.copy(alpha = if (theme.isDark) 0.18f else 0.10f),
                                Color.Transparent,
                            ),
                            radius = 900f,
                        ),
                    ),
            )

            if (!uiState.hasExplored) {
                FirstExploreState(
                    showEntrance = showEntrance,
                    animatedVisibilityScope = animatedVisibilityScope,
                    orbScale = orbScale.value,
                    orbAlpha = orbAlpha.value,
                    onStartExplore = onStartExplore,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(innerPadding)
                        .verticalScroll(scroll)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HomeTopBar(
                        visible = showEntrance,
                        onSettingsClick = onSettingsClick,
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    AiOrb(
                        state = uiState.orbState,
                        onClick = onOrbClick,
                        size = 228.dp,
                        entranceScale = orbScale.value,
                        entranceAlpha = orbAlpha.value,
                        modifier = Modifier
                            .graphicsLayer {
                                translationY = parallaxOffset(scroll.value.toFloat(), 0.22f)
                            }
                            .sharedElement(
                                state = rememberSharedContentState(SharedKeys.AI_ORB),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = buildGreeting(uiState),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = greetingAlpha.value
                                translationY = greetingOffset.value +
                                    parallaxOffset(scroll.value.toFloat(), 0.12f)
                            }
                            .padding(horizontal = 12.dp),
                    )

                    Text(
                        text = "轻触核心，开始对话",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .graphicsLayer { alpha = greetingAlpha.value * 0.9f },
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    HomeActivitySection(
                        activity = HomeActivityUi(
                            recentConversation = uiState.recentConversation,
                            recentMemory = uiState.recentMemory,
                            recommendedPersona = uiState.recommendedPersona,
                        ),
                        visible = cardsVisible.value,
                        scrollPx = scroll.value.toFloat(),
                        onRecentChatClick = onRecentChatClick,
                        onRecentMemoryClick = onRecentMemoryClick,
                        onRecommendedAgentClick = onRecommendedAgentClick,
                    )
                }
            }

            ScrollEdgeFade(
                top = true,
                bottom = true,
                height = 36.dp,
                color = theme.background,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.FirstExploreState(
    showEntrance: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    orbScale: Float,
    orbAlpha: Float,
    onStartExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AgentThemeColors
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        AiOrb(
            state = AiOrbState.Idle,
            size = 200.dp,
            entranceScale = if (showEntrance) orbScale else 0.86f,
            entranceAlpha = if (showEntrance) orbAlpha else 0f,
            modifier = Modifier.sharedElement(
                state = rememberSharedContentState(SharedKeys.AI_ORB),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "你好，我是灵伴。",
            style = MaterialTheme.typography.headlineMedium,
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "一个愿意慢慢了解你的 AI 伙伴。",
            style = MaterialTheme.typography.bodyLarge,
            color = theme.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "开始探索",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(theme.accent)
                .scaleClickable(onClick = onStartExplore)
                .padding(horizontal = 36.dp, vertical = 14.dp),
        )
    }
}

private fun buildGreeting(uiState: HomeUiState): String {
    val nick = uiState.userNickname.trim()
    if (nick.isEmpty()) return uiState.greeting
    val comma = uiState.greeting.indexOf('，')
    return if (comma >= 0) {
        uiState.greeting.substring(0, comma + 1) + nick + "，" +
            uiState.greeting.substring(comma + 1)
    } else {
        "$nick，${uiState.greeting}"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF7F1)
@Composable
private fun HomeScreenPreview() {
    AgentChatTheme {
        // Preview skips SharedTransitionScope — use non-shared content path via stub not available;
        // keep compile-safe by leaving preview as theme wrapper only.
        Text("Home Preview", color = TextPrimary)
    }
}
