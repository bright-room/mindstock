package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface StockControllerFactory {
    fun create(session: MindstockSession): StockController
}
