package net.brightroom.mindstock.frontend.feature.household.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.invite_copied
import mindstock.frontend.generated.resources.invite_copy
import mindstock.frontend.generated.resources.invite_desc
import mindstock.frontend.generated.resources.invite_issue
import mindstock.frontend.generated.resources.invite_none
import mindstock.frontend.generated.resources.invite_reissue
import mindstock.frontend.generated.resources.invite_reusable
import mindstock.frontend.generated.resources.invite_revoke
import mindstock.frontend.generated.resources.invite_role_label
import mindstock.frontend.generated.resources.invite_title
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.SegOption
import net.brightroom.mindstock.frontend.designsystem.atom.SegmentedControl
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

@Composable
fun InviteSheet(
    open: Boolean,
    householdName: String,
    issuedInvite: Invitation?,
    onClose: () -> Unit,
    onIssue: (HouseholdMemberRole) -> Unit,
    onRevoke: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current

    // LocalClipboard(suspend API)への移行は別途。現状の同期 setText を維持し deprecation のみ抑制する。
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var selectedRole by remember(open) {
        mutableStateOf(issuedInvite?.grantedRole ?: HouseholdMemberRole.メンバー)
    }
    var copied by remember(open, issuedInvite?.code) { mutableStateOf(false) }
    Sheet(open = open, title = stringResource(Res.string.invite_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            AppText(stringResource(Res.string.invite_desc, householdName), style = MindstockType.summarySub(), color = tokens.sub)

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AppText(stringResource(Res.string.invite_role_label), style = MindstockType.sectionMeta(), color = tokens.faint)
                SegmentedControl(
                    options =
                        listOf(
                            SegOption(HouseholdMemberRole.メンバー.name, stringResource(roleLabelResource(HouseholdMemberRole.メンバー))),
                            SegOption(HouseholdMemberRole.閲覧者.name, stringResource(roleLabelResource(HouseholdMemberRole.閲覧者))),
                        ),
                    selectedKey = selectedRole.name,
                    onSelect = { selectedRole = HouseholdMemberRole.valueOf(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(roleIcon(selectedRole), contentDescription = null, size = 14.dp, tint = tokens.sub)
                    AppText(
                        stringResource(roleDescResource(selectedRole)),
                        style = MindstockType.summarySub().copy(fontSize = 12.sp),
                        color = tokens.faint,
                    )
                }
            }

            if (issuedInvite == null) {
                AppText(
                    stringResource(Res.string.invite_none),
                    style = MindstockType.summarySub(),
                    color = tokens.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppButton(
                    onClick = { onIssue(selectedRole) },
                    size = ButtonSize.Lg,
                    icon = AppIconName.Link,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(stringResource(Res.string.invite_issue))
                }
            } else {
                val code = issuedInvite.code.invoke()
                // big monospace code with copy affordance
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.radiusMd))
                            .background(tokens.surface2)
                            .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusMd))
                            .clickable {
                                clipboard.setText(AnnotatedString(code))
                                copied = true
                            }.padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppText(
                        code,
                        style =
                            MindstockType.summaryTitle().copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                letterSpacing = 0.22.em,
                            ),
                        color = tokens.ink,
                    )
                }
                AppButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    size = ButtonSize.Lg,
                    icon = if (copied) AppIconName.Check else AppIconName.Copy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(stringResource(if (copied) Res.string.invite_copied else Res.string.invite_copy))
                }
                AppText(
                    stringResource(Res.string.invite_reusable),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AppButton(
                        onClick = { onIssue(selectedRole) },
                        variant = ButtonVariant.Ghost,
                        icon = AppIconName.Plus,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(Res.string.invite_reissue))
                    }
                    AppButton(
                        onClick = onRevoke,
                        variant = ButtonVariant.Quiet,
                        icon = AppIconName.Trash,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText(stringResource(Res.string.invite_revoke))
                    }
                }
            }
        }
    }
}
