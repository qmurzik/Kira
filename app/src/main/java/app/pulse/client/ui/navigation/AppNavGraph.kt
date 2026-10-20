package app.pulse.client.ui.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.pulse.client.ui.auth.AccountsScreen
import app.pulse.client.ui.auth.LoginScreen
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.messages.ChatScreen
import app.pulse.client.ui.messages.DialogsScreen
import app.pulse.client.ui.services.ServicesScreen

@Composable
fun AppNavGraph(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.FEED) {
        composable(Routes.FEED) { Pending("Главная", 3, showBack = false) }
        composable(Routes.SERVICES) { ServicesScreen() }
        composable(Routes.MESSAGES) { DialogsScreen() }
        composable(Routes.CLIPS) { Pending("Клипы", 4, showBack = false) }
        composable(Routes.VIDEO) { Pending("Видео", 4, showBack = false) }

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

        composable(Routes.PROFILE) { Pending("Профиль", 3) }
        composable(Routes.FRIENDS) { Pending("Друзья", 3) }
        composable(Routes.GROUPS) { Pending("Сообщества", 3) }
        composable(Routes.PHOTOS) { Pending("Фото", 3) }
        composable(Routes.BIRTHDAYS) { Pending("Дни рождения", 3) }
        composable(Routes.SEARCH) { Pending("Поиск", 3) }
        composable(Routes.MUSIC) { Pending("Музыка", 5) }
        composable(Routes.NOTIFICATIONS) { Pending("Уведомления", 6) }
        composable(Routes.SETTINGS) { Pending("Настройки", 6) }
    }
}

/** Temporary screen for sections scheduled for a later stage. */
@Composable
private fun Pending(title: String, stage: Int, showBack: Boolean = true) {
    Scaffold(topBar = { AppTopBar(title, showBack = showBack) }) { p ->
        MessageBox("Раздел в разработке", "Будет реализован на этапе $stage", modifier = Modifier.padding(p))
    }
}
