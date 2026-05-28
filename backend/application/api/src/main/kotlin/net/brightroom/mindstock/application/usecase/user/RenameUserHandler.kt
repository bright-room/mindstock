package net.brightroom.mindstock.application.usecase.user

import net.brightroom.mindstock.application.repository.user.UserRegisterRepository
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User

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
