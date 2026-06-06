package net.brightroom.mindstock.presentation.rpc.session

import net.brightroom.mindstock.application.service.resident.ResidentService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.guard.allowUnregistered
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.session.SessionStatus

class SessionController(
    private val residentService: ResidentService,
    private val session: MindstockSession,
) : SessionRpcService {
    override suspend fun whoami(): RpcResult<SessionStatus, RpcError> =
        allowUnregistered(session) {
            when (session) {
                is MindstockSession.Registered -> {
                    RpcResult.Ok(SessionStatus.Registered(residentService.me(session.residentId)))
                }

                is MindstockSession.Unregistered -> {
                    RpcResult.Ok(SessionStatus.Unregistered)
                }
            }
        }
}
