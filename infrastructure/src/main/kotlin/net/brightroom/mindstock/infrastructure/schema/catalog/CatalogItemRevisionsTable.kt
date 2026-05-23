package net.brightroom.mindstock.infrastructure.schema.catalog

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

@Migratable
object CatalogItemRevisionsTable : HistoryTable("catalog_item_revisions") {
    val catalog_item_id =
        reference("catalog_item_id", CatalogItemsTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 200)
    val unit = varchar("unit", 10)
    val edited_by =
        reference("edited_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        index(false, catalog_item_id, id)
    }
}
