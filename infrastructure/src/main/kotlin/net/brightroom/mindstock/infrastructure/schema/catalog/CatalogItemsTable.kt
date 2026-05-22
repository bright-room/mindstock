package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.AggregateRootTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object CatalogItemsTable : AggregateRootTable("catalog_items") {
    val created_by = reference("created_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
}
