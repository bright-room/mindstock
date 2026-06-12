package net.brightroom.mindstock.infrastructure.datasource.invitation

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationValidity
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.household.HouseholdRegisterDataSource
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class InvitationDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residentRegister = ResidentRegisterDataSource(db)
        val householdRegister = HouseholdRegisterDataSource(db)
        val invitationRegister = InvitationRegisterDataSource(db)
        val invitationDataSource = InvitationDataSource(db)

        beforeTest { TestDatabase.clean() }

        fun createHousehold(authSubject: String): Household {
            val owner =
                residentRegister.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject(authSubject)),
                    DisplayName("テストオーナー"),
                )
            val household = Household.create(HouseholdName("テスト世帯"), owner)
            householdRegister.registerHousehold(household)
            return household
        }

        test("issue した招待は有効で引ける") {
            val household = createHousehold("sub-invitation-1")
            val issued = invitationRegister.issue(Invitation.issue(household.id, HouseholdMemberRole.メンバー))

            val loaded = invitationDataSource.findByCode(issued.code)

            loaded.householdId shouldBe household.id
            loaded.grantedRole shouldBe HouseholdMemberRole.メンバー
            loaded.validity shouldBe InvitationValidity.有効
        }

        test("revoke すると最新 validity が無効になる") {
            val household = createHousehold("sub-invitation-2")
            val issued = invitationRegister.issue(Invitation.issue(household.id, HouseholdMemberRole.メンバー))
            invitationRegister.revoke(issued.code)

            val loaded = invitationDataSource.findByCode(issued.code)

            loaded.validity shouldBe InvitationValidity.無効
        }
    })
