package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object CatalogItemUnitsTable : HistoryTable("catalog_item_units") {
    val catalog_item_id = reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val unit = text("unit")
    val edited_by = reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, catalog_item_id, id)
    }
}
