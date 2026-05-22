plugins {
    id("mindstock.kotlin-jvm")
}

dependencies {
    implementation(projects.domain)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
