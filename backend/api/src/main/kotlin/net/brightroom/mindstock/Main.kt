package net.brightroom.mindstock

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, module = Application::healthModule).start(wait = true)
}

fun Application.healthModule() {
    routing {
        get("/health") { call.respondText("OK") }
    }
}
