package net.brightroom.mindstock.rpc.product

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ProductRpcService {
    /** 在庫一覧(UC16)。数量+status を見せるので Stock 集合を返す。 */
    suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError>

    /** アーカイブ済み商品一覧(UC23)。 */
    suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError>

    /** 買い物リスト(UC18。自動=在庫不足 + 手動希望の 2 区分を含む読みモデル)。 */
    suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError>

    /** 商品画像の presigned GET URL を取得。画像未設定は NotFound。 */
    suspend fun imageUrl(productId: ProductId): RpcResult<ImageUrl, RpcError>
}
