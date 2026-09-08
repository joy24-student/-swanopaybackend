# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve enough metadata for crash reports and libraries that inspect
# annotations while still allowing R8 to optimize application code.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.migration.Migration
-keep class com.example.data.local.** { *; }
-dontwarn androidx.room.paging.**

# --- Moshi & JSON Serialization ---
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class com.example.data.remote.** { *; }

# --- Retrofit & OkHttp ---
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# --- ML Kit & CameraX ---
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# --- AndroidX Security & Crypto ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# --- Kotlin Coroutines ---
-keepclassmembers class kotlinx.coroutines.** { *; }

