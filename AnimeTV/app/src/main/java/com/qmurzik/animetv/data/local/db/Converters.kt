package com.qmurzik.animetv.data.local.db

import androidx.room.TypeConverter
import com.qmurzik.animetv.domain.model.AnimeFormat
import com.qmurzik.animetv.domain.model.SourceRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class Converters {

    @TypeConverter
    fun sourceRefsToJson(refs: List<SourceRef>): String = json.encodeToString(refs)

    @TypeConverter
    fun sourceRefsFromJson(value: String): List<SourceRef> =
        runCatching { json.decodeFromString<List<SourceRef>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun formatToString(format: AnimeFormat): String = format.name

    @TypeConverter
    fun formatFromString(value: String): AnimeFormat =
        runCatching { AnimeFormat.valueOf(value) }.getOrDefault(AnimeFormat.UNKNOWN)
}
