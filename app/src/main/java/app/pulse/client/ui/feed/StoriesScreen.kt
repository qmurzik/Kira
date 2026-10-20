package app.pulse.client.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.client.core.util.Format
import app.pulse.client.domain.model.StoryGroup
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.video.InlineVideo
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Full-screen stories viewer: horizontal pager of authors, tap left/right to step, auto-advance. */
@Composable
fun StoriesScreen(vm: FeedViewModel, startIndex: Int) {
    val nav = LocalNavigator.current
    val groups by vm.stories.collectAsStateWithLifecycle()
    BackHandler { nav.back() }
    if (groups.isEmpty()) {
        MessageBox("Историй нет")
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, groups.size - 1)) { groups.size }
    val scope = rememberCoroutineScope()
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black)) { page ->
        StoryGroupView(
            group = groups[page],
            active = pager.currentPage == page,
            onSeen = { i -> vm.markSeen(page, i) },
            onFinished = {
                if (page + 1 < groups.size) scope.launch { pager.animateScrollToPage(page + 1) } else nav.back()
            },
            onBackFromFirst = {
                if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) }
            },
            onClose = { nav.back() },
        )
    }
}

@Composable
private fun StoryGroupView(
    group: StoryGroup,
    active: Boolean,
    onSeen: (Int) -> Unit,
    onFinished: () -> Unit,
    onBackFromFirst: () -> Unit,
    onClose: () -> Unit,
) {
    var index by remember(group.owner.id) { mutableIntStateOf(group.stories.indexOfFirst { !it.seen }.coerceAtLeast(0)) }
    var progress by remember(group.owner.id, index) { mutableFloatStateOf(0f) }
    val story = group.stories[index.coerceIn(0, group.stories.size - 1)]

    LaunchedEffect(active, index) {
        if (!active) return@LaunchedEffect
        onSeen(index)
        if (story.videoUrl != null) return@LaunchedEffect // video advances on completion
        val total = 5_000L
        val start = System.currentTimeMillis()
        while (isActive) {
            progress = ((System.currentTimeMillis() - start).toFloat() / total).coerceAtMost(1f)
            if (progress >= 1f) break
            delay(50)
        }
        if (index + 1 < group.stories.size) index++ else onFinished()
    }

    Box(
        Modifier.fillMaxSize().pointerInput(group.owner.id) {
            detectTapGestures { offset ->
                if (offset.x < size.width / 3) {
                    if (index > 0) index-- else onBackFromFirst()
                } else {
                    if (index + 1 < group.stories.size) index++ else onFinished()
                }
            }
        },
    ) {
        if (story.videoUrl != null && active) {
            InlineVideo(
                url = story.videoUrl,
                modifier = Modifier.fillMaxSize(),
                onEnded = { if (index + 1 < group.stories.size) index++ else onFinished() },
                onProgress = { progress = it },
            )
        } else {
            AsyncImage(
                model = story.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                group.stories.forEachIndexed { i, _ ->
                    LinearProgressIndicator(
                        progress = { if (i < index) 1f else if (i == index) progress else 0f },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.weight(1f).height(2.dp),
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(group.owner.photo, group.owner.name, 32.dp)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(group.owner.name, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Text(Format.postDate(story.date), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White) }
            }
        }
    }
}
