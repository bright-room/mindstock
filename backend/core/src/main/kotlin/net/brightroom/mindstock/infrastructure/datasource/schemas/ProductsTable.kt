@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object ProductsTable : AggregateRootTable("products") {
    val householdId = reference("household_id", HouseholdsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 60)
    val jan = varchar("jan", 13).nullable() // null = Barcode.Unlinked / 値 = Barcode.Linked
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(false, householdId)
    }
}
