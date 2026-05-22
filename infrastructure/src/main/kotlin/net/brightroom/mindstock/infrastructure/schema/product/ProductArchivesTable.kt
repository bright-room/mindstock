package net.brightroom.mindstock.infrastructure.schema.product

import net.brightroom.mindstock.infrastructure.persistence.Migratable
import net.brightroom.mindstock.infrastructure.schema.HistoryTable
import net.brightroom.mindstock.infrastructure.schema.user.UsersTable
import org.jetbrains.exposed.v1.core.ReferenceOption

@Migratable
object ProductArchivesTable : HistoryTable("product_archives") {
    val product_id = reference("product_id", ProductsTable.id, onDelete = ReferenceOption.RESTRICT)
    val archived_by = reference("archived_by", UsersTable.id, onDelete = ReferenceOption.RESTRICT)

    init {
        index(false, product_id, id)
    }
}
