plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

application {
    mainClass.set("net.brightroom.mindstock.MainKt")
}

dependencies {
    implementation(projects.shared.rpc)
    implementation(projects.domain)
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.executor)

    implementation(ktorLib.server.core)
    implementation(ktorLib.server.cio)
    implementation(ktorLib.server.di)
    implementation(ktorLib.server.config.yaml)
    implementation(ktorLib.server.websockets)
    implementation(ktorLib.server.auth)
    implementation(ktorLib.server.auth.jwt)
    implementation(ktorLib.server.contentNegotiation)
    implementation(ktorLib.serialization.kotlinx.json)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(ktorLib.server.testHost)
}
