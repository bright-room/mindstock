package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.UserDisplayName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.ZitadelSub

public interface UserRepository {
    public fun findById(id: UserId): User?
    public fun findByZitadelSub(sub: ZitadelSub): User?

    /** ユーザーの最新表示名を取得する。display_name 履歴がない場合は null。 */
    public fun findDisplayNameOf(userId: UserId): UserDisplayName?
}
