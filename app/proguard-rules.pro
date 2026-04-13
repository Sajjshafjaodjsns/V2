# Jsoup
-keep public class org.jsoup.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(); }

# Общие правила
-dontwarn okhttp3.**
-dontwarn java.lang.invoke.**
