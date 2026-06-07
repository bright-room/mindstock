package net.brightroom.mindstock.frontend.feature.household

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

/** 設定画面の表示状態。AppSession 由来 + 発行済み invite(VM メモリ)+ submitting。 */
data class SettingsUiState(
    val displayName: String = "",
    val households: List<HouseholdSummary> = emptyList(),
    val activeId: HouseholdId? = null,
    val activeName: String = "",
    val members: List<MemberRow> = emptyList(),
    val isOwner: Boolean = false,
    val issuedInvite: Invitation? = null,
    val submitting: Boolean = false,
)

/** 切替シート 1 行ぶん。 */
data class HouseholdSummary(
    val id: HouseholdId,
    val name: String,
    val memberCount: Int,
    val myRole: HouseholdMemberRole,
    val active: Boolean,
)

/** メンバー行 1 件ぶん。 */
data class MemberRow(
    val residentId: ResidentId,
    val name: String,
    val role: HouseholdMemberRole,
    val isMe: Boolean,
)
