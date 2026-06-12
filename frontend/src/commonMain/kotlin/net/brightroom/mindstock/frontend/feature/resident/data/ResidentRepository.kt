package net.brightroom.mindstock.frontend.feature.resident.data

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService

/** 住人登録まわりの RPC を隠蔽。サービスは認証後に開かれる opener として遅延注入する。 */
class ResidentRepository(
    private val residentRegisterService: () -> ResidentRegisterRpcService,
) {
    suspend fun register(displayName: DisplayName): RpcOutcome<Resident> = residentRegisterService().register(displayName).toOutcome()

    suspend fun rename(displayName: DisplayName): RpcOutcome<Unit> = residentRegisterService().rename(displayName).toOutcome()
}
