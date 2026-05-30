package net.brightroom.mindstock.infrastructure.datasource.stock

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import net.brightroom.mindstock.domain.model.stock.movement.Note
import net.brightroom.mindstock.domain.model.stock.movement.OccurredAt
import net.brightroom.mindstock.domain.model.stock.movement.Quantity
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.product.ProductDataSource
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Tags("integration")
class StockDataSourceIntegrationTest :
    FunSpec({

        test("stocksOf returns empty Stocks when household has no products") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val householdRepo = HouseholdRegisterDataSource(database)
                val user =
                    runBlocking {
                        userRepo.register(
                            AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")),
                            DisplayName("U"),
                        )
                    }
                val household = runBlocking { householdRepo.create(user.userId) }

                val stockRepo = StockDataSource(ProductDataSource(database), database)
                val result = runBlocking { stockRepo.stocksOf(household) }
                result.list.shouldBeEmpty()
            }
        }

        test("stocksOf returns the household's products with their current quantities") {
            withRepositoryTestContext {
                val (user, household, product) = setupUserHouseholdProduct()
                val register = StockRegisterDataSource(database)
                val reader = StockDataSource(ProductDataSource(database), database)

                runBlocking {
                    register.replenish(product, Quantity(7), OccurredAt(Clock.System.now()), user.userId, Note(""))
                }

                val stocks = runBlocking { reader.stocksOf(household) }

                stocks.list shouldHaveSize 1
                val stock = stocks.list.single()
                stock.product.id shouldBe product.id
                stock.currentQuantity() shouldBe 7
            }
        }

        test("stockOf returns empty StockMovements for a fresh product") {
            withRepositoryTestContext {
                val (_, _, product) = setupUserHouseholdProduct()
                val reader = StockDataSource(ProductDataSource(database), database)

                val stock = runBlocking { reader.stockOf(product) }
                stock.movements.list.size shouldBe 0
                stock.currentQuantity() shouldBe 0
            }
        }

        test("movementHistory respects limit and returns DESC by occurred_at") {
            withRepositoryTestContext {
                val (user, _, product) = setupUserHouseholdProduct()
                val register = StockRegisterDataSource(database)
                val reader = StockDataSource(ProductDataSource(database), database)

                val t0 = Clock.System.now().minus(10.seconds)
                runBlocking { register.replenish(product, Quantity(1), OccurredAt(t0), user.userId, Note("a")) }
                runBlocking { register.replenish(product, Quantity(2), OccurredAt(t0.plus(1.seconds)), user.userId, Note("b")) }
                runBlocking { register.replenish(product, Quantity(3), OccurredAt(t0.plus(2.seconds)), user.userId, Note("c")) }

                val history = runBlocking { reader.movementHistory(product, limit = 2) }
                val rows = history.list
                rows shouldHaveSize 2
                rows[0].quantity shouldBe Quantity(3) // most recent first
                rows[1].quantity shouldBe Quantity(2)
            }
        }
    })
