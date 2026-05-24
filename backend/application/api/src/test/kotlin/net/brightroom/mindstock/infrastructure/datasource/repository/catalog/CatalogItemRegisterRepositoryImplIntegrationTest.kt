package net.brightroom.mindstock.infrastructure.datasource.repository.catalog

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class CatalogItemRegisterRepositoryImplIntegrationTest :
    FunSpec({

        test("register inserts catalog_items + first revision and returns CatalogItem") {
            withRepositoryTestContext {
                val userRepo = UserRegisterRepositoryImpl()
                val catalogRepo = CatalogItemRegisterRepositoryImpl()
                val creator = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("creator")), DisplayName("Creator")) }

                val item = tx { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), creator) }

                item.name shouldBe CatalogItemName("Milk")
                item.unit shouldBe CatalogItemUnit("L")
            }
        }

        test("revise inserts new revision and findById returns the latest") {
            withRepositoryTestContext {
                val userRepo = UserRegisterRepositoryImpl()
                val catalogRegister = CatalogItemRegisterRepositoryImpl()
                val catalogReader = CatalogItemRepositoryImpl()
                val editor = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("editor")), DisplayName("Editor")) }

                val item = tx { catalogRegister.register(CatalogItemName("Milk"), CatalogItemUnit("L"), editor) }
                tx { catalogRegister.revise(item, CatalogItemName("Whole Milk"), CatalogItemUnit("L"), editor) }

                val refetched = tx { catalogReader.findById(item.id) }
                refetched?.name shouldBe CatalogItemName("Whole Milk")
            }
        }
    })
