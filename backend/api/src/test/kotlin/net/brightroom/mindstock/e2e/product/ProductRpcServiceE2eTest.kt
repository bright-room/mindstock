package net.brightroom.mindstock.e2e.product

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedProduct
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pinned behaviors:
 * 1. adopt creates a new Product linking household and catalog item.
 * 2. listOfHousehold returns all adopted products.
 * 3. find(household, catalogItem) returns the matching product.
 * 4. setMinimumStock updates the threshold on an existing product.
 * 5. archive marks an existing product as archived.
 * 6. setMinimumStock against an unknown productId returns Err(NotFound).
 * 7. archive against an unknown productId returns Err(NotFound).
 * 8. A failing adopt does not leave partial DB writes (transaction rollback).
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class ProductRpcServiceE2eTest :
    FunSpec({

        test("adopt creates a new Product linking household and catalog item") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner, name = "Soap")
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                val r = rpc.adopt(hh.id, item.id)
                r.shouldBeInstanceOf<RpcResult.Ok<Product>>()
                val product = r.value

                product.catalogItem.id shouldBe item.id
                product.archived shouldBe false
            }
        }

        test("listOfHousehold returns all adopted products") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item1 = seedCatalogItem(createdBy = owner, name = "A")
                val item2 = seedCatalogItem(createdBy = owner, name = "B")
                seedProduct(hh, item1)
                seedProduct(hh, item2)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                val r = rpc.listOfHousehold(hh.id)
                r.shouldBeInstanceOf<RpcResult.Ok<Products>>()
                r.value.list shouldHaveSize 2
            }
        }

        test("find returns the product for (household, catalogItem)") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                val r = rpc.find(hh.id, item.id)
                r.shouldBeInstanceOf<RpcResult.Ok<Product?>>()
                val found = r.value
                found.shouldNotBeNull()
                found.id shouldBe product.id
            }
        }

        test("setMinimumStock updates the threshold on an existing product") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                rpc
                    .setMinimumStock(product.id, MinimumStock.Set(3))
                    .shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                val read = rpc.find(hh.id, item.id)
                read.shouldBeInstanceOf<RpcResult.Ok<Product?>>()
                val updated = read.value
                updated.shouldNotBeNull()
                updated.minimumStock shouldBe MinimumStock.Set(3)
            }
        }

        test("archive marks an existing product as archived") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val item = seedCatalogItem(createdBy = owner)
                val product = seedProduct(hh, item)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                rpc.archive(product.id).shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                // archive 後の find は実装依存:
                //   - null を返す (archived は find から除外)
                //   - or archived=true で返す
                // どちらでも整合と見なす。
                val read = rpc.find(hh.id, item.id)
                read.shouldBeInstanceOf<RpcResult.Ok<Product?>>()
                val after = read.value
                if (after != null) {
                    after.archived shouldBe true
                }
            }
        }

        test("setMinimumStock with unknown productId returns Err(NotFound)") {
            e2eTest {
                val owner = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()
                val r = rpc.setMinimumStock(ProductId(Uuid.random()), MinimumStock.Set(1))
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                r.error.shouldBeInstanceOf<RpcError.NotFound>()
            }
        }

        test("archive with unknown productId returns Err(NotFound)") {
            e2eTest {
                val owner = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()
                val r = rpc.archive(ProductId(Uuid.random()))
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                r.error.shouldBeInstanceOf<RpcError.NotFound>()
            }
        }

        test("a failing adopt does not leave partial DB writes (transaction rollback)") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                val initial = rpc.listOfHousehold(hh.id)
                initial.shouldBeInstanceOf<RpcResult.Ok<Products>>()
                val initialCount = initial.value.list.size

                val failed = rpc.adopt(hh.id, CatalogItemId(Uuid.random()))
                failed.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                failed.error.shouldBeInstanceOf<RpcError.NotFound>()

                val after = rpc.listOfHousehold(hh.id)
                after.shouldBeInstanceOf<RpcResult.Ok<Products>>()
                after.value.list.size shouldBe initialCount
            }
        }
    })
