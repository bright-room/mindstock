package net.brightroom.mindstock.infrastructure.datasource.household

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource

@Tags("integration")
class HouseholdRegisterDataSourceIntegrationTest :
    FunSpec({

        test("create inserts household + OWNER membership and returns Household with owner as member") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val householdRepo = HouseholdRegisterDataSource(database)
                val owner =
                    runBlocking {
                        userRepo.register(
                            AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")),
                            DisplayName("Owner"),
                        )
                    }

                val household = runBlocking { householdRepo.create(owner.userId) }

                household.members.list shouldHaveSize 1
                household.members
                    .list
                    .first()
                    .role shouldBe HouseholdMemberRole.OWNER
            }
        }

        test("invite adds another active member that appears in findOf") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val householdRepo = HouseholdRegisterDataSource(database)
                val householdReader = HouseholdDataSource(database)

                val owner =
                    runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
                val invitee =
                    runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("invitee")), DisplayName("Invitee")) }
                val household = runBlocking { householdRepo.create(owner.userId) }
                runBlocking { householdRepo.invite(household, invitee.userId, HouseholdMemberRole.MEMBER) }

                val refetched = runBlocking { householdReader.findOf(invitee.userId) }
                refetched.members.list.map { it.profile.displayName } shouldContain DisplayName("Invitee")
            }
        }

        test("revoke removes the revoked member from active membership view") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource(database)
                val householdRepo = HouseholdRegisterDataSource(database)
                val householdReader = HouseholdDataSource(database)

                val owner =
                    runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
                val invitee =
                    runBlocking { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("invitee")), DisplayName("Invitee")) }
                val household = runBlocking { householdRepo.create(owner.userId) }
                runBlocking { householdRepo.invite(household, invitee.userId, HouseholdMemberRole.MEMBER) }
                runBlocking { householdRepo.revoke(household, invitee.userId) }

                shouldThrow<ResourceNotFoundException> {
                    runBlocking { householdReader.findOf(invitee.userId) }
                }.message shouldContain "household not found"
            }
        }
    })
