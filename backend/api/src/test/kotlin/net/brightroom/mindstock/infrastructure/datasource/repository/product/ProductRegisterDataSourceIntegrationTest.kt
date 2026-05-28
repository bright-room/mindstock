package net.brightroom.mindstock.infrastructure.datasource.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.catalog.CatalogItemRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource

class ProductRegisterDataSourceIntegrationTest :
    FunSpec({

        test("adopt creates a Product with no MinimumStock and not archived") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val catalogRepo = CatalogItemRegisterDataSource()
                val productRepo = ProductRegisterDataSource()

                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
                val household = tx { householdRepo.create(user) }
                val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }

                val product = tx { productRepo.adopt(household, item) }

                product.minimumStock shouldBe null
                product.archived shouldBe false
                product.catalogItem shouldBe item
            }
        }

        test("setMinimumStock + find reflects the latest minimum stock value") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val catalogRepo = CatalogItemRegisterDataSource()
                val productRegister = ProductRegisterDataSource()
                val productReader = ProductDataSource()

                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
                val household = tx { householdRepo.create(user) }
                val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }
                val product = tx { productRegister.adopt(household, item) }

                tx { productRegister.setMinimumStock(product, MinimumStock(2), user) }
                tx { productRegister.setMinimumStock(product, MinimumStock(5), user) }

                val refetched = tx { productReader.find(household, item) }
                refetched?.minimumStock shouldBe MinimumStock(5)
            }
        }

        test("archive sets archived = true on Product") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val catalogRepo = CatalogItemRegisterDataSource()
                val productRegister = ProductRegisterDataSource()
                val productReader = ProductDataSource()

                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("u")), DisplayName("U")) }
                val household = tx { householdRepo.create(user) }
                val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), user) }
                val product = tx { productRegister.adopt(household, item) }
                tx { productRegister.archive(product, user) }

                val refetched = tx { productReader.find(household, item) }
                refetched?.archived shouldBe true
            }
        }
    })
