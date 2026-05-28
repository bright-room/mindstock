package net.brightroom.mindstock.configuration.error

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.errorConfigure() {
    install(StatusPages) {
        exception<UnauthorizedException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, cause.message ?: "not found")
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "bad request")
        }
    }
}
