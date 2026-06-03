package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products

interface ProductRepository {
    fun findById(id: ProductId): Product

    /** 採用中の商品一覧(空なら空 Products)。 */
    fun listByHousehold(householdId: HouseholdId): Products

    /** アーカイブ済の商品一覧。 */
    fun listArchivedByHousehold(householdId: HouseholdId): Products

    /** 世帯内に当該 JAN を持つ Product が存在するか(採用中+アーカイブ済を対象)。重複登録防止に使う。 */
    fun existsByJan(
        householdId: HouseholdId,
        jan: Jan,
    ): Boolean

    /** 現在手動希望中(最新 wanted イベントが true)の Product 一覧。空なら空 Products。 */
    fun listWanted(householdId: HouseholdId): Products
}
