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
        // Garmin Connect IQ SDK repository
        maven { url = uri("https://developer.garmin.com/downloads/connect-iq/sdks/") }
    }
}

rootProject.name = "GarminActivityStreaming"
include(":app")
