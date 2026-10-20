package app.pulse.client.ui.feed

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.navigation.LocalNavigator
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreatePostViewModel(private val app: Application, session: UserSession) : ViewModel() {
    private val repo = session.feed
    val photos = MutableStateFlow<List<Uri>>(emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val done = MutableStateFlow(false)

    fun addPhotos(uris: List<Uri>) {
        photos.value = (photos.value + uris).distinct().take(10)
    }

    fun remove(uri: Uri) {
        photos.value = photos.value - uri
    }

    fun publish(text: String, friendsOnly: Boolean) {
        if (busy.value || (text.isBlank() && photos.value.isEmpty())) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try {
                val attachments = photos.value.map { uri ->
                    val bytes = withContext(Dispatchers.IO) {
                        app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Файл недоступен")
                    }
                    repo.uploadWallPhoto(bytes, app.contentResolver.getType(uri) ?: "image/jpeg")
                }
                repo.publish(null, text.trim(), attachments, friendsOnly)
                done.value = true
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
fun CreatePostScreen() {
    val container = LocalAppContainer.current
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel { CreatePostViewModel(container.app, session) }
    val photos by vm.photos.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(done) { if (done) nav.back() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        vm.addPhotos(uris)
    }

    Scaffold(
        topBar = {
            AppTopBar("Новая запись", actions = {
                TextButton(onClick = { vm.publish(text, friendsOnly) }, enabled = !busy && (text.isNotBlank() || photos.isNotEmpty())) {
                    Text("Опубликовать")
                }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Что у вас нового?") },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                minLines = 5,
            )
            if (photos.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(photos, key = { it.toString() }) { uri ->
                        Box(Modifier.padding(end = 8.dp).size(88.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            )
                            IconButton(onClick = { vm.remove(uri) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Icon(Icons.Filled.Close, "Убрать")
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Outlined.AddPhotoAlternate, "Добавить фото") }
                Checkbox(checked = friendsOnly, onCheckedChange = { friendsOnly = it })
                Text("Только для друзей", style = MaterialTheme.typography.bodyMedium)
            }
            if (busy) LoadingRow()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
