@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdMembershipEventsTable : HistoryTable("household_membership_events") {
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val residentId = reference("resident_id", ResidentsTable.id, onDelete = ReferenceOption.RESTRICT)
    val role = enumerationByName("role", 20, HouseholdMemberRole::class)
    val status = varchar("status", 10) // 永続専用: 所属 / 除外(tombstone)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, householdId, residentId, id)
    }

    const val STATUS_ACTIVE = "所属"
    const val STATUS_REMOVED = "除外"
}
