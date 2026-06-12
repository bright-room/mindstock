package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.application.repository.product.ProductImageStorageRepository
import net.brightroom.mindstock.application.repository.product.ProductRegisterRepository
import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.exception.DuplicateJanException
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.shopping.Wanted
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productRegisterRepository: ProductRegisterRepository,
    private val stockRepository: StockRepository,
    private val householdRepository: HouseholdRepository,
    private val imageStorage: ProductImageStorageRepository,
) {
    private fun authorize(
        householdId: HouseholdId,
        actor: ResidentId,
    ) = householdRepository.findById(householdId).requireMember(actor)

    private fun authorizeProduct(
        productId: ProductId,
        actor: ResidentId,
    ) = authorize(productRepository.householdOf(productId), actor)

    fun adopt(
        catalogItem: CatalogItem,
        householdId: HouseholdId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ): Product {
        authorize(householdId, actor)
        if (productRepository.existsByJan(householdId, catalogItem.jan)) {
            throw DuplicateJanException("already adopted: ${catalogItem.jan}")
        }
        val product = Product.adopt(catalogItem, unit, minimumStock)
        productRegisterRepository.registerAdopted(product, householdId, catalogItem.id)
        return product
    }

    fun addCustom(
        householdId: HouseholdId,
        name: ProductName,
        barcode: Barcode,
        unit: ProductUnit,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ): Product {
        authorize(householdId, actor)
        (barcode as? Barcode.Linked)?.let {
            if (productRepository.existsByJan(householdId, it.jan)) {
                throw DuplicateJanException("already adopted: ${it.jan}")
            }
        }
        val product = Product.custom(name, barcode, unit, minimumStock)
        productRegisterRepository.registerCustom(product, householdId)
        return product
    }

    fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeUnit(unit))
    }

    fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeMinimum(minimumStock))
    }

    /** 画像を未設定に戻す。ref はクライアントが作れないため、設定は uploadImage 経由のみ・削除はこの None 専用経路に絞る。 */
    fun removeImage(
        productId: ProductId,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeImage(ProductImage.None))
    }

    suspend fun uploadImage(
        productId: ProductId,
        upload: RawImageUpload,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val ref = imageStorage.store(upload)
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeImage(ProductImage.Stored(ref)))
    }

    /** 在庫 0 のときのみ可。ガードは Stock.archive() が担保する。 */
    fun archive(
        productId: ProductId,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val stock = stockRepository.findByProduct(productId)
        productRegisterRepository.appendRevision(stock.archive().product)
    }

    fun unarchive(
        productId: ProductId,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        val stock = stockRepository.findByProduct(productId)
        productRegisterRepository.appendRevision(stock.unarchive().product)
    }

    fun setWanted(
        productId: ProductId,
        wanted: Wanted,
        actor: ResidentId,
    ) {
        authorizeProduct(productId, actor)
        productRegisterRepository.setWanted(productId, wanted)
    }
}
