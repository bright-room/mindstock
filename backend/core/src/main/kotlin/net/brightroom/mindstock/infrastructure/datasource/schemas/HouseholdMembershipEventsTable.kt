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
    val role = enumerationByName("role", 20, HouseholdMemberRole::class)
    val status = varchar("status", 10) // 永続専用: 所属 / 除外(tombstone)
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, householdId, residentId, id)
        index(false, residentId, householdId, id)
    }

    const val STATUS_ACTIVE = "所属"
    const val STATUS_REMOVED = "除外"
}
