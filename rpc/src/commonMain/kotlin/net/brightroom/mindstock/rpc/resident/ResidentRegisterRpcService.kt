package net.brightroom.mindstock.rpc.resident

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ResidentRegisterRpcService {
    /** 初回:表示名を登録する(UC2)。AuthIdentity は session 由来(引数で受けない)。 */
    suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError>

    /** 表示名を変更する。 */
    suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError>
}
