@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.household

import net.brightroom.mindstock.infrastructure.datasource.HistoryTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdNamesTable : HistoryTable("household_names") {
    val household_id = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 100)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, household_id, id)
    }
}
