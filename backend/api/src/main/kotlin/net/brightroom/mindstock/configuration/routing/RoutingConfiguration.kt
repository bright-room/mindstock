@file:OptIn(ExperimentalSerializationApi::class)

package net.brightroom.mindstock.configuration.routing

import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import net.brightroom.mindstock.configuration.Environment
import net.brightroom.mindstock.extensions.kotlinx.serialization.CustomJson

fun Application.routingConfigure(
    @Property("ktor.environment") environment: Environment,
) {
    install(ContentNegotiation) {
        jsonIo(CustomJson)
    }

    routing {
        get("/health") { call.respondText("OK") }
    }
}
