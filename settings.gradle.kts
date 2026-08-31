import org.gradle.api.JavaVersion
import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

require(JavaVersion.current() == JavaVersion.VERSION_21) {
    "NENE-PIXEL requires JDK 21 to run Gradle; current JVM is ${JavaVersion.current()}."
}

rootProject.name = "NENE-PIXEL"

include(":app:android")
