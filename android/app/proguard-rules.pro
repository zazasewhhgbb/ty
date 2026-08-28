# Add project specific ProGuard rules here.
# minifyEnabled is currently false in release builds (see app/build.gradle.kts),
# so these rules aren't exercised yet — kept as a starting point for when
# you turn minification on for a real release build.

-keepattributes Signature
-keepattributes *Annotation*

# Retrofit / OkHttp / Gson models
-keep class com.voicegen.app.data.remote.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
