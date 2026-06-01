package net.brightroom.mindstock.rpc.product

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ProductRegisterRpcService {
    /** マスタから商品を採用する(UC10。単位・最低在庫を指定)。 */
    suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcResult<Product, RpcError>

    /** マスタに無い商品をその場で追加(UC13)。 */
    suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcResult<Product, RpcError>

    /** 単位を変更(UC22, owner のみ)。 */
    suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcResult<Unit, RpcError>

    /** 画像を変更(UC22, owner のみ)。`ProductImage.None` で未設定に戻せる。 */
    suspend fun changeImage(
        productId: ProductId,
        image: ProductImage,
    ): RpcResult<Unit, RpcError>

    /** 最低在庫を変更(UC22, owner のみ)。 */
    suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError>

    /** 商品をアーカイブ(UC23, owner のみ。在庫 0 のときのみ)。 */
    suspend fun archive(productId: ProductId): RpcResult<Unit, RpcError>

    /** 商品を復元(UC23, owner のみ)。 */
    suspend fun unarchive(productId: ProductId): RpcResult<Unit, RpcError>

    /** 手動の買い物希望フラグを設定/解除(UC19,20)。ProductId は global UUID で世帯一意のため householdId は不要。 */
    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcResult<Unit, RpcError>
}
