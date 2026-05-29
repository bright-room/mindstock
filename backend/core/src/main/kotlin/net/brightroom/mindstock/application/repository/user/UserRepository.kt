package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRepository {
    /**
     * 認証 identity(zitadel sub 等)で profile を引く。
     * 該当 user が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findProfileByAuthIdentity(identity: AuthIdentity): Profile

    /**
     * id 引き。
     * 該当 user が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findProfileById(id: UserId): Profile
}
