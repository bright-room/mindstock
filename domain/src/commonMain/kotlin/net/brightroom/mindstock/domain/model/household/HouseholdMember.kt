package net.brightroom.mindstock.domain.model.household

import net.brightroom.mindstock.domain.model.user.User

/**
 * 世帯のメンバー(ユーザー + 役割)。
 *
 * Household 集約に含まれる Value Object。
 * 「revoked」状態は Repository が読み込み時にフィルタするため、
 * HouseholdMember を持っている = active なメンバー。
 */
data class HouseholdMember(
    val user: User,
    val role: HouseholdMemberRole,
)
