package net.brightroom.mindstock.infrastructure.datasource.repository.product

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.catalog.CatalogItemRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.household.HouseholdRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class ProductRegisterRepositoryImplIntegrationTest :
    FunSpec({

        test("adopt creates a Product with no MinimumStock and not archived") {
            withRepositoryTestContext {
                val userRepo = UserRegisterRepositoryImpl(database)
                val householdRepo = HouseholdRegisterRepositoryImpl(database)
                val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
                val productRepo = ProductRegisterRepositoryImpl(database)

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
                val userRepo = UserRegisterRepositoryImpl(database)
                val householdRepo = HouseholdRegisterRepositoryImpl(database)
                val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
                val productRegister = ProductRegisterRepositoryImpl(database)
                val productReader = ProductRepositoryImpl(database)

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
                val userRepo = UserRegisterRepositoryImpl(database)
                val householdRepo = HouseholdRegisterRepositoryImpl(database)
                val catalogRepo = CatalogItemRegisterRepositoryImpl(database)
                val productRegister = ProductRegisterRepositoryImpl(database)
                val productReader = ProductRepositoryImpl(database)

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
