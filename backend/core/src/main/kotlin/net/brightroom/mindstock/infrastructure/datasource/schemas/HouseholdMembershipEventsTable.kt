@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object HouseholdMembershipEventsTable : Table("household_membership_events") {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val role = enumerationByName<HouseholdMemberRole>("role", 20)
    val status = enumerationByName<MembershipStatus>("status", 10)
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, householdId, residentId, id)
        index(false, residentId, householdId, id)
    }
}
