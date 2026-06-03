package de.nexus.agent.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.nexus.agent.feature.chat.ui.ChatScreen
import de.nexus.agent.feature.chat.viewmodel.ChatViewModel
import de.nexus.agent.feature.settings.ui.LlmProviderSettingsScreen
import de.nexus.agent.feature.settings.ui.SettingsScreen

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object ProviderConfig : Screen("provider_config/{providerId}") {
        fun createRoute(providerId: String) = "provider_config/$providerId"
    }
}

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                viewModel = viewModel<ChatViewModel>()
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToProviderConfig = { providerId ->
                    navController.navigate(Screen.ProviderConfig.createRoute(providerId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProviderConfig.route) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: return@composable
            LlmProviderSettingsScreen(
                providerId = providerId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
