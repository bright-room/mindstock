@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ProductsTable : Table("products") {
    val id = uuid("id")
    override val primaryKey = PrimaryKey(id)

    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 60)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(false, householdId)
    }
}
