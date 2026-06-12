package net.brightroom.mindstock.presentation.rpc.resident

import net.brightroom.mindstock.application.service.resident.ResidentRegisterService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.allowUnregistered
import net.brightroom.mindstock.configuration.guard.requireRegistered
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ResidentRegisterController(
    private val residentRegisterService: ResidentRegisterService,
    private val session: MindstockSession,
) : ResidentRegisterRpcService {
    override suspend fun register(displayName: DisplayName): RpcResult<Resident, RpcError> =
        allowUnregistered(session) {
            when (session) {
                is MindstockSession.Registered -> RpcResult.Err(RpcError.Conflict(reason = "already registered"))
                is MindstockSession.Unregistered -> RpcResult.Ok(residentRegisterService.register(session.identity, displayName))
            }
        }

    override suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError> =
        requireRegistered(session) { residentId ->
            residentRegisterService.rename(residentId, displayName)
            RpcResult.Ok(Unit)
        }
}
