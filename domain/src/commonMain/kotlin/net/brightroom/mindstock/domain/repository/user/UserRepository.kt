package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserDisplayName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.ZitadelSub

interface UserRepository {
    fun findById(id: UserId): User?

    fun findByZitadelSub(sub: ZitadelSub): User?

    /** ユーザーの最新表示名を取得する。display_name 履歴がない場合は null。 */
    fun findDisplayNameOf(userId: UserId): UserDisplayName?
}
