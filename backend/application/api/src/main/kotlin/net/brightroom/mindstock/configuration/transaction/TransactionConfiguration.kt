package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.transactionConfigure() {
    val database: Database by dependencies
    install(ExposedTransactionPlugin) {
        this.database = database
    }
}
