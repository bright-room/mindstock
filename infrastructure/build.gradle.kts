plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

tasks.withType<Test>().configureEach {
    // On macOS with a non-default Docker context, Testcontainers cannot
    // auto-detect the socket. The socket is at /var/run/docker.sock on both
    // macOS Docker Desktop (via symlink) and GitHub Actions Ubuntu runners.
    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    // tc.host is used by TestcontainersHostPropertyClientProviderStrategy
    // (highest priority) to locate the Docker socket reliably.
    jvmArgs(
        "-Dtc.host=unix:///var/run/docker.sock",
        "-Dtestcontainers.dockerhost=unix:///var/run/docker.sock",
        "-Dapi.version=1.41",
    )
    // Ryuk (resource reaper) uses a Docker API call that fails with the
    // testcontainers BadRequestException on Docker Desktop when the socket
    // path is not correctly resolved before strategy selection. Disable Ryuk
    // to avoid that initialisation race; containers are still stopped when
    // the JVM exits via GenericContainer's shutdown hook.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    // Exclude tests tagged "manual" by default. Override on the command line with
    // -Dkotest.tags.exclude= (empty) to include them when intentionally running
    // GenerateInitialMigrationManually or similar maintenance specs.
    val kotestTagsExclude = providers.systemProperty("kotest.tags.exclude").orElse("manual")
    systemProperty("kotest.tags.exclude", kotestTagsExclude.get())
}

dependencies {
    implementation(projects.domain)
    implementation(projects.backend.application)
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.annotation)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    api(libs.hikari)
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
