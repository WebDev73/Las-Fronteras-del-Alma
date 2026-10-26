// Plugins are put on the root classpath through `buildscript` (rather than the
// plugins DSL) so the Android Gradle Plugin is only resolved when an Android SDK
// is present. KGP and AGP must share a classloader, hence the root declaration.
buildscript {
    val kotlinVersion = "2.3.21"
    val agpVersion = "8.13.0"
    val androidEnabled = gradle.extra["androidEnabled"] as Boolean
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        if (androidEnabled) {
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
            classpath("com.android.tools.build:gradle:$agpVersion")
        }
    }
}
