package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

/**
 * 世帯集約。アクティブなメンバー一覧を持つ。
 */
@Serializable
data class Household(
    val id: HouseholdId,
    val members: HouseholdMembers,
)
