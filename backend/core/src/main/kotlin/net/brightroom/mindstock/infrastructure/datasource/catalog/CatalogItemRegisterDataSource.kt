package net.brightroom.mindstock.infrastructure.datasource.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.UserId
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class CatalogItemRegisterDataSource(
    private val database: Database,
) : CatalogItemRegisterRepository {
    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: UserId,
    ): CatalogItem =
        newSuspendedTransaction(db = database) {
            val insertedId =
                CatalogItemsTable.insert {
                    it[created_by] = createdBy()
                } get CatalogItemsTable.id

            CatalogItemRevisionsTable.insert {
                it[catalog_item_id] = insertedId
                it[this.name] = name()
                it[this.unit] = unit()
                it[edited_by] = createdBy()
            }

            hydrateCatalogItem(
                id = insertedId,
                name = name(),
                unit = unit(),
            )
        }

    override suspend fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: UserId,
    ) {
        newSuspendedTransaction(db = database) {
            CatalogItemRevisionsTable.insert {
                it[catalog_item_id] = catalogItem.id()
                it[name] = newName()
                it[unit] = newUnit()
                it[edited_by] = editedBy()
            }
        }
    }
}
