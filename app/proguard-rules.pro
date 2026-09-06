# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase

# Moshi & JSON Serialization
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Lingo-AI Data Models (DB and API)
-keep class com.example.data.** { *; }
-keep class com.example.data.api.** { *; }
-keepclassmembers class com.example.data.** { *; }

# Retain generic signatures for Moshi
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# Compose
-keep class androidx.compose.** { *; }
