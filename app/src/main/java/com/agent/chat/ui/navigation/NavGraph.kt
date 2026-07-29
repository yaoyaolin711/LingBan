package com.agent.chat.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agent.chat.ui.agent.AgentCenterScreen
import com.agent.chat.ui.agent.AgentDetailScreen
import com.agent.chat.ui.chat.ChatScreen
import com.agent.chat.ui.conversation.ConversationListScreen
import com.agent.chat.ui.home.HomeScreen
import com.agent.chat.ui.memory.MemoryScreen
import com.agent.chat.ui.motion.SwipeBackContainer
import com.agent.chat.ui.persona.PersonaListScreen
import com.agent.chat.ui.profile.ProfileScreen
import com.agent.chat.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val AGENT_CENTER = "agent_center"
    const val AGENT_DETAIL = "agent/{personaId}"
    const val PERSONA_LIST = "persona_list"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"

    fun chat(conversationId: String): String = "chat/$conversationId"
    fun agentDetail(personaId: String): String = "agent/$personaId"
}

private val EnterTween = tween<Float>(durationMillis = 220)
private val ExitTween = tween<Float>(durationMillis = 180)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentNavHost(
    navController: NavHostController = rememberNavController(),
    openConversationId: String? = null,
    onOpenConversationConsumed: () -> Unit = {},
) {
    androidx.compose.runtime.LaunchedEffect(openConversationId) {
        val id = openConversationId ?: return@LaunchedEffect
        navController.navigate(Routes.chat(id)) {
            launchSingleTop = true
        }
        onOpenConversationConsumed()
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                slideInHorizontally(animationSpec = tween(220)) { it / 12 } + fadeIn(EnterTween)
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(180)) { -it / 24 } + fadeOut(ExitTween)
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(220)) { -it / 24 } + fadeIn(EnterTween)
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(180)) { it / 12 } + fadeOut(ExitTween)
            },
        ) {
            composable(
                route = Routes.HOME,
                enterTransition = { fadeIn(tween(280)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                HomeScreen(
                    animatedVisibilityScope = this,
                    onOpenConversation = { conversationId ->
                        navController.navigate(Routes.chat(conversationId))
                    },
                    onSettingsClick = {
                        navController.navigate(Routes.PROFILE)
                    },
                    onAgentCenterClick = {
                        navController.navigate(Routes.AGENT_CENTER)
                    },
                    onMemoryClick = {
                        navController.navigate(Routes.MEMORY)
                    },
                )
            }

            composable(Routes.PROFILE) {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    ProfileScreen(
                        onBackClick = { navController.popBackStack() },
                        onModelSettingsClick = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        onApiConfigClick = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        onMemoryClick = {
                            navController.navigate(Routes.MEMORY)
                        },
                    )
                }
            }

            composable(Routes.MEMORY) {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    MemoryScreenRoute(
                        onBackClick = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.CONVERSATION_LIST) {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    ConversationListScreen(
                        onConversationClick = { conversationId ->
                            navController.navigate(Routes.chat(conversationId))
                        },
                        onNewConversationCreated = { conversationId ->
                            navController.navigate(Routes.chat(conversationId))
                        },
                        onPersonaManageClick = {
                            navController.navigate(Routes.AGENT_CENTER)
                        },
                        onSettingsClick = {
                            navController.navigate(Routes.PROFILE)
                        },
                    )
                }
            }

            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                ),
                enterTransition = { fadeIn(tween(280)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) {
                ChatScreen(
                    animatedVisibilityScope = this,
                    onBackClick = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.AGENT_CENTER,
                enterTransition = {
                    fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 8 }
                },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { it / 8 }
                },
            ) {
                val visibilityScope = this
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    AgentCenterScreen(
                        animatedVisibilityScope = visibilityScope,
                        onBackClick = { navController.popBackStack() },
                        onAgentClick = { personaId ->
                            navController.navigate(Routes.agentDetail(personaId))
                        },
                        onManageClick = {
                            navController.navigate(Routes.PERSONA_LIST)
                        },
                        onCreateClick = {
                            navController.navigate(Routes.PERSONA_LIST)
                        },
                    )
                }
            }

            composable(
                route = Routes.AGENT_DETAIL,
                arguments = listOf(
                    navArgument("personaId") { type = NavType.StringType },
                ),
                enterTransition = { fadeIn(tween(280)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) {
                val visibilityScope = this
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    AgentDetailScreen(
                        animatedVisibilityScope = visibilityScope,
                        onBackClick = { navController.popBackStack() },
                        onOpenConversation = { conversationId ->
                            navController.navigate(Routes.chat(conversationId)) {
                                popUpTo(Routes.AGENT_CENTER) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onManageClick = {
                            navController.navigate(Routes.PERSONA_LIST)
                        },
                    )
                }
            }

            composable(Routes.PERSONA_LIST) {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    PersonaListScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.SETTINGS) {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryScreenRoute(onBackClick: () -> Unit) {
    // 下拉刷新：轻量刷新入口（记忆流本已为 Flow 实时）
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                delay(650)
                refreshing = false
            }
        },
    ) {
        MemoryScreen(onBackClick = onBackClick)
    }
}
