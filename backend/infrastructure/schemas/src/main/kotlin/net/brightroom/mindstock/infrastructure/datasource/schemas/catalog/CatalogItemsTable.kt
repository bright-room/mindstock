@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package net.brightroom.mindstock.infrastructure.datasource.schemas.catalog

import net.brightroom.mindstock.infrastructure.datasource.schemas.AggregateRootTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.user.UsersTable
import net.brightroom.mindstock.infrastructure.migration.annotation.Migratable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object CatalogItemsTable : AggregateRootTable("catalog_items") {
    val created_by = reference("created_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
