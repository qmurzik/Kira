package app.pulse.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.pulse.client.ui.auth.AccountsScreen
import app.pulse.client.ui.auth.LoginScreen
import app.pulse.client.ui.messages.ChatScreen
import app.pulse.client.ui.messages.DialogsScreen
import app.pulse.client.ui.music.MusicScreen
import app.pulse.client.ui.notifications.NotificationsScreen
import app.pulse.client.ui.services.ServicesScreen
import app.pulse.client.ui.settings.PinSetupScreen
import app.pulse.client.ui.settings.SettingsScreen
import app.pulse.client.ui.video.ClipsScreen
import app.pulse.client.ui.video.VideoPlayerScreen
import app.pulse.client.ui.video.VideoScreen
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.feed.CreatePostScreen
import app.pulse.client.ui.feed.FeedScreen
import app.pulse.client.ui.feed.FeedViewModel
import app.pulse.client.ui.feed.PostScreen
import app.pulse.client.ui.feed.StoriesScreen
import app.pulse.client.ui.profile.BirthdaysScreen
import app.pulse.client.ui.profile.FriendsScreen
import app.pulse.client.ui.profile.GroupsScreen
import app.pulse.client.ui.profile.PhotoViewerScreen
import app.pulse.client.ui.profile.PhotosScreen
import app.pulse.client.ui.profile.ProfileScreen
import app.pulse.client.ui.search.SearchScreen

@Composable
fun AppNavGraph(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.FEED) {
        composable(Routes.FEED) {
            val session = LocalSession.current
            FeedScreen(viewModel { FeedViewModel(session) })
        }
        composable(Routes.SERVICES) { ServicesScreen() }
        composable(Routes.MESSAGES) { DialogsScreen() }
        composable(Routes.CLIPS) { ClipsScreen() }
        composable(Routes.VIDEO) { VideoScreen() }
        composable(Routes.VIDEO_PLAYER, arguments = listOf(navArgument("videoId") { type = NavType.StringType })) { e ->
            VideoPlayerScreen(e.arguments?.getString("videoId").orEmpty())
        }

        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("peerId") { type = NavType.LongType }),
        ) { entry ->
            ChatScreen(entry.arguments?.getLong("peerId") ?: 0L)
        }
        composable(Routes.FORWARD_PICK) {
            val navigator = LocalNavigator.current
            DialogsScreen(onPick = { peer -> navigator.replace(Routes.chat(peer)) })
        }

        composable(Routes.ACCOUNTS) { AccountsScreen() }
        composable(Routes.ADD_ACCOUNT) {
            LoginScreen(addAccount = true, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
        }

        composable(Routes.PROFILE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            ProfileScreen(e.arguments?.getLong("id") ?: 0L)
        }
        composable(Routes.FRIENDS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            FriendsScreen(e.arguments?.getLong("id") ?: 0L)
        }
        composable(Routes.GROUPS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            GroupsScreen(e.arguments?.getLong("id") ?: 0L)
        }
        composable(Routes.PHOTOS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            PhotosScreen(e.arguments?.getLong("id") ?: 0L)
        }
        composable(
            Routes.PHOTO_VIEWER,
            arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }),
        ) { e ->
            PhotoViewerScreen(e.arguments?.getString("url").orEmpty())
        }
        composable(
            Routes.POST,
            arguments = listOf(
                navArgument("ownerId") { type = NavType.LongType },
                navArgument("postId") { type = NavType.LongType },
            ),
        ) { e ->
            PostScreen(e.arguments?.getLong("ownerId") ?: 0L, e.arguments?.getLong("postId") ?: 0L)
        }
        composable(Routes.CREATE_POST) { CreatePostScreen() }
        composable(Routes.STORIES, arguments = listOf(navArgument("index") { type = NavType.IntType })) { e ->
            val session = LocalSession.current
            // Share the feed's ViewModel so the viewer shows the already loaded stories.
            val owner = remember(e) { runCatching { nav.getBackStackEntry(Routes.FEED) }.getOrDefault(e) }
            StoriesScreen(viewModel(viewModelStoreOwner = owner) { FeedViewModel(session) }, e.arguments?.getInt("index") ?: 0)
        }
        composable(Routes.BIRTHDAYS) { BirthdaysScreen() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.MUSIC) { MusicScreen() }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.PIN_SETUP) { PinSetupScreen() }
    }
}
