package net.brightroom.mindstock.infrastructure.datasource.household

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthProvider
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthSubject
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.infrastructure.datasource.resident.ResidentRegisterDataSource
import net.brightroom.mindstock.testfixtures.TestDatabase

@Tags("integration")
class HouseholdDataSourceTest :
    FunSpec({
        val db = TestDatabase.database
        val residents = ResidentRegisterDataSource(db)
        val householdRegister = HouseholdRegisterDataSource(db)
        val householdDataSource = HouseholdDataSource(db)

        beforeTest { TestDatabase.clean() }

        test("findById: owner と joinMember したメンバーが role/displayName 付きで引ける") {
            val owner =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-owner-1")),
                    DisplayName("おーなー"),
                )
            val member =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-member-1")),
                    DisplayName("めんばー"),
                )
            val household = Household.create(HouseholdName("我が家"), owner)
            householdRegister.registerHousehold(household)
            householdRegister.joinMember(household.id, member, HouseholdMemberRole.メンバー)

            val loaded = householdDataSource.findById(household.id)

            // 世帯名・メンバー件数の検証
            loaded.profile.name() shouldBe "我が家"
            loaded.members.size() shouldBe 2

            // 各メンバーを 1 度だけ引き当て、role と displayName(hydrate)を同一メンバー上で検証
            val ownerMember = loaded.members.list.first { it.resident.id == owner.id }
            val joinedMember = loaded.members.list.first { it.resident.id == member.id }
            ownerMember.role shouldBe HouseholdMemberRole.世帯主
            ownerMember.resident.profile.displayName shouldBe DisplayName("おーなー")
            joinedMember.role shouldBe HouseholdMemberRole.メンバー
            joinedMember.resident.profile.displayName shouldBe DisplayName("めんばー")
        }

        test("findById: rename 後は最新の世帯名が引ける") {
            val owner =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-owner-2")),
                    DisplayName("おーなー2"),
                )
            val household = Household.create(HouseholdName("旧名"), owner)
            householdRegister.registerHousehold(household)
            householdRegister.appendHouseholdName(household.id, HouseholdName("新名"))

            val loaded = householdDataSource.findById(household.id)

            // 最新名が引けること
            loaded.profile.name() shouldBe "新名"
        }

        test("listByResident: 同一 resident が owner の世帯が 2 つあれば 2 件返る") {
            val owner =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-owner-3")),
                    DisplayName("おーなー3"),
                )
            val householdA = Household.create(HouseholdName("家A"), owner)
            val householdB = Household.create(HouseholdName("家B"), owner)
            householdRegister.registerHousehold(householdA)
            householdRegister.registerHousehold(householdB)

            val result = householdDataSource.listByResident(owner.id)

            // 件数だけでなく、返る 2 世帯が登録した A/B 本人であること(N+1 リファクタの安全網)
            result.size() shouldBe 2
            result.list.map { it.id }.toSet() shouldBe setOf(householdA.id, householdB.id)
        }

        test("listByResident: removeMember された世帯は返らない") {
            val owner =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-owner-4")),
                    DisplayName("おーなー4"),
                )
            val leaver =
                residents.registerResident(
                    AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-leaver-4")),
                    DisplayName("たいしゃ4"),
                )
            val household = Household.create(HouseholdName("退出テスト世帯"), owner)
            householdRegister.registerHousehold(household)
            householdRegister.joinMember(household.id, leaver, HouseholdMemberRole.メンバー)
            householdRegister.removeMember(household.id, leaver.id)

            // 除外後は leaver の listByResident が 0 件
            val leaverResult = householdDataSource.listByResident(leaver.id)
            leaverResult.size() shouldBe 0

            // owner は引き続き 1 件返る
            val ownerResult = householdDataSource.listByResident(owner.id)
            ownerResult.size() shouldBe 1
        }
    })
