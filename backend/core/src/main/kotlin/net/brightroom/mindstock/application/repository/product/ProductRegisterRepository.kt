package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId

interface ProductRegisterRepository {
    /** マスタ採用: products + 初回 revision + product_catalog_links を 1 tx で INSERT。 */
    fun registerAdopted(
        product: Product,
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    )

    /** 世帯独自: products + 初回 revision を INSERT(リンク無し)。 */
    fun registerCustom(
        product: Product,
        householdId: HouseholdId,
    )

    /** 変更後の Product 全状態を product_revisions に 1 行 append(changeUnit/changeMinimum/uploadImage/removeImage/archive/unarchive)。 */
    fun appendRevision(product: Product)

    /** 手動希望フラグを product_wanted_events に append。 */
    fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    )
}
