package net.brightroom.mindstock.presentation.rpc.stock

import io.ktor.server.application.ApplicationCall

fun interface StockControllerFactory {
    fun create(call: ApplicationCall): StockController
}
