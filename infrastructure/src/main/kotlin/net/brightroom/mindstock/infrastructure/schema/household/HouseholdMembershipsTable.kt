package net.brightroom.mindstock.infrastructure.schema.household

import net.brightroom.mindstock.domain.household.HouseholdMemberRole
import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object HouseholdMembershipsTable : HistoryTable("household_memberships") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val user_id = reference("user_id", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val role = enumerationByName<HouseholdMemberRole>("role", 20)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, household_id, id)
        index(false, user_id, id)
    }
}
