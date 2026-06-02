@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object HouseholdNamesTable : HistoryTable("household_names") {
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 30)
    val recordedAt = timestampWithTimeZone("recorded_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, householdId, id)
    }
}
