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

// Kotlin/JS・Wasm のビルドツールチェーン(webpack 系)が引き込む transitive npm 依存の
// 既知脆弱性に対し、patched バージョンへ強制する yarn resolution。
// 本番成果物には含まれないビルド時依存だが、Dependabot アラート解消のため明示的に固定する。
plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply {
        resolution("http-proxy-middleware", "2.0.10")
        resolution("webpack-dev-server", "5.2.5")
        resolution("webpack", "5.104.1")
        resolution("ws", "8.21.0")
        resolution("serialize-javascript", "7.0.5")
        resolution("uuid", "11.1.1")
        resolution("diff", "8.0.3")
    }
}

plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootExtension>().apply {
        resolution("ws", "8.21.0")
    }
}
