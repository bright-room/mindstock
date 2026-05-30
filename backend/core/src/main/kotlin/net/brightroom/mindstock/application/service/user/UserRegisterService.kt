package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

class UserRegisterService(
    private val userRegisterRepository: UserRegisterRepository,
) {
    suspend fun register(
        identity: AuthIdentity,
        defaultDisplayName: DisplayName,
    ): Profile = userRegisterRepository.register(identity, defaultDisplayName)

    suspend fun rename(
        userId: UserId,
        newName: DisplayName,
    ) {
        userRegisterRepository.rename(userId, newName)
    }
}
