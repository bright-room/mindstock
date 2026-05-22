plugins {
    id("mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

application {
    mainClass.set("mindstock.backend.MainKt")
}

dependencies {
    implementation(projects.shared)
    implementation(projects.domain)
    implementation(projects.application)
    implementation(projects.infrastructure)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.server.test.host)
}
