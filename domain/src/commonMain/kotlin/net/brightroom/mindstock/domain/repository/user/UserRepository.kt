package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity

interface UserRepository {
    fun findByAuthIdentity(identity: AuthIdentity): User?
}
