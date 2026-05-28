plugins {
    id("net.brightroom.mindstock.kotlin-jvm-testcontainers")
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.detector)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.postgres.jdbc)
    implementation(libs.hikari)

    testImplementation(testFixtures(projects.backend.infrastructure.migration.executor))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
