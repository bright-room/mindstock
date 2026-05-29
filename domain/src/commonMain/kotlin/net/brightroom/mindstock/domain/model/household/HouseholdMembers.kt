package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 世帯のメンバー一覧。
 *
 * アクティブなメンバーのみを保持(Repository が revoked を除外して読み込む)。
 */
@Serializable
data class HouseholdMembers(
    val list: List<HouseholdMember>,
) {
    /** OWNER ロールのメンバーを返す。存在しなければ null。 */
    fun owner(): Profile? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.profile

    /** すべてのアクティブメンバーの Profile を返す。 */
    fun activeMembers(): List<Profile> = list.map { it.profile }

    /** 指定したユーザーがアクティブメンバーに含まれるか。 */
    fun contains(userId: UserId): Boolean = list.any { it.profile.userId == userId }
}
