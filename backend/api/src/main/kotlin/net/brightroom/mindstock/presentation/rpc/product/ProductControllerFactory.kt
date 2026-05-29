package net.brightroom.mindstock.presentation.rpc.product

import io.ktor.server.application.ApplicationCall

fun interface ProductControllerFactory {
    fun create(call: ApplicationCall): ProductController
}
