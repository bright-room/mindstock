package net.brightroom.mindstock.domain.repository.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.ZitadelSub

public interface UserRegisterRepository {
    /** users 行を新規 INSERT(id は呼び出し側が UUIDv7 を生成して引数化)。 */
    public fun register(id: UserId, zitadelSub: ZitadelSub)

    /** user_display_names 行を新規 INSERT。最新表示名のロールフォワード扱い。 */
    public fun rename(userId: UserId, displayName: DisplayName)
}
