@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object InvitationsTable : Table("invitations") {
    val code = varchar("code", 6)
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val grantedRole = enumerationByName<HouseholdMemberRole>("granted_role", 20)
    override val primaryKey = PrimaryKey(code)

    init {
        index(false, householdId)
    }
}
