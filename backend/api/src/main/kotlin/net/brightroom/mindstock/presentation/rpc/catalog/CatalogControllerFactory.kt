package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.configuration.auth.MindstockSession

fun interface CatalogControllerFactory {
    fun create(session: MindstockSession): CatalogController
}
