package net.brightroom.mindstock.infrastructure.datasource.stock

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.repository.RepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
import kotlin.time.Clock

internal fun RepositoryTestContext.setupUserHouseholdProduct(): Triple<User, Household, Product> =
    tx {
        val user =
            UserRegisterDataSource().register(
                AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")),
                DisplayName("U"),
            )
        val household = HouseholdRegisterDataSource().create(user)
        val item = CatalogItemRegisterDataSource().register(CatalogItemName("Milk"), CatalogItemUnit("L"), user)
        val product = ProductRegisterDataSource().adopt(household, item)
        Triple(user, household, product)
    }

@Tags("integration")
class StockRegisterDataSourceIntegrationTest :
    FunSpec({

        test("replenish inserts REPLENISHMENT movement; stockOf returns +quantity") {
            withRepositoryTestContext {
                val (user, _, product) = setupUserHouseholdProduct()
                val stockRegister = StockRegisterDataSource()
                val stockReader = StockDataSource(ProductDataSource())

                tx {
                    stockRegister.replenish(product, Quantity(3), OccurredAt(Clock.System.now()), user, Note(""))
                }
                val stock = tx { stockReader.stockOf(product) }
                stock.currentQuantity() shouldBe 3
            }
        }

        test("consume inserts CONSUMPTION movement; stockOf returns net (replenish - consume)") {
            withRepositoryTestContext {
                val (user, _, product) = setupUserHouseholdProduct()
                val stockRegister = StockRegisterDataSource()
                val stockReader = StockDataSource(ProductDataSource())

                tx { stockRegister.replenish(product, Quantity(5), OccurredAt(Clock.System.now()), user, Note("")) }
                tx { stockRegister.consume(product, Quantity(2), OccurredAt(Clock.System.now()), user, Note("")) }

                val stock = tx { stockReader.stockOf(product) }
                stock.currentQuantity() shouldBe 3
            }
        }
    })
