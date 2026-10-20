package app.pulse.client.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.client.di.AppContainer
import app.pulse.client.ui.LocalSession

@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val unread = LocalSession.current.unreadCount.collectAsStateWithLifecycle().value
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = {
                        if (tab == Tab.Messages && unread > 0) {
                            Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                        }
                    }) {
                        Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
fun SessionExpiredBanner(container: AppContainer, onRelogin: () -> Unit) {
    val expired by container.expired.collectAsStateWithLifecycle()
    if (expired == null || expired != LocalSession.current.userId) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onRelogin).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Сессия истекла, данные могут быть устаревшими",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRelogin) { Text("Войти") }
        }
    }
}
