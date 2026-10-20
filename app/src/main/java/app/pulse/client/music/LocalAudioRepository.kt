package app.pulse.client.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
) {
    val artwork: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

/** Music files on the device (MediaStore). Requires READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE. */
class LocalAudioRepository(private val context: Context) {

    suspend fun tracks(): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val result = ArrayList<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    title = c.getString(titleCol) ?: "Без названия",
                    artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Неизвестный исполнитель",
                    album = c.getString(albumCol) ?: "",
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                )
            }
        }
        result
    }
}
