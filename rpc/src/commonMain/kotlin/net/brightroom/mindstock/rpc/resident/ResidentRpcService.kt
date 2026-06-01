package net.brightroom.mindstock.rpc.resident

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ResidentRpcService {
    /** ログイン中の Resident を取得(UC2 の `me`)。actor は session 由来。 */
    suspend fun me(): RpcResult<Resident, RpcError>
}
