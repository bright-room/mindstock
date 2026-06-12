package net.brightroom.mindstock.rpc.product

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted
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

    /** 画像をアップロード(UC22)。原画像を base64 で送り、サーバが検証/縮小/保存する。 */
    suspend fun uploadImage(
        productId: ProductId,
        request: UploadImageRequest,
    ): RpcResult<Unit, RpcError>

    /** 単位を変更(UC22)。 */
    suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcResult<Unit, RpcError>

    /** 画像を未設定に戻す(UC22)。設定は uploadImage 経由のみ(ref はクライアントが採番できない)。 */
    suspend fun removeImage(productId: ProductId): RpcResult<Unit, RpcError>

    /** 最低在庫を変更(UC22)。 */
    suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError>

    /** 商品をアーカイブ(UC23。在庫 0 のときのみ)。 */
    suspend fun archive(productId: ProductId): RpcResult<Unit, RpcError>

    /** 商品を復元(UC23)。 */
    suspend fun unarchive(productId: ProductId): RpcResult<Unit, RpcError>

    /** 手動の買い物希望フラグを設定/解除(UC19,20)。ProductId は global UUID で世帯一意のため householdId は不要。 */
    suspend fun setWanted(
        productId: ProductId,
        wanted: Wanted,
    ): RpcResult<Unit, RpcError>
}
