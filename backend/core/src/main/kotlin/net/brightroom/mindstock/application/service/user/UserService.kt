package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile

class UserService(
    private val userRepository: UserRepository,
) {
    fun findById(userId: UserId): Profile = userRepository.findProfileById(userId)

    fun findByIdentity(identity: AuthIdentity): Profile = userRepository.findProfileByAuthIdentity(identity)
}
