# kotlinx.serialization: keep generated serializers of @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp ships its own rules; silence optional platform warnings.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Strip verbose logging from release builds (tokens are never logged anyway).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
