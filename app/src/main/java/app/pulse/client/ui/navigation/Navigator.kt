package app.pulse.client.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/** Typed wrapper so screens do not need to know route strings. */
@Stable
class Navigator(private val nav: NavController) {
    fun back() {
        nav.popBackStack()
    }

    fun tab(tab: Tab) {
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun open(route: String) {
        nav.navigate(route) { launchSingleTop = true }
    }

    /** Pops the current screen and opens [route] in its place. */
    fun replace(route: String) {
        nav.popBackStack()
        nav.navigate(route) { launchSingleTop = true }
    }

    fun chat(peerId: Long) = open(Routes.chat(peerId))
    fun post(ownerId: Long, postId: Long) = open(Routes.post(ownerId, postId))
    fun profile(id: Long) = open(Routes.profile(id))
    fun friends(id: Long) = open(Routes.friends(id))
    fun groups(id: Long) = open(Routes.groups(id))
    fun photos(id: Long) = open(Routes.photos(id))
    fun photo(url: String) = open(Routes.photoViewer(url))
    fun video(apiId: String) = open(Routes.videoPlayer(apiId))
    fun stories(index: Int) = open(Routes.stories(index))

    /** Result passing for the "forward to…" picker. */
    fun setResult(key: String, value: Long) {
        nav.previousBackStackEntry?.savedStateHandle?.set(key, value)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
