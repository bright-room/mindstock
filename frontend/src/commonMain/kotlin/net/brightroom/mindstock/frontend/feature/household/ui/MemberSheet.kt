package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.member_owner_note
import mindstock.frontend.generated.resources.member_remove
import mindstock.frontend.generated.resources.member_remove_cancel
import mindstock.frontend.generated.resources.member_remove_confirm
import mindstock.frontend.generated.resources.member_remove_do
import mindstock.frontend.generated.resources.member_role_label
import mindstock.frontend.generated.resources.member_title
import mindstock.frontend.generated.resources.member_viewer_note
import mindstock.frontend.generated.resources.member_you
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.avatarColorOf
import net.brightroom.mindstock.frontend.feature.household.MemberRow
import org.jetbrains.compose.resources.stringResource

@Composable
fun MemberSheet(
    open: Boolean,
    member: MemberRow?,
    isOwnerSelf: Boolean,
    onClose: () -> Unit,
    onChangeRole: (ResidentId, HouseholdMemberRole) -> Unit,
    onRemove: (ResidentId) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    var confirm by remember(open, member?.residentId) { mutableStateOf(false) }
    Sheet(open = open && member != null, title = stringResource(Res.string.member_title), onClose = onClose) {
        val m = member ?: return@Sheet
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            // header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(99.dp)).background(avatarColorOf(m.name)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(m.name.take(1), style = MindstockType.summaryTitle().copy(fontSize = 21.sp), color = Color.White)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(m.name, style = MindstockType.summaryTitle(), color = tokens.ink)
                        if (m.isMe) {
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tokens.surface2)
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                            ) {
                                AppText(stringResource(Res.string.member_you), style = MindstockType.unitCaption(), color = tokens.sub)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        AppIcon(roleIcon(m.role), contentDescription = null, size = 14.dp, tint = tokens.faint)
                        AppText(stringResource(roleLabelResource(m.role)), style = MindstockType.summarySub(), color = tokens.faint)
                    }
                }
            }

            when {
                m.role == HouseholdMemberRole.世帯主 -> {
                    NoteCard(icon = AppIconName.Crown, text = stringResource(Res.string.member_owner_note), accent = true)
                }

                isOwnerSelf && !m.isMe -> {
                    AppText(
                        stringResource(Res.string.member_role_label),
                        style = MindstockType.sectionMeta(),
                        color = tokens.faint,
                        modifier = Modifier.padding(bottom = 9.dp),
                    )
                    SegmentedControl(
                        options =
                            listOf(
                                SegOption(HouseholdMemberRole.メンバー.name, stringResource(roleLabelResource(HouseholdMemberRole.メンバー))),
                                SegOption(HouseholdMemberRole.閲覧者.name, stringResource(roleLabelResource(HouseholdMemberRole.閲覧者))),
                            ),
                        selectedKey = m.role.name,
                        onSelect = { onChangeRole(m.residentId, HouseholdMemberRole.valueOf(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppText(
                        stringResource(roleDescResource(m.role)),
                        style = MindstockType.summarySub().copy(fontSize = 12.sp),
                        color = tokens.faint,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (confirm) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp)
                                    .clip(RoundedCornerShape(tokens.radiusMd))
                                    .background(tokens.statusOutSoft)
                                    .padding(16.dp),
                        ) {
                            AppText(
                                stringResource(Res.string.member_remove_confirm, m.name),
                                style = MindstockType.summarySub(),
                                color = tokens.statusOut,
                                modifier = Modifier.padding(bottom = 14.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                AppButton(
                                    onClick = { confirm = false },
                                    variant = ButtonVariant.Ghost,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    AppText(stringResource(Res.string.member_remove_cancel))
                                }
                                AppButton(
                                    onClick = { onRemove(m.residentId) },
                                    variant = ButtonVariant.Danger,
                                    icon = AppIconName.Trash,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    AppText(stringResource(Res.string.member_remove_do))
                                }
                            }
                        }
                    } else {
                        AppButton(
                            onClick = { confirm = true },
                            variant = ButtonVariant.Quiet,
                            icon = AppIconName.Trash,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        ) {
                            AppText(stringResource(Res.string.member_remove), color = tokens.statusOut)
                        }
                    }
                }

                else -> {
                    NoteCard(icon = AppIconName.User, text = stringResource(Res.string.member_viewer_note), accent = false)
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    icon: AppIconName,
    text: String,
    accent: Boolean,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMd))
                .background(if (accent) tokens.accentSoft else tokens.surface2)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIcon(icon, contentDescription = null, size = 18.dp, tint = if (accent) tokens.accent else tokens.sub)
        AppText(text, style = MindstockType.summarySub(), color = if (accent) tokens.accent else tokens.sub)
    }
}
