package net.brightroom.mindstock.e2e.household

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.rpc.HouseholdRpcService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import io.kotest.core.annotation.Tags

/**
 * Pinned behaviors:
 * 1. findOf returns the household the actor owns (and null if none).
 * 2. create makes a new household with the caller as the sole OWNER.
 * 3. invite + revoke alter membership, observable via findOf as the affected user.
 * 4. invite to a non-existent householdId raises a NotFound-class error end-to-end.
 */
@Tags("integration")
@OptIn(ExperimentalUuidApi::class)
class HouseholdRpcServiceE2eTest :
    FunSpec({

        test("findOf returns the household the actor owns") {
            e2eTest {
                val owner = seedUser()
                val expected = seedHousehold(owner)
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()

                val found = rpc.findOf()
                found.shouldNotBeNull()
                found.id shouldBe expected.id
            }
        }

        test("findOf returns null when the actor has no household") {
            e2eTest {
                val orphan = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = orphan, path = "household")
                        .withService<HouseholdRpcService>()

                rpc.findOf().shouldBeNull()
            }
        }

        test("create makes a new household with the actor as sole OWNER") {
            e2eTest {
                val owner = seedUser(displayName = "Owner")
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()

                val household = rpc.create()

                household.members.list.shouldNotBeNull()
                household.members.list.size shouldBe 1
                household.members.list[0]
                    .user.id shouldBe owner.id
                household.members.list[0].role shouldBe HouseholdMemberRole.OWNER
            }
        }

        test("invite adds a new MEMBER, observable when that user calls findOf") {
            e2eTest {
                val owner = seedUser()
                val household = seedHousehold(owner)
                val invitee = seedUser(displayName = "Invitee")
                val ownerRpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()

                ownerRpc.invite(household.id, invitee.id, HouseholdMemberRole.MEMBER)

                val inviteeRpc =
                    authenticatedRpcClient(asUser = invitee, path = "household")
                        .withService<HouseholdRpcService>()
                val seen = inviteeRpc.findOf()
                seen.shouldNotBeNull()
                seen.id shouldBe household.id
            }
        }

        test("invite to an unknown householdId is rejected") {
            e2eTest {
                val owner = seedUser()
                val invitee = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()

                shouldThrowAny {
                    rpc.invite(
                        householdId = HouseholdId(Uuid.random()),
                        invitee = invitee.id,
                        role = HouseholdMemberRole.MEMBER,
                    )
                }
            }
        }

        test("revoke removes the member, observable as null findOf for that user") {
            e2eTest {
                val owner = seedUser()
                val household = seedHousehold(owner)
                val member = seedUser()
                val ownerRpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()
                ownerRpc.invite(household.id, member.id, HouseholdMemberRole.MEMBER)

                ownerRpc.revoke(household.id, member.id)

                val memberRpc =
                    authenticatedRpcClient(asUser = member, path = "household")
                        .withService<HouseholdRpcService>()
                memberRpc.findOf().shouldBeNull()
            }
        }
    })
