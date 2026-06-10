package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.image.ImageUrl
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ProductService(
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val householdRepository: HouseholdRepository,
    private val imageStorage: ProductImageStorageRepository,
) {
    /**
     * 商品画像の presigned GET URL を返す。
     *
     * 認可は registered ガード(Controller 側)で担保し、ここで世帯メンバー越しの追加認可は課さない
     * (productId は世帯一意・表示は member 全員可・presigned URL は短命)。
     */
    suspend fun imageUrl(productId: ProductId): ImageUrl {
        val product = productRepository.findById(productId)
        return when (val image = product.image) {
            is ProductImage.Stored -> imageStorage.presignedUrl(image.ref)
            ProductImage.None -> throw ResourceNotFoundException("product has no image: $productId")
        }
    }

    /** 在庫一覧(数量+status を見せるため Stock 集合)。 */
    fun list(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Stocks {
        householdRepository.findById(householdId).requireMember(actor)
        return stockRepository.listByHousehold(householdId)
    }

    fun listArchived(
        householdId: HouseholdId,
        actor: ResidentId,
    ): Products {
        householdRepository.findById(householdId).requireMember(actor)
        return productRepository.listArchivedByHousehold(householdId)
    }

    /** 買い物リスト(自動=在庫不足 + 手動希望)。Stock 集合 × 手動希望 を read-model に合成する。 */
    fun shoppingList(
        householdId: HouseholdId,
        actor: ResidentId,
    ): ShoppingList {
        householdRepository.findById(householdId).requireMember(actor)
        val stocks = stockRepository.listByHousehold(householdId)
        val wantedIds =
            productRepository
                .listWanted(householdId)
                .list
                .map { it.id }
                .toSet()
        return ShoppingList(stocks.list.map { ShoppingEntry(it, it.product.id in wantedIds) })
    }
}
