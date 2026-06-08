package net.brightroom.mindstock.frontend.feature.household.ui

import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.role_member
import mindstock.frontend.generated.resources.role_member_desc
import mindstock.frontend.generated.resources.role_owner
import mindstock.frontend.generated.resources.role_viewer
import mindstock.frontend.generated.resources.role_viewer_desc
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

/**
 * 区分の説明文(mock `data.jsx` の `ROLES[*].desc`)。
 * 世帯主は専用 note(`member_owner_note`)を使うため、ここでは扱える 2 区分のみを対象に member の文言へ寄せる。
 */
fun roleDescResource(role: HouseholdMemberRole): StringResource =
    when (role) {
        HouseholdMemberRole.閲覧者 -> Res.string.role_viewer_desc
        else -> Res.string.role_member_desc
    }
