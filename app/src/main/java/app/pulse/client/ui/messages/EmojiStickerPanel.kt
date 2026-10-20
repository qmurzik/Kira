package app.pulse.client.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.UnavailableBox
import coil.compose.AsyncImage

private val EMOJI = (
    "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 " +
        "😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 " +
        "😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 😹 😻 😼 " +
        "👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 🙏 💪 👋 🤝 ✍️ 👀 🧠 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 💕 💞 💓 💗 💖 💘 💯 " +
        "🔥 ✨ ⭐ 🌟 💫 ⚡ ☀️ 🌈 ☁️ ❄️ 🌸 🌹 🌻 🍀 🍁 🎉 🎊 🎁 🎂 🍰 🍕 🍔 🍟 🍩 🍪 ☕ 🍺 🍷 🥂 ⚽ 🏀 🎮 🎧 🎵 " +
        "🚗 ✈️ 🏠 💻 📱 📷 💡 📚 ✏️ 📌 ✅ ❌ ❓ ❗ 💤 🙈 🙉 🙊 🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵"
    ).split(' ').filter { it.isNotBlank() }

@Composable
fun EmojiStickerPanel(
    stickers: StickersState,
    onEmoji: (String) -> Unit,
    onSticker: (Long) -> Unit,
    onNeedStickers: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().height(280.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Эмодзи") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Стикеры") })
        }
        if (tab == 0) {
            LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), contentPadding = PaddingValues(6.dp)) {
                items(EMOJI, key = { it }) { e ->
                    Box(
                        Modifier.aspectRatio(1f).clickable { onEmoji(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
        } else {
            LaunchedEffect(Unit) { onNeedStickers() }
            when (stickers) {
                StickersState.Idle, StickersState.Loading -> LoadingBox()
                is StickersState.Unavailable -> UnavailableBox(
                    "Отправка стикеров",
                    "Список наборов (store.getProducts) не выдан этому приложению: ${stickers.reason}",
                )
                is StickersState.Ready -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(72.dp),
                    contentPadding = PaddingValues(6.dp),
                ) {
                    stickers.packs.forEach { pack ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "pack_${pack.id}") {
                            Text(
                                pack.title ?: "Набор",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                        items(pack.stickers, key = { "s_${pack.id}_${it.stickerId}" }) { s ->
                            val url = s.images.filter { it.width <= 128 }.maxByOrNull { it.width }?.url ?: s.images.firstOrNull()?.url
                            AsyncImage(
                                model = url,
                                contentDescription = "Стикер",
                                modifier = Modifier.aspectRatio(1f).padding(4.dp).clickable { onSticker(s.stickerId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
