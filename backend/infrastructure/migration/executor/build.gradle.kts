plugins {
    id("net.brightroom.mindstock.kotlin-jvm-testcontainers")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.detector)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
    implementation(libs.hikari)
    implementation(libs.postgres.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlin.logging.jvm)

    testFixturesImplementation(libs.testcontainers.postgres)
    testFixturesImplementation(libs.hikari)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
