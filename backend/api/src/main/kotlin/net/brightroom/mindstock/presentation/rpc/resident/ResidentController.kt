package net.brightroom.mindstock.presentation.rpc.resident

import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.auth.requireResidentId
import net.brightroom.mindstock.configuration.guard.guarded
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.rpc.resident.ResidentRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

class ResidentController(
    private val residentService: ResidentService,
    private val session: MindstockSession,
) : ResidentRpcService {
    override suspend fun me(): RpcResult<Resident, RpcError> =
        guarded(session) { RpcResult.Ok(residentService.me(session.requireResidentId())) }
}
