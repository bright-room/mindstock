plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.backend.core)
    implementation(projects.domain)
    implementation(projects.rpc)
    implementation(projects.shared)

    implementation(ktorLib.server.core)
    implementation(ktorLib.server.cio)
    implementation(ktorLib.server.di)
    implementation(ktorLib.server.config.yaml)
    implementation(ktorLib.server.websockets)
    implementation(ktorLib.server.contentNegotiation)
    implementation(ktorLib.serialization.kotlinx.json)
    implementation(libs.kotlinx.rpc.server)
    implementation(libs.kotlinx.rpc.server.ktor)
    implementation(libs.kotlinx.rpc.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.hikari)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.auth0.java.jwt)
    implementation(libs.auth0.jwks.rsa)
    implementation(libs.aws.sdk.kotlin.s3)

    testFixturesImplementation(projects.domain)
    testFixturesImplementation(testFixtures(projects.backend.core))
    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.flyway.database.postgresql)
    testFixturesImplementation(libs.postgres.jdbc)
    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(testFixtures(projects.backend.core))
    testImplementation(testFixtures(projects.backend.api))
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

// 注: api の integrationTest は現状 @Tags("integration") のテストが 0 件で空実行(将来の e2e 受け皿)。
// タスク定義は kotlin-jvm convention(ktor-server 経由で継承)に集約済み。
