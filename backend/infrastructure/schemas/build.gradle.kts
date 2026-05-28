plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    alias(libs.plugins.exposed.migration)
}

dependencies {
    implementation(projects.domain)
    implementation(libs.exposed.core)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.jdbc)
}

exposed {
    migrations {
        tablesPackage.set("net.brightroom.mindstock.infrastructure.datasource.schemas")
        fileDirectory.set(
            rootProject.layout.projectDirectory
                .dir("backend/application/api/src/main/resources/db/migration")
                .asFile,
        )
        testContainersImageName.set("postgres:18.0-alpine")
    }
}
