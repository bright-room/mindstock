package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface ProductControllerFactory {
    fun create(session: MindstockSession): ProductController
}
