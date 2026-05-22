import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("net.brightroom.mindstock.spotless")
}

kotlin {
    jvmToolchain(25)

    js(IR) {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val webMain by creating {}
        jsMain {}
        wasmJsMain {}

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
