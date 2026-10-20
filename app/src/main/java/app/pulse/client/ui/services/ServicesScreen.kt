package app.pulse.client.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Routes

@Immutable
private data class Service(val title: String, val icon: ImageVector, val tint: Color, val route: String)

@Composable
fun ServicesScreen() {
    val nav = LocalNavigator.current
    val me = LocalSession.current.userId
    val services = listOf(
        Service("Профиль", Icons.Outlined.AccountCircle, Color(0xFF5B4FE0), Routes.profile(me)),
        Service("Друзья", Icons.Outlined.People, Color(0xFF2FB7A0), Routes.friends(me)),
        Service("Сообщества", Icons.Outlined.Groups, Color(0xFF4C9BE8), Routes.groups(me)),
        Service("Фото", Icons.Outlined.PhotoLibrary, Color(0xFFF08A3C), Routes.photos(me)),
        Service("Музыка", Icons.Outlined.MusicNote, Color(0xFFE25C7E), Routes.MUSIC),
        Service("Дни рождения", Icons.Outlined.Cake, Color(0xFFD9A400), Routes.BIRTHDAYS),
        Service("Уведомления", Icons.Outlined.Notifications, Color(0xFF8BBF3F), Routes.NOTIFICATIONS),
        Service("Поиск", Icons.Outlined.Search, Color(0xFF7B6CF6), Routes.SEARCH),
        Service("Аккаунты", Icons.Outlined.SwitchAccount, Color(0xFF6B7280), Routes.ACCOUNTS),
        Service("Настройки", Icons.Outlined.Settings, Color(0xFF6B7280), Routes.SETTINGS),
    )
    Scaffold(topBar = { AppTopBar("Сервисы", showBack = false) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Всё, что доступно через официальный API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
            items(services, key = { it.title }) { s ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { nav.open(s.route) },
                ) {
                    Column(
                        Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(s.tint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(s.icon, contentDescription = null, tint = Color.White)
                        }
                        Text(s.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}
