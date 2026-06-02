@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas

import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object CatalogItemsTable : AggregateRootTable("catalog_items") {
    val jan = varchar("jan", 13)
    val name = varchar("name", 60)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(jan)
    }
}
