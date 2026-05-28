package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

interface UserRepository {
    fun findByAuthIdentity(identity: AuthIdentity): User?

    fun findById(id: UserId): User?
}
