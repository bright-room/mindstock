package net.brightroom.mindstock.infrastructure.datasource.repository.household

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.user.UserRegisterRepositoryImpl
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext

class HouseholdRepositoryImplIntegrationTest :
    FunSpec({

        test("findOf returns null when user has no household membership") {
            withRepositoryTestContext {
                val userRepo = UserRegisterRepositoryImpl()
                val householdReader = HouseholdRepositoryImpl()
                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("lonely")), DisplayName("Lonely")) }

                val result = tx { householdReader.findOf(user) }

                result.shouldBeNull()
            }
        }

        test("findOf returns the household with owner as OWNER member") {
            withRepositoryTestContext {
                val userRepo = UserRegisterRepositoryImpl()
                val householdRegister = HouseholdRegisterRepositoryImpl()
                val householdReader = HouseholdRepositoryImpl()

                val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
                tx { householdRegister.create(owner) }

                val found = tx { householdReader.findOf(owner) }
                found
                    ?.members
                    ?.asList()
                    ?.single()
                    ?.role shouldBe HouseholdMemberRole.OWNER
            }
        }
    })
