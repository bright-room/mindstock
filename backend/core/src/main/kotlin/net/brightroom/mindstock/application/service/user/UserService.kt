package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.Profile

class UserService(
    private val userRepository: UserRepository,
) {
    suspend fun findById(userId: UserId): Profile = userRepository.findProfileById(userId)
}
