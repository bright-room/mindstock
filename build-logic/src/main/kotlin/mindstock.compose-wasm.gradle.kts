import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("mindstock.spotless")
}

kotlin {
    jvmToolchain(21)

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "mindstock-frontend"
        browser {
            commonWebpackConfig {
                outputFileName = "mindstock-frontend.js"
            }
        }
        binaries.executable()
    }
}
