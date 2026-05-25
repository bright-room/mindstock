package net.brightroom.mindstock.configuration.transaction

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

/**
 * RPC message-scoped transaction boundary.
 *
 * kotlinx-rpc dispatches each RPC method as a WebSocket message over an
 * already-upgraded socket. The Ktor ApplicationCallPipeline fires only at
 * upgrade time, so [ExposedTransactionPlugin]'s call-pipeline interceptor
 * does not wrap individual RPC method invocations.
 *
 * Service Impl methods that touch the database MUST wrap their body in
 * this helper so each RPC method = 1 transaction.
 *
 * The `@Suppress("DEPRECATION")` mirrors the existing suppression on
 * [ExposedTransactionPlugin] — `newSuspendedTransaction` is deprecated in
 * Exposed v1 JDBC but its successor `suspendTransaction()` is r2dbc-only.
 * See ExposedTransactionPlugin.kt for the full rationale.
 */
@Suppress("DEPRECATION")
suspend fun <T> tx(
    database: Database,
    block: suspend () -> T,
): T = newSuspendedTransaction(db = database) { block() }
