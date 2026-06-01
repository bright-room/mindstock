package net.brightroom.mindstock.rpc.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

/**
 * 招待コードのプレビュー(UC4)。参加前のユーザに見せる射影。
 * `Invitation` は内部に `householdId` を持つが、joiner には世帯名と付与ロールだけを見せる。
 */
@Serializable
data class InvitationPreview(
    val householdName: HouseholdName,
    val grantedRole: HouseholdMemberRole,
)
