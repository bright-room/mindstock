rootProject.name = "mindstock"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-rpc/maven")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-rpc/maven")
    }
}

// composite build for convention plugins
includeBuild("build-logic")

include(
    ":shared",
    ":domain",
    ":application",
    ":infrastructure",
    ":backend",
    ":frontend",
)
