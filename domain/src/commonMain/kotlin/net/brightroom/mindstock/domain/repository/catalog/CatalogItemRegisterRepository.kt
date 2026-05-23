package net.brightroom.mindstock.domain.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.UserId

public interface CatalogItemRegisterRepository {
    /**
     * catalog_items + catalog_item_revisions(初回)を 1 トランザクションで INSERT。
     */
    public fun register(
        id: CatalogItemId,
        createdBy: UserId,
        name: CatalogItemName,
        unit: CatalogItemUnit,
    )

    /**
     * 新リビジョンを INSERT。
     * 名前のみ・単位のみの変更でも、両方の値を持ち回す責任は呼び出し側(UseCase)。
     */
    public fun revise(
        catalogItemId: CatalogItemId,
        name: CatalogItemName,
        unit: CatalogItemUnit,
        editedBy: UserId,
    )
}
