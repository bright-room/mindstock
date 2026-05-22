plugins {
    id("org.jetbrains.kotlin.jvm")
    id("mindstock.spotless")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
