plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.backend.infrastructure.schemas)
    implementation(projects.backend.infrastructure.migration.annotation)

    implementation(libs.exposed.core)
}
