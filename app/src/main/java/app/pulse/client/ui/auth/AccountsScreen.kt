package app.pulse.client.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ListRow
import app.pulse.client.ui.components.SectionHeader
import app.pulse.client.ui.components.ThinDivider
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen() {
    val container = LocalAppContainer.current
    val nav = LocalNavigator.current
    val activeId = LocalSession.current.userId
    val accounts by container.accounts.accounts.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar("Аккаунты") }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("Вход выполнен") }
            items(accounts, key = { it.userId }) { acc ->
                ListRow(
                    title = acc.name.ifEmpty { "id${acc.userId}" },
                    subtitle = "id${acc.userId}",
                    leading = { Avatar(acc.photo, acc.name, 44.dp) },
                    trailing = {
                        if (acc.userId == activeId) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Активный", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable { container.switchTo(acc.userId) },
                )
            }
            item { ThinDivider() }
            item {
                ListRow(
                    title = "Добавить аккаунт",
                    leading = { Icon(Icons.Outlined.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { nav.open(Routes.ADD_ACCOUNT) },
                )
            }
            item {
                ListRow(
                    title = "Выйти из текущего аккаунта",
                    subtitle = "Токены и кэш этого аккаунта будут удалены с устройства",
                    leading = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { confirmLogout = true },
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Сессия будет завершена, локальные данные этого аккаунта удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    container.appScope.launch { container.logout() }
                }) { Text("Выйти") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
        )
    }
}
