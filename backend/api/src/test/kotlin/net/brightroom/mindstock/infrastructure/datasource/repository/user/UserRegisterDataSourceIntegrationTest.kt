package net.brightroom.mindstock.infrastructure.datasource.user

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

@Tags("integration")
class UserRegisterDataSourceIntegrationTest :
    FunSpec({

        test("register inserts users + display_names and returns Profile with initial display name") {
            withRepositoryTestContext {
                val repo = UserRegisterDataSource(database)
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))
                val name = DisplayName("Alice")

                val profile = runBlocking { repo.register(identity, name) }

                profile.displayName shouldBe name
            }
        }

        test("rename inserts a new display_names row and the latest is returned by reader") {
            withRepositoryTestContext {
                val registerRepo = UserRegisterDataSource(database)
                val readerRepo = UserDataSource(database)
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

                val profile = runBlocking { registerRepo.register(identity, DisplayName("Alice")) }
                runBlocking { registerRepo.rename(profile.userId, DisplayName("Alicia")) }

                val refetched = runBlocking { readerRepo.findProfileByAuthIdentity(identity) }
                refetched.displayName shouldBe DisplayName("Alicia")
            }
        }
    })
