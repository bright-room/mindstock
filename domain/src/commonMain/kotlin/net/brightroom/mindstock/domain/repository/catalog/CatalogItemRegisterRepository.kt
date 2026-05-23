package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User

interface CatalogItemRegisterRepository {
    /** catalog_items + 初回 catalog_item_revisions を 1 トランザクションで INSERT。 */
    fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem

    /** catalog_item_revisions に行を INSERT。name と unit 両方を渡す責任は呼び出し側。 */
    fun revise(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    )
}
