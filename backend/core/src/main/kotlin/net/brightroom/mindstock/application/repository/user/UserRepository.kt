package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRepository {
    fun findProfileByAuthIdentity(identity: AuthIdentity): Profile?

    fun findProfileById(id: UserId): Profile?
}
