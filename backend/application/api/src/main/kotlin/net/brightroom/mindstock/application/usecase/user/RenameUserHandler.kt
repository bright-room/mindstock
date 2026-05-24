package net.brightroom.mindstock.application.usecase.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository

class RenameUserHandler(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun handle(
        user: User,
        newName: DisplayName,
    ) {
        userRegisterRepository.rename(user, newName)
    }
}
