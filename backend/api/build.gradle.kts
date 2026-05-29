plugins {
    id("net.brightroom.mindstock.ktor-server")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
    `java-test-fixtures`
}

application {
    mainClass.set("net.brightroom.mindstock.MainKt")
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
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
    testImplementation(ktorLib.server.testHost)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.rpc.client)
    testImplementation(libs.kotlinx.rpc.client.ktor)
    testImplementation(ktorLib.client.cio)
    testImplementation(ktorLib.client.websockets)
    testImplementation(ktorLib.client.contentNegotiation)
}

tasks.test {
    useJUnitPlatform()
    // Exclude "integration" and "manual" tagged specs by default.
    // Override on the command line with -Dkotest.tags.exclude= (empty string) to run all.
    systemProperty("kotest.tags.exclude", "integration | manual")
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against TEST_DB_URL."
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    // Run only "integration" tagged specs.
    systemProperty("kotest.tags.include", "integration")
    // Exclude "manual" maintenance specs.
    systemProperty("kotest.tags.exclude", "manual")
    // Forward TEST_DB_* env vars to the test JVM
    listOf("TEST_DB_URL", "TEST_DB_USER", "TEST_DB_PASSWORD").forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
}

tasks.check {
    dependsOn(integrationTest)
}
