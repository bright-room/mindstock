@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}
private val callLogJson = Json { encodeDefaults = true }

@Serializable
private data class TxLogEntry(
    val callId: String,
    val userId: String?,
    val outcome: String, // "Ok" | "Err:<variant>" | "Throwable"
    val elapsedMs: Long,
)

/**
 * RPC message-scoped transaction boundary + session guard。
 *
 * - session.exp が現在時刻を超えていたら即 `Err(Unauthorized("token expired"))`(L2)
 * - block 内の想定外例外は `Err(Internal)` に変換し、logger.error する
 * - CancellationException は伝播
 * - supervisorScope は kRPC server scope へのエラー leak 防止のため維持
 * - 各呼び出しごとに callId / userId / outcome / elapsedMs を 1 行 JSON で logger.info 出力する
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun <T> tx(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    val now = start
    if (now > session.exp) {
        emitLog(session, start, outcome = "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result =
            supervisorScope {
                newSuspendedTransaction(db = database) { block() }
            }
        emitLog(
            session,
            start,
            outcome =
                when (result) {
                    is RpcResult.Ok -> "Ok"
                    is RpcResult.Err -> "Err:${result.error::class.simpleName}"
                },
        )
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResourceNotFoundException) {
        emitLog(session, start, outcome = "Err:NotFound")
        RpcResult.Err(RpcError.NotFound(message = e.message.orEmpty()))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        emitLog(session, start, outcome = "Throwable:${e::class.simpleName}")
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun emitLog(
    session: MindstockSession,
    start: kotlinx.datetime.Instant,
    outcome: String,
) {
    val elapsedMs = (Clock.System.now() - start).inWholeMilliseconds
    val entry =
        TxLogEntry(
            callId = session.callId.toString(),
            userId = session.userId?.toString(),
            outcome = outcome,
            elapsedMs = elapsedMs,
        )
    logger.info { "rpc call ${callLogJson.encodeToString(entry)}" }
}
