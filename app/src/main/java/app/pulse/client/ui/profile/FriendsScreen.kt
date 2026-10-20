package app.pulse.client.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.data.repo.BirthdayFriend
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Owner
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.ListRow
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.navigation.LocalNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/** Generic "load a list once" state used by the people/community screens. */
sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>
    data class Ready<T>(val items: List<T>) : ListState<T>
    data class Error(val message: String) : ListState<Nothing>
}

class FriendsViewModel(private val session: UserSession, private val userId: Long) : ViewModel() {
    private val repo = session.profiles
    val friends = MutableStateFlow<ListState<Owner>>(ListState.Loading)
    val requests = MutableStateFlow<ListState<Owner>>(ListState.Loading)
    val isMe = userId == session.userId

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            friends.value = ListState.Loading
            friends.value = try {
                // friends.get pages by 5000 at most; one big page covers almost everyone.
                ListState.Ready(repo.friends(userId, 0, 5000).first)
            } catch (e: Exception) {
                ListState.Error(e.userMessage())
            }
        }
        if (isMe) loadRequests()
    }

    private fun loadRequests() {
        viewModelScope.launch {
            requests.value = try {
                ListState.Ready(repo.requests())
            } catch (e: Exception) {
                ListState.Error(e.userMessage())
            }
        }
    }

    fun accept(o: Owner) {
        viewModelScope.launch {
            runCatching { repo.addFriend(o.id) }
            loadRequests()
            load()
        }
    }

    fun decline(o: Owner) {
        viewModelScope.launch {
            runCatching { repo.deleteFriend(o.id) }
            loadRequests()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(userId: Long) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "friends_$userId") { FriendsViewModel(session, userId) }
    val friends by vm.friends.collectAsStateWithLifecycle()
    val requests by vm.requests.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    val tabs = if (vm.isMe) listOf("Все", "Онлайн", "Заявки") else listOf("Все", "Онлайн")

    Scaffold(topBar = { AppTopBar("Друзья") }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            if (tab < 2) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Поиск друзей") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            val state = if (tab == 2) requests else friends
            when (val s = state) {
                ListState.Loading -> LoadingBox()
                is ListState.Error -> ErrorBox(s.message, vm::load)
                is ListState.Ready -> {
                    val list = s.items
                        .let { if (tab == 1) it.filter { o -> o.online } else it }
                        .let { if (tab < 2 && filter.isNotBlank()) it.filter { o -> o.name.contains(filter, ignoreCase = true) } else it }
                    if (list.isEmpty()) {
                        MessageBox(if (tab == 2) "Новых заявок нет" else "Никого не найдено")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(list, key = { it.id }, contentType = { "person" }) { o ->
                                ListRow(
                                    title = o.name,
                                    subtitle = Format.lastSeen(o.online, o.lastSeen),
                                    leading = { Avatar(o.photo, o.name, 48.dp, online = o.online) },
                                    trailing = {
                                        if (tab == 2) {
                                            Column {
                                                FilledTonalButton(onClick = { vm.accept(o) }) { Text("Принять") }
                                                Text(
                                                    "Скрыть",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.clickable { vm.decline(o) }.padding(8.dp),
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable { nav.profile(o.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class BirthdaysViewModel(session: UserSession) : ViewModel() {
    private val repo = session.profiles
    val state = MutableStateFlow<ListState<BirthdayFriend>>(ListState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.value = ListState.Loading
            state.value = try {
                ListState.Ready(repo.birthdays())
            } catch (e: Exception) {
                ListState.Error(e.userMessage())
            }
        }
    }
}

@Composable
fun BirthdaysScreen() {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel { BirthdaysViewModel(session) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Дни рождения") }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ListState.Loading -> LoadingBox()
                is ListState.Error -> ErrorBox(s.message, vm::load)
                is ListState.Ready -> if (s.items.isEmpty()) {
                    MessageBox("Нет данных", "Друзья не указали дату рождения или скрыли её")
                } else {
                    LazyColumn {
                        items(s.items, key = { it.owner.id }) { b ->
                            val whenText = when (b.daysLeft) {
                                0 -> "сегодня"
                                1 -> "завтра"
                                else -> "через " + Format.plural(b.daysLeft, "день", "дня", "дней")
                            }
                            val age = b.year?.let { y -> ", исполнится ${ageOnNextBirthday(y, b.daysLeft)}" }.orEmpty()
                            ListRow(
                                title = b.owner.name,
                                subtitle = "${b.day}.${b.month.toString().padStart(2, '0')} — $whenText$age",
                                leading = { Avatar(b.owner.photo, b.owner.name, 44.dp) },
                                modifier = Modifier.clickable { nav.profile(b.owner.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Age the friend turns on the upcoming birthday (daysLeft days from today). */
private fun ageOnNextBirthday(birthYear: Int, daysLeft: Int): Int {
    val next = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysLeft) }
    return next.get(Calendar.YEAR) - birthYear
}
