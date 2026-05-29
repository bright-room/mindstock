@file:Suppress("DEPRECATION")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

private val logger = KotlinLogging.logger {}

/**
 * RPC message-scoped transaction boundary + session guard。
 *
 * - session.exp が現在時刻を超えていたら即 `Err(Unauthorized("token expired"))`(L2)
 * - block 内の想定外例外は `Err(Internal)` に変換し、logger.error する
 * - CancellationException は伝播
 * - supervisorScope は kRPC server scope へのエラー leak 防止のため維持
 */
suspend fun <T> tx(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    val now = Clock.System.now()
    if (now > session.exp) {
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        supervisorScope { newSuspendedTransaction(db = database) { block() } }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId} user_id=${session.userId}" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
