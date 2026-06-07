package net.brightroom.mindstock.frontend.feature.catalog.data

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService

/**
 * 商品の採用・カスタム追加・マスタ設定・アーカイブまわりの RPC を隠蔽。
 * サービスは「開く関数」を遅延注入(認証後にトークン付きで open される)。
 */
class CatalogRepository(
    private val catalogService: () -> CatalogRpcService,
    private val productRegisterService: () -> ProductRegisterRpcService,
    private val productService: () -> ProductRpcService,
) {
    suspend fun search(
        name: CatalogItemName,
        limit: Int,
    ): RpcOutcome<CatalogItems> = catalogService().search(name, limit).toOutcome()

    suspend fun lookupByJan(jan: Jan): RpcOutcome<CatalogItem> = catalogService().lookupByJan(jan).toOutcome()

    suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcOutcome<Product> = productRegisterService().adopt(householdId, catalogItemId, unit, minimumStock).toOutcome()

    suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcOutcome<Product> = productRegisterService().addCustom(householdId, request).toOutcome()

    suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcOutcome<Unit> = productRegisterService().changeUnit(productId, unit).toOutcome()

    suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcOutcome<Unit> = productRegisterService().changeMinimum(productId, minimumStock).toOutcome()

    suspend fun archive(productId: ProductId): RpcOutcome<Unit> = productRegisterService().archive(productId).toOutcome()

    suspend fun unarchive(productId: ProductId): RpcOutcome<Unit> = productRegisterService().unarchive(productId).toOutcome()

    suspend fun listArchived(householdId: HouseholdId): RpcOutcome<Products> = productService().listArchived(householdId).toOutcome()
}
