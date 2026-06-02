package de.nexus.agent.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.nexus.agent.feature.chat.ui.ChatScreen
import de.nexus.agent.feature.settings.ui.AutonomySettingsScreen
import de.nexus.agent.feature.settings.ui.LlmProviderConfigScreen
import de.nexus.agent.feature.settings.ui.LlmProviderSettingsScreen
import de.nexus.agent.feature.settings.ui.MemoryBrowserScreen
import de.nexus.agent.feature.settings.ui.SettingsScreen
import de.nexus.agent.feature.settings.ui.SkillEditorScreen
import de.nexus.agent.feature.settings.ui.ToolSettingsScreen

sealed class Screen(val route: String) {

    data object Chat : Screen("chat")

    data object Settings : Screen("settings")

    data object ProviderConfig : Screen("provider_config/{providerId}") {
        fun createRoute(providerId: String) = "provider_config/$providerId"
    }

    data object ToolSettings : Screen("tool_settings")

    data object SkillEditor : Screen("skill_editor/{skillId}") {
        fun createRoute(skillId: String) = "skill_editor/$skillId"
    }

    data object MemoryBrowser : Screen("memory_browser")

    data object AutonomySettings : Screen("autonomy_settings")

    data object FullProviderSettings : Screen("full_provider_settings")
}

private const val ANIMATION_DURATION_MS = 300

@Composable
fun NexusNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(ANIMATION_DURATION_MS)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION_MS))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(ANIMATION_DURATION_MS)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION_MS))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(ANIMATION_DURATION_MS)
            ) + fadeIn(animationSpec = tween(ANIMATION_DURATION_MS))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(ANIMATION_DURATION_MS)
            ) + fadeOut(animationSpec = tween(ANIMATION_DURATION_MS))
        }
    ) {
        // ── Chat (Main Screen) ──────────────────────────────────
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // ── Settings (Main Settings Screen) ─────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToProviderConfig = { providerId ->
                    navController.navigate(Screen.ProviderConfig.createRoute(providerId))
                },
                onNavigateToFullProviderSettings = {
                    navController.navigate(Screen.FullProviderSettings.route)
                },
                onNavigateToToolSettings = {
                    navController.navigate(Screen.ToolSettings.route)
                },
                onNavigateToAutonomySettings = {
                    navController.navigate(Screen.AutonomySettings.route)
                },
                onNavigateToMemoryBrowser = {
                    navController.navigate(Screen.MemoryBrowser.route)
                },
                onNavigateToSkillEditor = { skillId ->
                    navController.navigate(Screen.SkillEditor.createRoute(skillId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // ── LLM Provider Config (single provider) ───────────────
        composable(
            route = Screen.ProviderConfig.route,
            arguments = listOf(
                navArgument("providerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
            LlmProviderConfigScreen(
                providerId = providerId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Full Provider Settings ──────────────────────────────
        composable(Screen.FullProviderSettings.route) {
            LlmProviderSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProvider = { providerId ->
                    navController.navigate(Screen.ProviderConfig.createRoute(providerId))
                }
            )
        }

        // ── Tool Settings ──────────────────────────────────────
        composable(Screen.ToolSettings.route) {
            ToolSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Skill Editor ────────────────────────────────────────
        composable(
            route = Screen.SkillEditor.route,
            arguments = listOf(
                navArgument("skillId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val skillId = backStackEntry.arguments?.getString("skillId") ?: ""
            SkillEditorScreen(
                skillId = skillId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Memory Browser ─────────────────────────────────────
        composable(Screen.MemoryBrowser.route) {
            MemoryBrowserScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Autonomy Settings ──────────────────────────────────
        composable(Screen.AutonomySettings.route) {
            AutonomySettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
