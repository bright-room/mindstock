package net.brightroom.mindstock.domain.model.user.profile

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.UserId

/**
 * ユーザーの表示文脈エンティティ。`UserId` と紐付く。
 *
 * 認証文脈は別エンティティ([net.brightroom.mindstock.domain.model.user.auth.AuthIdentity])として
 * 分離されており、本クラスからは到達できない。
 */
@Serializable
data class Profile(
    val userId: UserId,
    val displayName: DisplayName,
)
