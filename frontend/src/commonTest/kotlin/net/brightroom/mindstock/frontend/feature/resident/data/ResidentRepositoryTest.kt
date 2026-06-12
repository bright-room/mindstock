package net.brightroom.mindstock.frontend.feature.resident.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private class FakeResidentRegisterService(
    private val result: RpcResult<Resident, RpcError>,
) : ResidentRegisterRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Resident, RpcError> = result

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> = RpcResult.Ok(Unit)
}

class ResidentRepositoryTest {
    @Test
    fun register_maps_ok_to_success() =
        runTest {
            val resident = Resident(ResidentId.create(), ResidentProfile(DisplayName("たろう")))
            val repo = ResidentRepository(residentRegisterService = { FakeResidentRegisterService(RpcResult.Ok(resident)) })
            repo.register(DisplayName("たろう")).shouldBeInstanceOf<RpcOutcome.Success<Resident>>()
        }

    @Test
    fun register_maps_err_to_failure() =
        runTest {
            val repo =
                ResidentRepository(
                    residentRegisterService = { FakeResidentRegisterService(RpcResult.Err(RpcError.Internal("boom"))) },
                )
            repo.register(DisplayName("たろう")).shouldBeInstanceOf<RpcOutcome.Failure>()
        }
}
