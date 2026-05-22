plugins {
    id("mindstock.compose-web")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        val webMain by getting {
            dependencies {
                implementation(projects.shared)

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.rpc.client)
                implementation(libs.kotlinx.rpc.client.ktor)
                implementation(libs.ktor.client.core)
            }
        }
    }
}
