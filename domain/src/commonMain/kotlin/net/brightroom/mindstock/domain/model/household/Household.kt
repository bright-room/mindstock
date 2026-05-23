package net.brightroom.mindstock.domain.model.household

/**
 * 世帯集約。アクティブなメンバー一覧を持つ。
 */
data class Household(
    val id: HouseholdId,
    val members: HouseholdMembers,
)
