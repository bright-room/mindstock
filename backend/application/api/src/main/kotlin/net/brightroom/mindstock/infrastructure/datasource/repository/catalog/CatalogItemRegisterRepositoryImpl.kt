package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemsTable
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal class CatalogItemRegisterRepositoryImpl : CatalogItemRegisterRepository {
    override fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem {
        val insertedId =
            CatalogItemsTable.insert {
                it[created_by] = createdBy.id()
            } get CatalogItemsTable.id

        CatalogItemRevisionsTable.insert {
            it[catalog_item_id] = insertedId
            it[this.name] = name()
            it[this.unit] = unit()
            it[edited_by] = createdBy.id()
        }

        return hydrateCatalogItem(
            id = insertedId,
            name = name(),
            unit = unit(),
        )
    }

    override fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    ) {
        CatalogItemRevisionsTable.insert {
            it[catalog_item_id] = catalogItem.id()
            it[name] = newName()
            it[unit] = newUnit()
            it[edited_by] = editedBy.id()
        }
    }
}
