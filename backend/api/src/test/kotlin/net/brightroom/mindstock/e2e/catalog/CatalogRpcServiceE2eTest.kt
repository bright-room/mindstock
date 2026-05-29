package net.brightroom.mindstock.e2e.catalog

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedCatalogItem
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pinned behaviors:
 * 1. register persists a new CatalogItem and returns it.
 * 2. findById returns a previously-seeded item.
 * 3. findById returns null for an unknown id.
 * 4. search matches items by query substring.
 * 5. revise mutates name/unit; read-back via findById reflects the change.
 * 6. revise against an unknown id returns Err(NotFound) end-to-end.
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class CatalogRpcServiceE2eTest :
    FunSpec({

        test("register creates a new CatalogItem and returns it") {
            e2eTest {
                val user = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = user, path = "catalog")
                        .withService<CatalogRpcService>()

                val r = rpc.register(CatalogItemName("Milk"), CatalogItemUnit("L"))
                r.shouldBeInstanceOf<RpcResult.Ok<CatalogItem>>()
                val item = r.value

                item.name shouldBe CatalogItemName("Milk")
                item.unit shouldBe CatalogItemUnit("L")
            }
        }

        test("findById returns a registered item") {
            e2eTest {
                val user = seedUser()
                val seeded = seedCatalogItem(createdBy = user, name = "Bread")
                val rpc =
                    authenticatedRpcClient(asUser = user, path = "catalog")
                        .withService<CatalogRpcService>()

                val r = rpc.findById(seeded.id)
                r.shouldBeInstanceOf<RpcResult.Ok<CatalogItem?>>()
                val found = r.value

                found.shouldNotBeNull()
                found.id shouldBe seeded.id
                found.name shouldBe CatalogItemName("Bread")
            }
        }

        test("findById returns null for unknown id") {
            e2eTest {
                val user = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = user, path = "catalog")
                        .withService<CatalogRpcService>()

                val r = rpc.findById(CatalogItemId(Uuid.random()))
                r.shouldBeInstanceOf<RpcResult.Ok<CatalogItem?>>()
                r.value.shouldBeNull()
            }
        }

        test("search returns items whose name matches the query") {
            e2eTest {
                val user = seedUser()
                seedCatalogItem(createdBy = user, name = "Apple Juice")
                seedCatalogItem(createdBy = user, name = "Apple Pie")
                seedCatalogItem(createdBy = user, name = "Banana")
                val rpc =
                    authenticatedRpcClient(asUser = user, path = "catalog")
                        .withService<CatalogRpcService>()

                val r = rpc.search("Apple", limit = 50)
                r.shouldBeInstanceOf<RpcResult.Ok<CatalogItems>>()
                val results = r.value

                results.list shouldHaveAtLeastSize 2
                val names = results.list.map { it.name }
                names shouldContain CatalogItemName("Apple Juice")
                names shouldContain CatalogItemName("Apple Pie")
                names shouldNotContain CatalogItemName("Banana")
            }
        }

        test("revise updates name and unit of an existing item") {
            e2eTest {
                val user = seedUser()
                val item = seedCatalogItem(createdBy = user, name = "Old", unit = "個")
                val rpc =
                    authenticatedRpcClient(asUser = user, path = "catalog")
                        .withService<CatalogRpcService>()

                val r = rpc.revise(item.id, CatalogItemName("New"), CatalogItemUnit("kg"))
                r.shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                val read = rpc.findById(item.id)
                read.shouldBeInstanceOf<RpcResult.Ok<CatalogItem?>>()
                val updated = read.value
                updated.shouldNotBeNull()
                updated.name shouldBe CatalogItemName("New")
                updated.unit shouldBe CatalogItemUnit("kg")
            }
        }

        test("revise against an unknown id returns Err(NotFound)") {
            e2eTest {
                val user = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = user, path = "catalog")
                        .withService<CatalogRpcService>()

                val r =
                    rpc.revise(
                        id = CatalogItemId(Uuid.random()),
                        newName = CatalogItemName("Anything"),
                        newUnit = CatalogItemUnit("個"),
                    )
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                r.error.shouldBeInstanceOf<RpcError.NotFound>()
            }
        }
    })
