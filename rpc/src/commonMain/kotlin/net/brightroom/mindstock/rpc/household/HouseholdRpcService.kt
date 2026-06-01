package net.brightroom.mindstock.rpc.household

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface HouseholdRpcService {
    /** ログイン中の Resident が所属する世帯一覧(UC5 の切替元)。 */
    suspend fun list(): RpcResult<Households, RpcError>

    /** 招待コードのプレビュー(UC4。参加前)。 */
    suspend fun previewInvite(code: InvitationCode): RpcResult<InvitationPreview, RpcError>
}
