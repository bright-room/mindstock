package net.brightroom.mindstock.configuration.auth

import net.brightroom.mindstock.domain.model.user.UserId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 開発・テスト専用の認証スタブ。
 * Bearer token をそのまま UserId (UUID 文字列) として解釈する。
 * 本格的な認証 (JWT 検証等) は別 Plan で置き換える。
 */
object StubAuthProvider {
    @OptIn(ExperimentalUuidApi::class)
    fun resolve(token: String): MindstockPrincipal? =
        runCatching { MindstockPrincipal(UserId(Uuid.parse(token))) }.getOrNull()
}
