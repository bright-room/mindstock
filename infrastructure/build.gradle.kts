plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

tasks.withType<Test>().configureEach {
    // Testcontainers needs to locate the Docker socket. On macOS with Docker
    // Desktop the socket lives at ~/.docker/run/docker.sock but is also
    // symlinked from /var/run/docker.sock. Pass the location explicitly so
    // the test worker JVM picks it up regardless of the active Docker context.
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    // tc.host is read by TestcontainersHostPropertyClientProviderStrategy (highest priority = 90)
    jvmArgs(
        "-Dtc.host=unix:///var/run/docker.sock",
        "-Dtestcontainers.dockerhost=unix:///var/run/docker.sock",
        "-Dapi.version=1.41",
    )
}

dependencies {
    implementation(projects.domain)
    implementation(projects.application)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    implementation(libs.hikari)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging.jvm)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
