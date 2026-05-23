package net.brightroom.mindstock.infrastructure.schemas.household

import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import net.brightroom.mindstock.infrastructure.schemas.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object HouseholdMembershipRevocationsTable : HistoryTable("household_membership_revocations") {
    val membership_id = reference("membership_id", HouseholdMembershipsTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
