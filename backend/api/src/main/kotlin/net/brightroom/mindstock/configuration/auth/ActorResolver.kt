package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.User

/**
 * 認証済み呼び出し元の User 集約を解決する。
 *
 * Phase 4 で Auth 再設計に合わせて削除予定。それまでの暫定として、
 * 想定外ケース (principal 不在 / unknown user) は `error(...)` で落とし、
 * `tx()` が `RpcError.Internal` に変換する。
 */
fun ApplicationCall.actor(userRepository: UserRepository): User {
    val principal = principal<MindstockPrincipal>() ?: error("missing principal")
    return userRepository.findByAuthIdentity(principal.authIdentity) ?: error("unknown user")
}
