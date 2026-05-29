package net.brightroom.mindstock.infrastructure.datasource.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
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

        test("findOf throws ResourceNotFoundException when user has no household membership") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdReader = HouseholdDataSource()
                val user = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("lonely")), DisplayName("Lonely")) }

                shouldThrow<ResourceNotFoundException> {
                    tx { householdReader.findOf(user.userId) }
                }.message shouldContain "household not found"
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
                found.members.list shouldBe emptyList()
            }
        }

        test("findById throws ResourceNotFoundException when no household with this id exists") {
            withRepositoryTestContext {
                val householdReader = HouseholdDataSource()
                val nonexistent = HouseholdId(Uuid.random())

                shouldThrow<ResourceNotFoundException> {
                    tx { householdReader.findById(nonexistent) }
                }.message shouldContain "household not found"
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
                found.members
                    .list
                    .single()
                    .role shouldBe HouseholdMemberRole.OWNER
            }
        }
    })
