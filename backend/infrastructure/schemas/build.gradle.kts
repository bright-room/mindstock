plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.backend.infrastructure.migration.annotation)

    implementation(libs.exposed.core)
    implementation(libs.exposed.kotlin.datetime)
    api(libs.exposed.jdbc)
}
