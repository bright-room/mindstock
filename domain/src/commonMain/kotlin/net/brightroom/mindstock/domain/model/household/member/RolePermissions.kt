package net.brightroom.mindstock.domain.model.household.member

import kotlinx.serialization.Serializable

@Serializable
enum class HouseholdMemberRole { 世帯主, メンバー, 閲覧者 }

enum class HouseholdCapability { 在庫編集, マスタ管理, 世帯管理 }

object RolePermissions {
    private val table: Map<HouseholdMemberRole, Set<HouseholdCapability>> =
        mapOf(
            HouseholdMemberRole.世帯主 to HouseholdCapability.entries.toSet(),
            HouseholdMemberRole.メンバー to setOf(HouseholdCapability.在庫編集),
            HouseholdMemberRole.閲覧者 to emptySet(),
        )

    fun allows(
        role: HouseholdMemberRole,
        capability: HouseholdCapability,
    ): Boolean = table.getValue(role).contains(capability)
}
