import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinx.rpc) apply false
    alias(libs.plugins.exposed.migration) apply false
}

// Kotlin/JS・Wasm ツールチェーンが生成する yarn.lock 内の脆弱な npm 依存を
// Yarn resolutions で修正版へ固定する。OSV(security ゲート)が kotlin-js-store/**/yarn.lock で
// 検出した既知脆弱性への対処。lockfile は `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` で再生成する。
// 各バージョンは OSV が提示する fixed version(複数該当は最大値)を採用。
val jsYarnResolutions =
    mapOf(
        "ws" to "8.21.0", // GHSA-96hv-2xvq-fx4p / GHSA-58qx-3vcg-4xpx
        "diff" to "8.0.3", // GHSA-73rr-hh4g-fpgx
        "http-proxy-middleware" to "2.0.10", // GHSA-64mm-vxmg-q3vj
        "serialize-javascript" to "7.0.5", // GHSA-5c6j-r48x-rmvq / GHSA-qj8w-gfj5-8c6v
        "uuid" to "11.1.1", // GHSA-w5hq-g745-h8pq
        "webpack" to "5.104.1", // GHSA-38r7-794h-5758 / GHSA-8fgc-7cc6-rx7x
        "webpack-dev-server" to "5.2.6", // GHSA-79cf-xcqc-c78w / GHSA-mx8g-39q3-5c79 / GHSA-f5vj-f2hx-8m93 / GHSA-m28w-2pqf-7qgj
        "body-parser" to "1.20.6", // GHSA-v422-hmwv-36x6
        // 1.x(minimatch@3 経由)と 2.x(minimatch@9 経由)が併存するが、
        // yarn v1 の resolutions はバージョン別に書き分けられないため 2.x に一本化する。
        // brace-expansion 2.x は 1.x と同一の API(expand)で drop-in 互換。
        "brace-expansion" to "2.1.2", // GHSA-3jxr-9vmj-r5cp
        "fast-uri" to "3.1.4", // GHSA-4c8g-83qw-93j6 / GHSA-v2hh-gcrm-f6hx
        "js-yaml" to "4.3.0", // GHSA-52cp-r559-cp3m
        "shell-quote" to "1.9.0", // GHSA-395f-4hp3-45gv
    )

// wasm の yarn.lock には ws のみ該当する。
val wasmYarnResolutions =
    mapOf(
        "ws" to "8.21.0", // GHSA-96hv-2xvq-fx4p / GHSA-58qx-3vcg-4xpx
    )

plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply {
        jsYarnResolutions.forEach { (name, version) -> resolution(name, version) }
    }
}

plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootExtension>().apply {
        wasmYarnResolutions.forEach { (name, version) -> resolution(name, version) }
    }
}
