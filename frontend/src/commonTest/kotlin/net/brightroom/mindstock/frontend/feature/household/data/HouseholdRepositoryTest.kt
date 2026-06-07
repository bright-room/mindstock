package net.brightroom.mindstock.frontend.feature.household.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.Profile
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.household.InvitationPreview
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private fun household(name: String) = Household(HouseholdId.create(), Profile(HouseholdName(name)), Members(emptyList()))

private class FakeHouseholdService(
    private val preview: RpcResult<InvitationPreview, RpcError>,
) : HouseholdRpcService {
    override suspend fun list(): RpcResult<Households, RpcError> = RpcResult.Ok(Households(emptyList()))

    override suspend fun previewInvite(code: InvitationCode): RpcResult<InvitationPreview, RpcError> = preview
}

private class FakeHouseholdRegisterService(
    private val createResult: RpcResult<Household, RpcError>,
    private val joinResult: RpcResult<Household, RpcError>,
) : HouseholdRegisterRpcService {
    override suspend fun create(name: HouseholdName): RpcResult<Household, RpcError> = createResult

    override suspend fun rename(
        householdId: HouseholdId,
        name: HouseholdName,
    ): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun leave(householdId: HouseholdId): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcResult<Invitation, RpcError> = RpcResult.Err(RpcError.Internal("n/a"))

    override suspend fun revokeInvite(code: InvitationCode): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)

    override suspend fun join(code: InvitationCode): RpcResult<Household, RpcError> = joinResult
}

private fun repo(
    preview: RpcResult<InvitationPreview, RpcError> = RpcResult.Ok(InvitationPreview(HouseholdName("家"), HouseholdMemberRole.メンバー)),
    create: RpcResult<Household, RpcError> = RpcResult.Ok(household("家")),
    join: RpcResult<Household, RpcError> = RpcResult.Ok(household("家")),
) = HouseholdRepository(
    householdService = { FakeHouseholdService(preview) },
    householdRegisterService = { FakeHouseholdRegisterService(create, join) },
)

class HouseholdRepositoryTest {
    @Test
    fun create_maps_ok() = runTest { repo().create(HouseholdName("家")).shouldBeInstanceOf<RpcOutcome.Success<Household>>() }

    @Test
    fun create_maps_err() =
        runTest {
            repo(create = RpcResult.Err(RpcError.Conflict("dup"))).create(HouseholdName("家")).shouldBeInstanceOf<RpcOutcome.Failure>()
        }

    @Test
    fun preview_maps_ok() =
        runTest {
            repo().previewInvite(InvitationCode.generate()).shouldBeInstanceOf<RpcOutcome.Success<InvitationPreview>>()
        }

    @Test
    fun preview_maps_err() =
        runTest {
            repo(
                preview = RpcResult.Err(RpcError.NotFound("no")),
            ).previewInvite(InvitationCode.generate()).shouldBeInstanceOf<RpcOutcome.Failure>()
        }

    @Test
    fun join_maps_ok() = runTest { repo().join(InvitationCode.generate()).shouldBeInstanceOf<RpcOutcome.Success<Household>>() }
}
