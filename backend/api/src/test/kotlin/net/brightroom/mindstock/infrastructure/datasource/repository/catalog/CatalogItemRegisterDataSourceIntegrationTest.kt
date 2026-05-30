package net.brightroom.mindstock.infrastructure.datasource.catalog

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource

@Tags("integration")
class CatalogItemRegisterDataSourceIntegrationTest :
    FunSpec({

        test("register inserts catalog_items + first revision and returns CatalogItem") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val catalogRepo = CatalogItemRegisterDataSource(database)
                val creator =
                    runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("creator")), DisplayName("Creator")) }

                val item = runBlocking { catalogRepo.register(CatalogItemName("Milk"), CatalogItemUnit("L"), creator.userId) }

                item.name shouldBe CatalogItemName("Milk")
                item.unit shouldBe CatalogItemUnit("L")
            }
        }

        test("revise inserts new revision and findById returns the latest") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val catalogRegister = CatalogItemRegisterDataSource(database)
                val catalogReader = CatalogItemDataSource(database)
                val editor =
                    runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("editor")), DisplayName("Editor")) }

                val item = runBlocking { catalogRegister.register(CatalogItemName("Milk"), CatalogItemUnit("L"), editor.userId) }
                runBlocking { catalogRegister.revise(item, CatalogItemName("Whole Milk"), CatalogItemUnit("L"), editor.userId) }

                val refetched = runBlocking { catalogReader.findById(item.id) }
                refetched?.name shouldBe CatalogItemName("Whole Milk")
            }
        }
    })
