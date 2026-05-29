package net.brightroom.mindstock.infrastructure.datasource.household

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.auth.AuthProvider
import net.brightroom.mindstock.domain.model.user.auth.AuthSubject
import net.brightroom.mindstock.infrastructure.datasource.repository.withRepositoryTestContext
import net.brightroom.mindstock.infrastructure.datasource.user.UserRegisterDataSource

@Tags("integration")
class HouseholdRegisterDataSourceIntegrationTest :
    FunSpec({

        test("create inserts household + OWNER membership and returns Household with owner as member") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val owner =
                    tx {
                        userRepo.register(
                            AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")),
                            DisplayName("Owner"),
                        )
                    }

                val household = tx { householdRepo.create(owner) }

                household.members.asList() shouldHaveSize 1
                household.members
                    .asList()
                    .first()
                    .role shouldBe HouseholdMemberRole.OWNER
            }
        }

        test("invite adds another active member that appears in findOf") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val householdReader = HouseholdDataSource()

                val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
                val invitee = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("invitee")), DisplayName("Invitee")) }
                val household = tx { householdRepo.create(owner) }
                tx { householdRepo.invite(household, invitee, HouseholdMemberRole.MEMBER) }

                val refetched = tx { householdReader.findOf(invitee) }
                refetched!!.members.asList().map { it.user.displayName } shouldContain DisplayName("Invitee")
            }
        }

        test("revoke removes the revoked member from active membership view") {
            withRepositoryTestContext {
                val userRepo = UserRegisterDataSource()
                val householdRepo = HouseholdRegisterDataSource()
                val householdReader = HouseholdDataSource()

                val owner = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("owner")), DisplayName("Owner")) }
                val invitee = tx { userRepo.register(AuthIdentity(AuthProvider.ZITADEL, AuthSubject("invitee")), DisplayName("Invitee")) }
                val household = tx { householdRepo.create(owner) }
                tx { householdRepo.invite(household, invitee, HouseholdMemberRole.MEMBER) }
                tx { householdRepo.revoke(household, invitee) }

                val refetched = tx { householdReader.findOf(invitee) }
                refetched.shouldBeNull()
            }
        }
    })
