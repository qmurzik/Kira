# AnimeTV ProGuard / R8 rules.
# Most androidx libraries ship consumer rules; the entries below cover the
# reflection-based bits (kotlinx.serialization models, Room, Media3) that R8
# cannot infer on its own.

# Keep line numbers for readable crash reports, but hide the file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep serializer() companions and @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.qmurzik.animetv.**$$serializer { *; }
-keepclassmembers class com.qmurzik.animetv.** {
    *** Companion;
}
-keepclasseswithmembers class com.qmurzik.animetv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities/DAOs are referenced via generated code; keep annotations.
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer keep rules (extractor/decoder reflection).
-dontwarn com.google.android.exoplayer2.**
-keep class androidx.media3.decoder.** { *; }

# Retrofit / OkHttp on R8: keep generic signatures & annotations used for reflection.
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Hilt / Dagger generated code is kept automatically by its own consumer rules.
