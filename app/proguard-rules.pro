# kotlinx.serialization ships its own consumer rules. Keep our serializable model classes' companions.
-keepclassmembers @kotlinx.serialization.Serializable class app.fronteras.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
