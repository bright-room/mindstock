package net.brightroom.mindstock.presentation.rpc.catalog

import io.ktor.server.application.ApplicationCall

fun interface CatalogControllerFactory {
    fun create(call: ApplicationCall): CatalogController
}
