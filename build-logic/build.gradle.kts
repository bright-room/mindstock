plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.compose.compiler)
    implementation(libs.plugin.compose.multiplatform)
    implementation(libs.plugin.ktor)
    implementation(libs.plugin.kotlinx.rpc)
    implementation(libs.plugin.spotless)
}
