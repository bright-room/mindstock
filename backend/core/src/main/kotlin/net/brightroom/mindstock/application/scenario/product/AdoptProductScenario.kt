package net.brightroom.mindstock.application.scenario.product

import net.brightroom.mindstock.application.service.catalog.CatalogService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit

class AdoptProductScenario(
    private val catalogService: CatalogService,
    private val productRegisterService: ProductRegisterService,
) {
    fun run(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): Product {
        val item = catalogService.findById(catalogItemId)
        return productRegisterService.adopt(item, householdId, unit, minimumStock)
    }
}
