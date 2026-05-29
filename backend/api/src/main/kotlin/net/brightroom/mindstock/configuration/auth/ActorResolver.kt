package net.brightroom.mindstock.configuration.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.configuration.error.UnauthorizedException
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 認証済み呼び出し元の Profile を解決する。
 *
 * 1. Principal が無い → [UnauthorizedException]("missing principal")
 * 2. AuthIdentity に紐づく Profile が DB に存在しない → [UnauthorizedException]("unknown user")
 */
fun ApplicationCall.actor(userRepository: UserRepository): Profile {
    val principal = principal<MindstockPrincipal>() ?: throw UnauthorizedException("missing principal")
    return userRepository.findProfileByAuthIdentity(principal.authIdentity) ?: throw UnauthorizedException("unknown user")
}
