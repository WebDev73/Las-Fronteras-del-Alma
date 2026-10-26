pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fronteras"

include(":core", ":server")

// The Android module needs an Android SDK. CI runners provide one; containers
// without it can still build and test :core and :server.
val localSdkDir = file("local.properties").takeIf { it.exists() }
    ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
val androidSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: localSdkDir
val androidEnabled = androidSdk != null && file(androidSdk).isDirectory &&
    providers.gradleProperty("fronteras.android").orNull != "false"
gradle.extra["androidEnabled"] = androidEnabled
if (androidEnabled) include(":app")
