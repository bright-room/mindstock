plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

tasks.withType<Test>().configureEach {
    // On macOS with a non-default Docker context, Testcontainers cannot
    // auto-detect the socket. The socket is at /var/run/docker.sock on both
    // macOS Docker Desktop (via symlink) and GitHub Actions Ubuntu runners.
    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    jvmArgs(
        "-Dtc.host=unix:///var/run/docker.sock",
        "-Dtestcontainers.dockerhost=unix:///var/run/docker.sock",
        "-Dapi.version=1.41",
    )
    // Ryuk fails on Docker Desktop when the socket path isn't resolved before
    // strategy selection. Containers are still stopped via GenericContainer's
    // JVM shutdown hook.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    // Exclude tests tagged "manual" by default. Override on the command line
    // with -Dkotest.tags.exclude= (empty) to run GenerateInitialMigrationManually
    // and similar maintenance specs.
    val kotestTagsExclude = providers.systemProperty("kotest.tags.exclude").orElse("manual")
    systemProperty("kotest.tags.exclude", kotestTagsExclude.get())
}
