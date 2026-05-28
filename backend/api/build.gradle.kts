plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

application {
    mainClass.set("net.brightroom.mindstock.MainKt")
}

dependencies {
    implementation(projects.backend.core)
    implementation(projects.domain)
    implementation(projects.shared.rpc)
    implementation(projects.shared.extensions)

    implementation(ktorLib.server.core)
    implementation(ktorLib.server.cio)
    implementation(ktorLib.server.di)
    implementation(ktorLib.server.config.yaml)
    implementation(ktorLib.server.websockets)
    implementation(ktorLib.server.auth)
    implementation(ktorLib.server.auth.jwt)
    implementation(ktorLib.server.contentNegotiation)
    implementation(ktorLib.serialization.kotlinx.json)
    implementation(ktorLib.server.doubleReceive)
    implementation(ktorLib.server.statusPages)
    implementation(ktorLib.server.callId)
    implementation(ktorLib.server.callLogging)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.kotlinx.rpc.serialization.json)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    testImplementation(testFixtures(projects.backend.core))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(ktorLib.server.testHost)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.rpc.client)
    testImplementation(libs.kotlinx.rpc.client.ktor)
    testImplementation(ktorLib.client.cio)
    testImplementation(ktorLib.client.websockets)
    testImplementation(ktorLib.client.contentNegotiation)
}
