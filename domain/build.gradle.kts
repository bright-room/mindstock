plugins {
    id("net.brightroom.mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlincrypto.crypto.rand)
        }
        commonTest.dependencies {
            implementation(libs.kotest.assertions.core)
        }
    }
}
