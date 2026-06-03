# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
