# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclasseswithmembers class com.streambert.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.streambert.tv.**$$serializer { *; }
-keepclassmembers class com.streambert.tv.** {
    *** Companion;
}

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
