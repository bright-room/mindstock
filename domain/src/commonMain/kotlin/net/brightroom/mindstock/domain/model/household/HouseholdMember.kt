package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 世帯のメンバー(プロフィール + 役割)。
 *
 * Household 集約に含まれる Value Object。
 * 「revoked」状態は Repository が読み込み時にフィルタするため、
 * HouseholdMember を持っている = active なメンバー。
 */
@Serializable
data class HouseholdMember(
    val profile: Profile,
    val role: HouseholdMemberRole,
)
