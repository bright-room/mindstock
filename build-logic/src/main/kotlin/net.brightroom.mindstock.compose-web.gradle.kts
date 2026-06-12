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
        // webMain は Kotlin default hierarchy template の web グループ(js + wasmJs の親)。
        // 両ターゲット共通のブラウザコード置き場。webMain→{jsMain,wasmJsMain} の dependsOn は
        // 自動適用される default hierarchy template により暗黙に張られる(明示の dependsOn は不要)。
        val webMain by creating {}
        jsMain {}
        wasmJsMain {}

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
