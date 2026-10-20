package app.pulse.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pulse.client.data.settings.AppSettings
import app.pulse.client.di.AppContainer
import app.pulse.client.music.PlaybackService
import app.pulse.client.ui.auth.LoginScreen
import app.pulse.client.ui.music.MiniPlayer
import app.pulse.client.ui.navigation.AppNavGraph
import app.pulse.client.ui.navigation.BottomBar
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Navigator
import app.pulse.client.ui.navigation.Routes
import app.pulse.client.ui.navigation.SessionExpiredBanner
import app.pulse.client.ui.navigation.Tab
import app.pulse.client.ui.theme.PulseTheme

@Composable
fun AppRoot(container: AppContainer, content: @Composable (AppSettings, @Composable () -> Unit) -> Unit = { _, c -> c() }) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    PulseTheme(settings.theme, settings.fontScale) {
        CompositionLocalProvider(LocalAppContainer provides container) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                content(settings) {
                    val session by container.session.collectAsStateWithLifecycle()
                    val s = session
                    if (s == null) {
                        LoginScreen(addAccount = false, onDone = {}, onCancel = null)
                    } else {
                        key(s.userId) {
                            CompositionLocalProvider(LocalSession provides s) {
                                MainScaffold(container)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(container: AppContainer) {
    val nav = rememberNavController()
    val navigator = remember(nav) { Navigator(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }

    val musicUi by container.music.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (PlaybackService.isRunning) container.music.connect() }

    // Open a chat requested from a notification (switching account if needed).
    val pending by container.pendingChat.collectAsStateWithLifecycle()
    val myId = LocalSession.current.userId
    LaunchedEffect(pending) {
        val (user, peer) = pending ?: return@LaunchedEffect
        if (user == myId) {
            container.pendingChat.value = null
            navigator.chat(peer)
        } else if (container.accounts.get(user) != null) {
            container.switchTo(user)
        } else {
            container.pendingChat.value = null
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Column {
                    // Mini player everywhere except the music screen itself and full-screen video.
                    if (musicUi.hasMedia && route != Routes.MUSIC && route != Routes.VIDEO_PLAYER && route != Routes.CLIPS) {
                        MiniPlayer(musicUi, container.music, onOpen = { navigator.open(Routes.MUSIC) })
                    }
                    if (tab != null) BottomBar(selected = tab, onSelect = navigator::tab)
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                SessionExpiredBanner(container, onRelogin = { navigator.open(Routes.ADD_ACCOUNT) })
                AppNavGraph(nav)
            }
        }
    }
}
