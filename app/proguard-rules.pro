# kotlinx.serialization — keep generated $$serializer companions and
# any class annotated @Serializable plus its companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.helloworld.**$$serializer { *; }
-keepclassmembers class com.example.helloworld.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.helloworld.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / Gson — interface methods + model classes use reflection.
-keepattributes Signature, Exceptions, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**

# Coroutines.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Ktor uses some reflection internally.
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.** { *; }

# OkHttp.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# SLF4J — Mapbox transitively pulls slf4j-api but no impl on Android; the
# StaticLoggerBinder lookup is dead at runtime. R8 sees the reference and
# fails the build without this dontwarn.
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**

# Mudita MMD components — keep public API surface.
-keep class com.mudita.mmd.** { *; }
-dontwarn com.mudita.mmd.**

# Places SDK + Mapbox SDKs ship their own consumer rules; do not strip
# anything reflection-loaded.
-keep class com.google.android.libraries.places.** { *; }
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**
