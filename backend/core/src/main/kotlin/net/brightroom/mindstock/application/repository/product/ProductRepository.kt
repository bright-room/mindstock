package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products

interface ProductRepository {
    /** 世帯の全商品(archived 含む)。 */
    suspend fun listOf(household: Household): Products

    /**
     * 複合キー(世帯 × カタログ商品)による Product 取得。`findById` の複合キー版。
     *
     * - UNIQUE 制約は DB 側 (products テーブルの (household_id, catalog_item_id))
     *   で担保されており、本メソッドを採用前のチェックには使わない
     * - クライアント側で「この世帯における当該カタログ商品の Product 状態
     *   (minimumStock / archived 等) を読みたい」用途で使う
     * - 該当 Product が存在しなければ `ResourceNotFoundException` を throw する
     */
    suspend fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product

    /**
     * id 引き(主に RPC 経由)。
     * 該当 product が存在しなければ `ResourceNotFoundException` を throw する。
     */
    suspend fun findById(id: ProductId): Product
}
