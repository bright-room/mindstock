package net.brightroom.mindstock.e2e.household

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.e2e.e2eTest
import net.brightroom.mindstock.e2e.seedHousehold
import net.brightroom.mindstock.e2e.seedUser
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pinned behaviors:
 * 1. findOf returns the household the actor owns (and Err(NotFound) if none).
 * 2. create makes a new household with the caller as the sole OWNER.
 * 3. invite + revoke alter membership, observable via findOf as the affected user.
 * 4. invite to a non-existent householdId raises a NotFound error end-to-end.
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

                val r = rpc.findOf()
                r.shouldBeInstanceOf<RpcResult.Ok<Household>>()
                val found = r.value
                found.id shouldBe expected.id
            }
        }

        test("findOf returns Err(NotFound) when the actor has no household") {
            e2eTest {
                val orphan = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = orphan, path = "household")
                        .withService<HouseholdRpcService>()

                val r = rpc.findOf()
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                val err = r.error
                err.shouldBeInstanceOf<RpcError.NotFound>()
                err.message shouldContain "household not found"
            }
        }

        test("create makes a new household with the actor as sole OWNER") {
            e2eTest {
                val owner = seedUser(displayName = "Owner")
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()

                val r = rpc.create()
                r.shouldBeInstanceOf<RpcResult.Ok<Household>>()
                val household = r.value

                household.members.list.size shouldBe 1
                household.members.list[0]
                    .profile.userId shouldBe owner.userId
                household.members.list[0]
                    .profile.displayName shouldBe owner.displayName
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

                val invite = ownerRpc.invite(household.id, invitee.userId, HouseholdMemberRole.MEMBER)
                invite.shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                val inviteeRpc =
                    authenticatedRpcClient(asUser = invitee, path = "household")
                        .withService<HouseholdRpcService>()
                val seen = inviteeRpc.findOf()
                seen.shouldBeInstanceOf<RpcResult.Ok<Household>>()
                seen.value.id shouldBe household.id
            }
        }

        test("invite to an unknown householdId returns Err(NotFound)") {
            e2eTest {
                val owner = seedUser()
                val invitee = seedUser()
                val rpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()

                val r =
                    rpc.invite(
                        householdId = HouseholdId(Uuid.random()),
                        invitee = invitee.userId,
                        role = HouseholdMemberRole.MEMBER,
                    )
                r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                val err = r.error
                err.shouldBeInstanceOf<RpcError.NotFound>()
                err.message shouldContain "household not found"
            }
        }

        test("revoke removes the member, findOf for that user then returns Err(NotFound)") {
            e2eTest {
                val owner = seedUser()
                val household = seedHousehold(owner)
                val member = seedUser()
                val ownerRpc =
                    authenticatedRpcClient(asUser = owner, path = "household")
                        .withService<HouseholdRpcService>()
                ownerRpc
                    .invite(household.id, member.userId, HouseholdMemberRole.MEMBER)
                    .shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                val revoked = ownerRpc.revoke(household.id, member.userId)
                revoked.shouldBeInstanceOf<RpcResult.Ok<Unit>>()

                val memberRpc =
                    authenticatedRpcClient(asUser = member, path = "household")
                        .withService<HouseholdRpcService>()
                val seen = memberRpc.findOf()
                seen.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
                val err = seen.error
                err.shouldBeInstanceOf<RpcError.NotFound>()
                err.message shouldContain "household not found"
            }
        }
    })
