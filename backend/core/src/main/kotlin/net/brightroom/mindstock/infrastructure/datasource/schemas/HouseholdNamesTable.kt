@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object HouseholdNamesTable : Table("household_names") {
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 30)
    val recordedAt = datetime("recorded_at").defaultExpression(CurrentDateTime)

    init {
        index(false, householdId, id)
    }
}
