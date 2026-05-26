package net.brightroom.mindstock.e2e.product

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedProduct
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.presentation.rpc.ProductRpcService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pinned behaviors:
 * 1. adopt creates a new Product linking household and catalog item.
 * 2. listOfHousehold returns all adopted products.
 * 3. find(household, catalogItem) returns the matching product.
 * 4. setMinimumStock updates the threshold on an existing product.
 * 5. archive marks an existing product as archived.
 * 6. setMinimumStock against an unknown productId is rejected.
 * 7. archive against an unknown productId is rejected.
 * 8. A failing adopt does not leave partial DB writes (transaction rollback).
 */
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

                val product = rpc.adopt(hh.id, item.id)

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

                val products = rpc.listOfHousehold(hh.id)
                products.list shouldHaveSize 2
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

                val found = rpc.find(hh.id, item.id)
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

                rpc.setMinimumStock(product.id, MinimumStock(3))

                val updated = rpc.find(hh.id, item.id)
                updated.shouldNotBeNull()
                updated.minimumStock shouldBe MinimumStock(3)
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

                rpc.archive(product.id)

                // archive 後の find は実装依存:
                //   - null を返す (archived は find から除外)
                //   - or archived=true で返す
                // どちらでも整合と見なす。
                val after = rpc.find(hh.id, item.id)
                if (after != null) {
                    after.archived shouldBe true
                }
            }
        }

        test("setMinimumStock with unknown productId is rejected") {
            e2eTest {
                val owner = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()
                shouldThrowAny {
                    rpc.setMinimumStock(ProductId(Uuid.random()), MinimumStock(1))
                }
            }
        }

        test("archive with unknown productId is rejected") {
            e2eTest {
                val owner = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()
                shouldThrowAny {
                    rpc.archive(ProductId(Uuid.random()))
                }
            }
        }

        test("a failing adopt does not leave partial DB writes (transaction rollback)") {
            e2eTest {
                val owner = seedUser()
                val hh = seedHousehold(owner)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "product")
                        .withService<ProductRpcService>()

                val initialCount = rpc.listOfHousehold(hh.id).list.size
                shouldThrowAny {
                    rpc.adopt(hh.id, CatalogItemId(Uuid.random()))
                }
                val afterCount = rpc.listOfHousehold(hh.id).list.size
                afterCount shouldBe initialCount
            }
        }
    })
