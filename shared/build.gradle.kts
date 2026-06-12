plugins {
    id("net.brightroom.mindstock.kmp-shared")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }

        // webMain(default hierarchy template の web グループ = js + wasmJs の親)に
        // 一本化。両ターゲットへ伝播するため jsMain/wasmJsMain で重複宣言しない。
        webMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
    }
}
