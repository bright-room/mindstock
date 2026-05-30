package net.brightroom.mindstock.configuration.auth

import io.ktor.util.AttributeKey
import kotlinx.datetime.Instant
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * WS upgrade 時に [MindstockAuthPlugin] が組み立て、call.attributes に格納する。
 * 接続単位で immutable。
 *
 * - [identity]: JWT 検証成功時に組み立てた AuthIdentity
 * - [userId]: 登録済み User の id。未登録の場合は null(register エンドポイントでのみ許容)
 * - [exp]: JWT の expiresAt。各 RPC メソッドで `rpcBoundary` 経由の guard が比較する
 * - [callId]: 接続単位のトレース ID。構造化ログに紐付ける
 */
@OptIn(ExperimentalUuidApi::class)
data class MindstockSession(
    val identity: AuthIdentity,
    val userId: UserId?,
    val exp: Instant,
    val callId: Uuid,
)

internal val MindstockSessionKey: AttributeKey<MindstockSession> =
    AttributeKey("net.brightroom.mindstock.MindstockSession")
