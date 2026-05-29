package net.brightroom.mindstock.infrastructure.datasource.user

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

@Tags("integration")
class UserDataSourceIntegrationTest :
    FunSpec({

        test("findProfileByAuthIdentity returns null when no user with that subject exists") {
            withRepositoryTestContext {
                val repo = UserDataSource()
                val result = tx { repo.findProfileByAuthIdentity(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("unknown"))) }
                result.shouldBeNull()
            }
        }

        test("findProfileByAuthIdentity returns profile with initial display name") {
            withRepositoryTestContext {
                val registerRepo = UserRegisterDataSource()
                val readerRepo = UserDataSource()
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

                tx { registerRepo.register(identity, DisplayName("Alice")) }
                val refetched = tx { readerRepo.findProfileByAuthIdentity(identity) }

                refetched?.displayName shouldBe DisplayName("Alice")
            }
        }

        test("findProfileByAuthIdentity returns LATEST display name after rename") {
            withRepositoryTestContext {
                val registerRepo = UserRegisterDataSource()
                val readerRepo = UserDataSource()
                val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("subject-1"))

                val profile = tx { registerRepo.register(identity, DisplayName("Alice")) }
                tx { registerRepo.rename(profile.userId, DisplayName("Alicia")) }

                val refetched = tx { readerRepo.findProfileByAuthIdentity(identity) }
                refetched?.displayName shouldBe DisplayName("Alicia")
            }
        }
    })
