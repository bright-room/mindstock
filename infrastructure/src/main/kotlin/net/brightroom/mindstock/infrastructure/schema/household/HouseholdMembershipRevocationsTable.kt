package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object HouseholdMembershipRevocationsTable : HistoryTable("household_membership_revocations") {
    val membership_id = reference("membership_id", HouseholdMembershipsTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
}
