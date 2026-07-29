package com.agent.chat.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agent.chat.ui.chat.ChatScreen
import com.agent.chat.ui.conversation.ConversationListScreen
import com.agent.chat.ui.persona.PersonaListScreen
import com.agent.chat.ui.settings.SettingsScreen

object Routes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val PERSONA_LIST = "persona_list"
    const val SETTINGS = "settings"

    fun chat(conversationId: String): String = "chat/$conversationId"
}

private val EnterTween = tween<Float>(durationMillis = 220)
private val ExitTween = tween<Float>(durationMillis = 180)

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

    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATION_LIST,
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
        composable(Routes.CONVERSATION_LIST) {
            ConversationListScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Routes.chat(conversationId))
                },
                onNewConversationCreated = { conversationId ->
                    navController.navigate(Routes.chat(conversationId))
                },
                onPersonaManageClick = {
                    navController.navigate(Routes.PERSONA_LIST)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
            ),
        ) {
            ChatScreen(
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(Routes.PERSONA_LIST) {
            PersonaListScreen(
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}
