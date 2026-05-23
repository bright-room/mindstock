plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.detector)

    implementation(libs.exposed.core)
    api(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.migration)
}
