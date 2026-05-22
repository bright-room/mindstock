package net.brightroom.mindstock.backend

import io.ktor.server.application.Application
import io.ktor.server.cio.EngineMain
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    routing {
        get("/health") {
            call.respondText("OK")
        }
    }
}
