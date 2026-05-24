package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemRevisionsTable
import net.brightroom.mindstock.infrastructure.datasource.schemas.catalog.CatalogItemsTable
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
internal class CatalogItemRegisterRepositoryImpl : CatalogItemRegisterRepository {
    override fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem {
        val insertedId =
            CatalogItemsTable.insert {
                it[created_by] = createdBy.id().toJavaUuid()
            } get CatalogItemsTable.id

        CatalogItemRevisionsTable.insert {
            it[catalog_item_id] = insertedId
            it[this.name] = name()
            it[this.unit] = unit()
            it[edited_by] = createdBy.id().toJavaUuid()
        }

        return hydrateCatalogItem(
            id = insertedId.toKotlinUuid(),
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
            it[catalog_item_id] = catalogItem.id().toJavaUuid()
            it[name] = newName()
            it[unit] = newUnit()
            it[edited_by] = editedBy.id().toJavaUuid()
        }
    }
}
