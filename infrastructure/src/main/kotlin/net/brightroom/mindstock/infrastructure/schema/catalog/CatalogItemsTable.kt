package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

@Migratable
object CatalogItemsTable : AggregateRootTable("catalog_items") {
    val created_by = reference("created_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = datetime("created_at").defaultExpression(CurrentDateTime)
}
