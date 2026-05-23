@file:Suppress("UnstableApiUsage")

rootProject.name = "mindstock"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// composite build for convention plugins
includeBuild("build-logic")

include(
    ":shared",
    ":rpc",
    ":domain",
    ":backend:application",
    ":backend:infrastructure:schemas",
    ":backend:infrastructure:migration:annotation",
    ":backend:infrastructure:migration:detector",
    ":backend:infrastructure:migration:generator",
    ":backend:infrastructure:migration:executor",
    ":infrastructure",
    ":backend:api",
    ":frontend",
)
