package net.brightroom.mindstock.frontend.feature.household.ui

import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.role_member
import mindstock.frontend.generated.resources.role_owner
import mindstock.frontend.generated.resources.role_viewer
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import org.jetbrains.compose.resources.StringResource

/** 区分(世帯主/メンバー/閲覧者)を UI ラベル(オーナー/編集できる/閲覧のみ)の文言リソースに対応づける。 */
fun roleLabelResource(role: HouseholdMemberRole): StringResource =
    when (role) {
        HouseholdMemberRole.世帯主 -> Res.string.role_owner
        HouseholdMemberRole.メンバー -> Res.string.role_member
        HouseholdMemberRole.閲覧者 -> Res.string.role_viewer
    }

/** 区分を UI アイコンに対応づける(オーナー=crown / メンバー=pencil / 閲覧者=eye)。 */
fun roleIcon(role: HouseholdMemberRole): AppIconName =
    when (role) {
        HouseholdMemberRole.世帯主 -> AppIconName.Crown
        HouseholdMemberRole.メンバー -> AppIconName.Pencil
        HouseholdMemberRole.閲覧者 -> AppIconName.Eye
    }
