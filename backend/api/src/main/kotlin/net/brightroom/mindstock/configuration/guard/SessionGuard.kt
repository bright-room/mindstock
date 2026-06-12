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
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * RPC message 単位の認可ガード + 失効ガード + 例外→RpcError 翻訳。
 *
 * 認可は 2 つのヘルパーで表す(登録ガードは route ではなくここ=アプリ境界で行う):
 * - [requireRegistered] 既定。登録済み必須。Unregistered は Unauthorized で短絡(fail-closed)。
 * - [allowUnregistered] 認証のみ(未登録 OK)。register / whoami だけが使う。
 *
 * 共通処理([runGuarded]):接続時に保存した session.exp を現在時刻と比較し失効なら短絡(WS は
 * 張りっぱなしのため upgrade 時の 1 回検証では取りこぼす)。supervisorScope で例外 leak を防ぎ、
 * ドメイン例外を RpcError に翻訳する(DB transaction は張らない。境界は DataSource 自前)。
 * IdP 側の即時失効(revocation)は対象外。守るのは JWT の有効期限切れのみ。
 */
suspend fun <T : Any> allowUnregistered(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> = runGuarded(session, block)

suspend fun <T : Any> requireRegistered(
    session: MindstockSession,
    block: suspend (ResidentId) -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> =
    runGuarded(session) {
        when (session) {
            is MindstockSession.Registered -> block(session.residentId)
            is MindstockSession.Unregistered -> RpcResult.Err(RpcError.Unauthorized(reason = "registration required"))
        }
    }

private suspend fun <T : Any> runGuarded(
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
        val residentId = (session as? MindstockSession.Registered)?.residentId
        logger.error(e) {
            "unhandled exception during RPC call_id=${session.callId} " +
                "auth_subject=${session.identity.subject} resident_id=$residentId"
        }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
