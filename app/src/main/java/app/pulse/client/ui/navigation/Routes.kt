package app.pulse.client.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.ui.graphics.vector.ImageVector

/** String routes (simple, no reflection). Arguments are always URL-encoded. */
object Routes {
    const val FEED = "feed"
    const val SERVICES = "services"
    const val MESSAGES = "messages"
    const val CLIPS = "clips"
    const val VIDEO = "video"

    const val CHAT = "chat/{peerId}"
    const val FORWARD_PICK = "forward"
    const val POST = "post/{ownerId}/{postId}"
    const val CREATE_POST = "create_post"
    const val STORIES = "stories/{index}"
    const val PROFILE = "profile/{id}"
    const val FRIENDS = "friends/{id}"
    const val GROUPS = "groups/{id}"
    const val PHOTOS = "photos/{id}"
    const val PHOTO_VIEWER = "photo_viewer?url={url}"
    const val WALL_ONLY = "wall/{id}"
    const val VIDEO_PLAYER = "video_player/{videoId}"
    const val MUSIC = "music"
    const val NOTIFICATIONS = "notifications"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ACCOUNTS = "accounts"
    const val ADD_ACCOUNT = "add_account"
    const val BIRTHDAYS = "birthdays"
    const val PIN_SETUP = "pin_setup"

    fun chat(peerId: Long) = "chat/$peerId"
    fun post(ownerId: Long, postId: Long) = "post/$ownerId/$postId"
    fun stories(index: Int) = "stories/$index"
    fun profile(id: Long) = "profile/$id"
    fun friends(id: Long) = "friends/$id"
    fun groups(id: Long) = "groups/$id"
    fun photos(id: Long) = "photos/$id"
    fun photoViewer(url: String) = "photo_viewer?url=${Uri.encode(url)}"
    fun videoPlayer(apiId: String) = "video_player/${Uri.encode(apiId)}"
}

enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Feed(Routes.FEED, "Главная", Icons.Outlined.Home, Icons.Filled.Home),
    Services(Routes.SERVICES, "Сервисы", Icons.Outlined.Apps, Icons.Filled.Apps),
    Messages(Routes.MESSAGES, "Мессенджер", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    Clips(Routes.CLIPS, "Клипы", Icons.Outlined.Slideshow, Icons.Filled.Slideshow),
    Video(Routes.VIDEO, "Видео", Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
}
