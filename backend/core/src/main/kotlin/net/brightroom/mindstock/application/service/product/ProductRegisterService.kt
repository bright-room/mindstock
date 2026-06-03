package net.brightroom.mindstock.application.service.product

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
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

class ProductRegisterService(
    private val productRepository: ProductRepository,
    private val productRegisterRepository: ProductRegisterRepository,
    private val stockRepository: StockRepository,
) {
    fun adopt(
        catalogItem: CatalogItem,
        householdId: HouseholdId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): Product {
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
    ): Product {
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
    ) {
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeUnit(unit))
    }

    fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ) {
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeMinimum(minimumStock))
    }

    fun changeImage(
        productId: ProductId,
        image: ProductImage,
    ) {
        val product = productRepository.findById(productId)
        productRegisterRepository.appendRevision(product.changeImage(image))
    }

    /** 在庫 0 のときのみ可。ガードは Stock.archive() が担保する。 */
    fun archive(productId: ProductId) {
        val stock = stockRepository.findByProduct(productId)
        productRegisterRepository.appendRevision(stock.archive().product)
    }

    fun unarchive(productId: ProductId) {
        val stock = stockRepository.findByProduct(productId)
        productRegisterRepository.appendRevision(stock.unarchive().product)
    }

    fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ) = productRegisterRepository.setWanted(productId, wanted)
}
