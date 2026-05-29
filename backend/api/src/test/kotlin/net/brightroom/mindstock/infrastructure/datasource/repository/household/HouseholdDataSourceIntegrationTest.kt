package net.brightroom.mindstock.infrastructure.datasource.household

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class HouseholdDataSourceIntegrationTest :
    FunSpec({

        test("findOf returns null when user has no household membership") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdReader = HouseholdDataSource()
                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("lonely")), DisplayName("Lonely")) }

                val result = tx { householdReader.findOf(user.userId) }

                result.shouldBeNull()
            }
        }

        test("findById returns a household whose only membership has been revoked (with empty members)") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRegister = HouseholdRegisterDataSource()
                val householdReader = HouseholdDataSource()

                val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("rev-owner")), DisplayName("RevOwner")) }
                val created = tx { householdRegister.create(owner.userId) }
                tx { householdRegister.revoke(created, owner.userId) }

                val found = tx { householdReader.findById(created.id) }

                found.shouldNotBeNull()
                found.id shouldBe created.id
                found.members.asList() shouldBe emptyList()
            }
        }

        test("findById returns null when no household with this id exists") {
            withRepositoryTestContext {
                val householdReader = HouseholdDataSource()
                val nonexistent = HouseholdId(Uuid.random())

                val found = tx { householdReader.findById(nonexistent) }

                found.shouldBeNull()
            }
        }

        test("findOf returns the household with owner as OWNER member") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRegister = HouseholdRegisterDataSource()
                val householdReader = HouseholdDataSource()

                val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
                tx { householdRegister.create(owner.userId) }

                val found = tx { householdReader.findOf(owner.userId) }
                found
                    ?.members
                    ?.asList()
                    ?.single()
                    ?.role shouldBe HouseholdMemberRole.OWNER
            }
        }
    })
