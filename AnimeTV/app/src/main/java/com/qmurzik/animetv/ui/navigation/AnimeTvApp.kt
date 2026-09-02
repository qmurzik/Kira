package com.qmurzik.animetv.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qmurzik.animetv.ui.RootViewModel
import com.qmurzik.animetv.ui.components.LoadingState
import com.qmurzik.animetv.ui.details.DetailsScreen
import com.qmurzik.animetv.ui.favorites.FavoritesScreen
import com.qmurzik.animetv.ui.history.HistoryScreen
import com.qmurzik.animetv.ui.home.HomeScreen
import com.qmurzik.animetv.ui.onboarding.OnboardingScreen
import com.qmurzik.animetv.ui.player.PlayerScreen
import com.qmurzik.animetv.ui.search.SearchScreen
import com.qmurzik.animetv.ui.settings.SettingsScreen
import com.qmurzik.animetv.ui.theme.AnimeTvTheme

private val railRoutes = setOf(Screen.Home.route, Screen.Search.route, Screen.Favorites.route, Screen.History.route, Screen.Settings.route)

@Composable
fun AnimeTvApp(rootViewModel: RootViewModel = hiltViewModel()) {
    val settings by rootViewModel.settings.collectAsState()
    val currentSettings = settings ?: run {
        LoadingState()
        return
    }

    AnimeTvTheme(textScale = currentSettings.appearance.textScale) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showRail = railRoutes.any { it == currentRoute }

        Row(modifier = Modifier.fillMaxSize()) {
            if (showRail) {
                NavRail(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            NavHost(
                navController = navController,
                startDestination = if (currentSettings.onboardingCompleted) Screen.Home.route else Screen.Onboarding.route,
                modifier = Modifier.weight(1f),
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onOpenDetails = { id -> navController.navigate(Screen.Details.createRoute(id)) },
                        onPlay = { id, season, episode ->
                            navController.navigate(Screen.Player.createRoute(id, season, episode))
                        },
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(onOpenDetails = { id -> navController.navigate(Screen.Details.createRoute(id)) })
                }

                composable(Screen.Favorites.route) {
                    FavoritesScreen(onOpenDetails = { id -> navController.navigate(Screen.Details.createRoute(id)) })
                }

                composable(Screen.History.route) {
                    HistoryScreen(
                        onResume = { id, season, episode ->
                            navController.navigate(Screen.Player.createRoute(id, season, episode))
                        },
                    )
                }

                composable(Screen.Settings.route) { SettingsScreen() }

                composable(
                    route = Screen.Details.route,
                    arguments = listOf(navArgument("animeId") { type = NavType.StringType }),
                ) {
                    DetailsScreen(
                        onPlay = { animeId, season, episode ->
                            navController.navigate(Screen.Player.createRoute(animeId, season, episode))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Screen.Player.route,
                    arguments = listOf(
                        navArgument("animeId") { type = NavType.StringType },
                        navArgument("season") { type = NavType.IntType },
                        navArgument("episode") { type = NavType.IntType },
                    ),
                ) {
                    PlayerScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
