@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package net.brightroom.mindstock.configuration.guard

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.CannotArchiveWithStockException
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.exception.InsufficientStockException
import net.brightroom.mindstock.domain.exception.InvitationInvalidException
import net.brightroom.mindstock.domain.exception.LastOwnerException
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * RPC message 単位の失効ガード + 例外→RpcError 翻訳。
 *
 * - 接続時に保存した session.exp を現在時刻と比較し、期限切れなら Unauthorized で短絡(L2 失効ガード)。
 *   WS は長時間張りっぱなしのため、upgrade 時の 1 回検証だけでは期限切れを取りこぼす。
 * - supervisorScope で block を実行し、kRPC サーバスコープへの例外 leak を防ぐ。
 * - block 内のドメイン例外を RpcError に翻訳する(DB transaction は張らない。境界は DataSource 自前)。
 *
 * IdP 側の即時失効(revocation list)は対象外。守るのは JWT の有効期限切れのみ。
 */
suspend fun <T : Any> guarded(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    if (Clock.System.now() > session.exp) {
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        supervisorScope { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        RpcResult.Err(RpcError.BadRequest(field = "request", reason = e.message ?: "invalid request"))
    } catch (e: ResourceNotFoundException) {
        RpcResult.Err(RpcError.NotFound(message = e.message ?: "not found"))
    } catch (e: OwnerRequiredException) {
        RpcResult.Err(RpcError.Unauthorized(reason = e.message ?: "owner required"))
    } catch (e: MembershipRequiredException) {
        RpcResult.Err(RpcError.Unauthorized(reason = e.message ?: "membership required"))
    } catch (e: LastOwnerException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "last owner"))
    } catch (e: DuplicateJanException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "duplicate"))
    } catch (e: CannotArchiveWithStockException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "cannot archive with stock"))
    } catch (e: InsufficientStockException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "insufficient stock"))
    } catch (e: InvitationInvalidException) {
        RpcResult.Err(RpcError.Conflict(reason = e.message ?: "invitation invalid"))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
