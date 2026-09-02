package com.qmurzik.animetv.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Settings : Screen("settings")

    data object Details : Screen("details/{animeId}") {
        fun createRoute(animeId: String) = "details/${animeId.encode()}"
    }

    data object Player : Screen("player/{animeId}/{season}/{episode}") {
        fun createRoute(animeId: String, season: Int, episode: Int) =
            "player/${animeId.encode()}/$season/$episode"
    }
}

private fun String.encode(): String =
    java.net.URLEncoder.encode(this, "UTF-8")
