plugins {
    id("mindstock.kotlin-jvm")
    application
}

application {
    // Override in module if needed
    mainClass.set("net.brightroom.mindstock.backend.MainKt")
}
