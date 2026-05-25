package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.brightroom.mindstock.configuration.error.UnauthorizedException
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.user.UserRepository

/**
 * 認証済み呼び出し元の User 集約を解決する。
 * Principal が無い、あるいは UserId に紐づく User が存在しない場合は [UnauthorizedException] を投げる。
 */
fun ApplicationCall.actor(userRepository: UserRepository): User {
    val principal = principal<MindstockPrincipal>() ?: throw UnauthorizedException("missing principal")
    return userRepository.findById(principal.userId) ?: throw UnauthorizedException("unknown user")
}
