package net.brightroom.mindstock.domain.model.household

import net.brightroom.mindstock.domain.model.user.User

/**
 * 世帯のメンバー一覧。
 *
 * アクティブなメンバーのみを保持(Repository が revoked を除外して読み込む)。
 */
class HouseholdMembers(private val list: List<HouseholdMember>) {
    /** OWNER ロールのメンバーを返す。存在しなければ null。 */
    fun owner(): User? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.user

    /** すべてのアクティブメンバーの User オブジェクトを返す。 */
    fun activeMembers(): List<User> = list.map { it.user }

    /** 指定したユーザーがアクティブメンバーに含まれるか。 */
    fun contains(user: User): Boolean = list.any { it.user == user }

    fun asList(): List<HouseholdMember> = list.toList()
}
