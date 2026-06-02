plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    alias(libs.plugins.exposed.migration)
    `java-test-fixtures`
}

dependencies {
    implementation(projects.shared)
    implementation(projects.domain)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hikari)
    implementation(libs.kotlin.logging.jvm)

    testFixturesImplementation(libs.exposed.core)
    testFixturesImplementation(libs.exposed.jdbc)
    testFixturesImplementation(libs.hikari)
    testFixturesImplementation(libs.postgres.jdbc)
    testFixturesImplementation(libs.flyway.core)
    testFixturesImplementation(libs.flyway.database.postgresql)
    testFixturesImplementation(libs.kotest.assertions.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}

exposed {
    migrations {
        tablesPackage.set("net.brightroom.mindstock.infrastructure.datasource.schemas")
        fileDirectory.set(
            layout.projectDirectory.dir("src/main/resources/db/migration").asFile,
        )
        testContainersImageName.set("postgres:18.0-alpine")
    }
}
