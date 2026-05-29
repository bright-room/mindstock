@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

private val logger = KotlinLogging.logger {}

/**
 * RPC message-scoped transaction boundary。
 *
 * 仕様変更(2026-05-29 ktor-restructure):
 * - 例外 throw ベースから [RpcResult] 戻り値ベースに移行。
 * - 想定外の例外は [RpcError.Internal] に変換する(client にスタックトレースは漏らさない)。
 * - [CancellationException] は伝播させる(coroutine cancellation 仕様)。
 * - [supervisorScope] は kRPC server scope へのエラー leak 防止のため維持。
 *
 * Phase 4 で `session: MindstockSession` 引数が追加され、`session.exp` チェックが組み込まれる。
 */
suspend fun <T> tx(
    database: Database,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> =
    try {
        supervisorScope {
            newSuspendedTransaction(db = database) { block() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
