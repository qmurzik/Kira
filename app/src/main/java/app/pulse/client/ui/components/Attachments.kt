package app.pulse.client.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pulse.client.core.util.Format
import app.pulse.client.domain.model.Attachment
import coil.compose.AsyncImage

/** Callbacks shared by posts, comments and messages. */
data class AttachmentActions(
    val onPhoto: (String) -> Unit = {},
    val onVideo: (String) -> Unit = {},
    val onWall: (Long, Long) -> Unit = { _, _ -> },
    val onVoice: (Attachment.Voice) -> Unit = {},
)

/** Voice playback state provided by the screen that owns the player. */
data class VoiceState(val playingKey: String? = null, val progress: Float = 0f)

fun Attachment.Voice.key() = "${ownerId}_$id"

@Composable
fun Attachments(
    items: List<Attachment>,
    actions: AttachmentActions,
    modifier: Modifier = Modifier,
    voice: VoiceState = VoiceState(),
    maxWidthFraction: Float = 1f,
) {
    if (items.isEmpty()) return
    val photos = items.filterIsInstance<Attachment.Photo>()
    val others = items.filter { it !is Attachment.Photo }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (photos.isNotEmpty()) PhotoGrid(photos, actions.onPhoto, Modifier.fillMaxWidth(maxWidthFraction))
        others.forEach { a ->
            when (a) {
                is Attachment.Video -> VideoThumb(a, onClick = { actions.onVideo(a.apiId) })
                is Attachment.Doc -> DocRow(a)
                is Attachment.Link -> LinkCard(a)
                is Attachment.Sticker -> AsyncImage(
                    model = a.url,
                    contentDescription = "Стикер",
                    modifier = Modifier.size(128.dp),
                )
                is Attachment.Voice -> VoiceMessage(
                    a,
                    playing = voice.playingKey == a.key(),
                    progress = if (voice.playingKey == a.key()) voice.progress else 0f,
                    onToggle = { actions.onVoice(a) },
                )
                is Attachment.Audio -> IconRow(Icons.Outlined.MusicNote, "${a.artist} — ${a.title}", Format.duration(a.duration))
                is Attachment.Poll -> PollView(a)
                is Attachment.WallPost -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { actions.onWall(a.post.ownerId, a.post.id) },
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Запись: ${a.post.author.name}", style = MaterialTheme.typography.labelMedium)
                        if (a.post.text.isNotBlank()) {
                            Text(a.post.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                is Attachment.Unknown -> Text(
                    "Вложение «${a.type}» не поддерживается",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is Attachment.Photo -> Unit
            }
        }
    }
}

@Composable
fun PhotoGrid(photos: List<Attachment.Photo>, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    if (photos.size == 1) {
        val p = photos[0]
        val ratio = if (p.width > 0 && p.height > 0) (p.width.toFloat() / p.height).coerceIn(0.6f, 2f) else 1.33f
        AsyncImage(
            model = p.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(ratio).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick(p.url) },
        )
        return
    }
    // 2 per row, last row may hold 1 or 3 for odd counts.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        photos.take(10).chunked(if (photos.size == 3) 3 else 2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { p ->
                    AsyncImage(
                        model = p.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onClick(p.url) },
                    )
                }
            }
        }
    }
}

@Composable
fun VideoThumb(v: Attachment.Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
            .background(Color.Black).clickable(onClick = onClick),
    ) {
        AsyncImage(model = v.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color(0x99000000)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Смотреть", tint = Color.White) }
        if (v.duration > 0) {
            Text(
                Format.duration(v.duration),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Text(
            v.title,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp, end = 64.dp),
        )
    }
}

@Composable
private fun DocRow(d: Attachment.Doc) {
    val context = LocalContext.current
    IconRow(
        Icons.Outlined.Description,
        d.title,
        "${d.ext.uppercase()} · ${Format.fileSize(d.size)}",
        onClick = d.url?.let { url -> { openUrl(context, url) } },
    )
}

@Composable
private fun LinkCard(l: Attachment.Link) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { openUrl(context, l.url) },
    ) {
        Column {
            if (l.photo != null) {
                AsyncImage(
                    model = l.photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).aspectRatio(2f),
                )
            }
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (l.photo == null) {
                    Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(l.title ?: l.url, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        l.caption ?: Uri.parse(l.url).host.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PollView(p: Attachment.Poll) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Poll, null, Modifier.size(18.dp))
                Text(p.question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
            }
            p.answers.forEach { a ->
                Column {
                    Row {
                        Text(a.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${a.rate.toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { (a.rate / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
            Text(Format.plural(p.votes, "голос", "голоса", "голосов"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun VoiceMessage(v: Attachment.Voice, playing: Boolean, progress: Float, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(accent).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Пауза" else "Слушать",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            val bars = remember(v.waveform) { normalizeWave(v.waveform) }
            Canvas(Modifier.padding(horizontal = 8.dp).width(140.dp).height(28.dp)) {
                val step = size.width / bars.size
                val w = (step * 0.6f).coerceAtLeast(1.5f)
                bars.forEachIndexed { i, h ->
                    val barH = (h * size.height).coerceAtLeast(2f)
                    drawRoundRect(
                        color = if (i.toFloat() / bars.size <= progress) accent else muted,
                        topLeft = Offset(i * step, (size.height - barH) / 2),
                        size = Size(w, barH),
                        cornerRadius = CornerRadius(w / 2, w / 2),
                    )
                }
            }
            Text(Format.duration(v.duration), style = MaterialTheme.typography.labelSmall)
        }
        if (!v.transcript.isNullOrBlank()) {
            Text(
                v.transcript,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun normalizeWave(wave: List<Int>): List<Float> {
    if (wave.isEmpty()) return List(32) { 0.3f }
    val target = 40
    val chunk = (wave.size / target).coerceAtLeast(1)
    val reduced = wave.chunked(chunk).map { it.average().toFloat() }.take(target)
    val max = reduced.maxOrNull()?.takeIf { it > 0 } ?: 1f
    return reduced.map { (it / max).coerceIn(0.08f, 1f) }
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
