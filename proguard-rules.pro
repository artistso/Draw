# Animation Studio ProGuard Rules

# Keep data models
-keep class com.animationstudio.data.models.** { *; }
-keep class com.animationstudio.engine.** { *; }
-keep class com.animationstudio.ai.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
