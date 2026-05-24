package net.brightroom.mindstock.infrastructure.datasource.repository.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class UserRegisterRepositoryImplIntegrationTest :
    FunSpec({

        test("register inserts users + display_names and returns User with initial display name") {
            withRepositoryTestContext {
                val repo = UserRegisterRepositoryImpl()
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))
                val name = DisplayName("Alice")

                val user = tx { repo.register(identity, name) }

                user.authIdentity shouldBe identity
                user.displayName shouldBe name
            }
        }

        test("rename inserts a new display_names row and the latest is returned by reader") {
            withRepositoryTestContext {
                val registerRepo = UserRegisterRepositoryImpl()
                val readerRepo = UserRepositoryImpl()
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

                val user = tx { registerRepo.register(identity, DisplayName("Alice")) }
                tx { registerRepo.rename(user, DisplayName("Alicia")) }

                val refetched = tx { readerRepo.findByAuthIdentity(identity) }
                refetched?.displayName shouldBe DisplayName("Alicia")
            }
        }
    })
