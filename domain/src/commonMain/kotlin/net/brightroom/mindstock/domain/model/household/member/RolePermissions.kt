package net.brightroom.mindstock.domain.model.household.member

class RolePermissions(
    private val role: HouseholdMemberRole,
    private val capability: HouseholdCapability,
) {
    fun isAllowed(): Boolean = TABLE.getValue(role).contains(capability)

    companion object {
        private val TABLE: Map<HouseholdMemberRole, Set<HouseholdCapability>> =
            mapOf(
                HouseholdMemberRole.世帯主 to HouseholdCapability.entries.toSet(),
                HouseholdMemberRole.メンバー to setOf(HouseholdCapability.在庫編集),
                HouseholdMemberRole.閲覧者 to emptySet(),
            )
    }
}
