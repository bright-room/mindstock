package net.brightroom.mindstock.configuration.transaction

import kotlinx.coroutines.supervisorScope
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
 *
 * The [supervisorScope] wrapper is load-bearing for RPC error handling:
 * `newSuspendedTransaction` runs the body inside its own internal
 * `TransactionScope.async { ... }` whose Job is a regular (non-supervisor)
 * child of the caller's Job. When the body throws, the `async` failure
 * normally propagates cancellation *upward* through the Job hierarchy — past
 * kRPC's own per-call `catch (Throwable)` — and surfaces asynchronously on
 * the server's connection scope. kRPC still serializes the exception to the
 * client correctly, but the leaked cancellation also brings down the
 * Ktor testApplication scope, causing tests to fail with the SQL exception
 * even when the client-side `shouldThrowAny { ... }` already caught it.
 * [supervisorScope] isolates that cancellation while still rethrowing the
 * exception to the caller (kRPC), preserving server→client error delivery.
 */
@Suppress("DEPRECATION")
suspend fun <T> tx(
    database: Database,
    block: suspend () -> T,
): T =
    supervisorScope {
        newSuspendedTransaction(db = database) { block() }
    }
