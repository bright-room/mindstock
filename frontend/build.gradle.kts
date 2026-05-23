plugins {
    id("net.brightroom.mindstock.compose-web")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.rpc)

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
