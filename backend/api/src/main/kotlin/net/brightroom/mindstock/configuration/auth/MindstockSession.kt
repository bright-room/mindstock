package net.brightroom.mindstock.configuration.auth

import io.ktor.util.AttributeKey
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * WS upgrade 時に [MindstockAuthPlugin] が組み立て、call.attributes に格納する。
 * 接続単位で immutable。
 *
 * - [identity]: JWT 検証成功時に組み立てた AuthIdentity
 * - [exp]: JWT の expiresAt(時間軸上の一点)。P5c の per-message guard が
 *   `kotlin.time.Clock.System.now()` と比較して失効判定する。
 *   kotlinx.datetime.Instant は非推奨のため後継の kotlin.time.Instant を使う。
 * - [callId]: 接続単位のトレース ID。構造化ログに紐付ける
 *
 * 「JWT 有効だが Resident 未登録」を nullable で表さず sealed 2 状態で表現する
 * (nullable 戻り値禁止原則。承認済)。
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
sealed interface MindstockSession {
    val identity: AuthIdentity
    val exp: Instant
    val callId: Uuid

    /** JWT 有効だが Resident 未登録。register route でのみ通過を許す。 */
    data class Unregistered(
        override val identity: AuthIdentity,
        override val exp: Instant,
        override val callId: Uuid,
    ) : MindstockSession

    /** 登録済み Resident。residentId を保持。 */
    data class Registered(
        override val identity: AuthIdentity,
        val residentId: ResidentId,
        override val exp: Instant,
        override val callId: Uuid,
    ) : MindstockSession
}

internal val MindstockSessionKey: AttributeKey<MindstockSession> =
    AttributeKey("net.brightroom.mindstock.MindstockSession")
