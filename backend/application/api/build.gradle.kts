plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("net.brightroom.mindstock.kotlin-jvm-testcontainers")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
    `java-test-fixtures`
}

application {
    mainClass.set("net.brightroom.mindstock.MainKt")
}

dependencies {
    implementation(projects.shared.rpc)
    implementation(projects.shared.extensions)
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
    implementation(ktorLib.server.doubleReceive)
    implementation(ktorLib.server.statusPages)
    implementation(ktorLib.server.callId)
    implementation(ktorLib.server.callLogging)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    testFixturesImplementation(projects.domain)
    testFixturesImplementation(projects.backend.infrastructure.schemas)
    testFixturesImplementation(projects.backend.infrastructure.migration.executor)
    testFixturesImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.testcontainers.postgres)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(ktorLib.server.testHost)
    testImplementation(libs.mockk)
}
