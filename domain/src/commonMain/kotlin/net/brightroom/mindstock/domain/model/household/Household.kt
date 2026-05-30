package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

/**
 * 世帯集約。世帯名とアクティブなメンバー一覧を持つ。
 */
@Serializable
data class Household(
    val id: HouseholdId,
    val name: HouseholdName,
    val members: HouseholdMembers,
)
