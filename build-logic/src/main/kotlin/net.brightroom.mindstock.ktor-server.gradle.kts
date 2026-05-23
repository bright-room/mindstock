plugins {
    id("net.brightroom.mindstock.kotlin-jvm")
    application
}

application {
    // Override in module if needed
    mainClass.set("net.brightroom.mindstock.MainKt")
}
