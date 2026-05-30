package net.brightroom.mindstock.configuration.rpc

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}
private val callLogJson = Json { encodeDefaults = true }

@Serializable
private data class RpcCallLogEntry(
    val callId: String,
    val userId: String?,
    val outcome: String,
    val elapsedMs: Long,
)

/**
 * RPC message-scoped 境界。**transaction は張らない**(各 DataSource が自分で張る)。
 *
 * - session.exp が現在時刻を超えていたら即 Err(Unauthorized("token expired"))
 * - block はドメイン値 T を返す。成功時は RpcResult.Ok(result) に包む
 * - ResourceNotFoundException → Err(NotFound) / その他 Throwable → Err(Internal)
 * - CancellationException は伝播
 * - supervisorScope は kRPC server scope へのエラー leak 防止のため維持
 * - 各呼び出しごとに callId / userId / outcome / elapsedMs を 1 行 JSON でログ出力
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun <T> rpcBoundary(
    session: MindstockSession,
    block: suspend () -> T,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    if (start > session.exp) {
        emitLog(session, start, outcome = "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result = supervisorScope { block() }
        emitLog(session, start, outcome = "Ok")
        RpcResult.Ok(result)
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
    start: Instant,
    outcome: String,
) {
    val elapsedMs = (Clock.System.now() - start).inWholeMilliseconds
    val entry =
        RpcCallLogEntry(
            callId = session.callId.toString(),
            userId = session.userId?.toString(),
            outcome = outcome,
            elapsedMs = elapsedMs,
        )
    logger.info { "rpc call ${callLogJson.encodeToString(entry)}" }
}
