package net.brightroom.mindstock.frontend.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.invite_title
import mindstock.frontend.generated.resources.leave_cancel
import mindstock.frontend.generated.resources.leave_confirm
import mindstock.frontend.generated.resources.leave_do
import mindstock.frontend.generated.resources.member_you
import mindstock.frontend.generated.resources.need_household_create
import mindstock.frontend.generated.resources.need_household_join
import mindstock.frontend.generated.resources.need_household_sub
import mindstock.frontend.generated.resources.need_household_title
import mindstock.frontend.generated.resources.settings_account_provider
import mindstock.frontend.generated.resources.settings_badge_future
import mindstock.frontend.generated.resources.settings_badge_soon
import mindstock.frontend.generated.resources.settings_display_name_edit
import mindstock.frontend.generated.resources.settings_eyebrow
import mindstock.frontend.generated.resources.settings_footer
import mindstock.frontend.generated.resources.settings_household_member_count
import mindstock.frontend.generated.resources.settings_household_rename
import mindstock.frontend.generated.resources.settings_invite_owner_only
import mindstock.frontend.generated.resources.settings_leave
import mindstock.frontend.generated.resources.settings_logout
import mindstock.frontend.generated.resources.settings_master_entry
import mindstock.frontend.generated.resources.settings_master_entry_sub
import mindstock.frontend.generated.resources.settings_other_archived
import mindstock.frontend.generated.resources.settings_other_trend
import mindstock.frontend.generated.resources.settings_owner_badge
import mindstock.frontend.generated.resources.settings_pref_offline
import mindstock.frontend.generated.resources.settings_pref_offline_sub
import mindstock.frontend.generated.resources.settings_pref_push
import mindstock.frontend.generated.resources.settings_pref_push_sub
import mindstock.frontend.generated.resources.settings_section_household
import mindstock.frontend.generated.resources.settings_section_other
import mindstock.frontend.generated.resources.settings_section_preferences
import mindstock.frontend.generated.resources.settings_switch
import mindstock.frontend.generated.resources.settings_tab_title
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.EmptyState
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.atom.Toggle
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.avatarColorOf
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.household.MemberRow
import net.brightroom.mindstock.frontend.feature.household.SettingsUiState
import net.brightroom.mindstock.frontend.feature.household.ui.InviteSheet
import net.brightroom.mindstock.frontend.feature.household.ui.MemberSheet
import net.brightroom.mindstock.frontend.feature.household.ui.roleIcon
import net.brightroom.mindstock.frontend.feature.household.ui.roleLabelResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRenameDisplayName: (DisplayName) -> Unit,
    onRenameHousehold: (HouseholdName) -> Unit,
    onChangeRole: (ResidentId, HouseholdMemberRole) -> Unit,
    onRemoveMember: (ResidentId) -> Unit,
    onLeave: () -> Unit,
    onIssueInvite: (HouseholdMemberRole) -> Unit,
    onRevokeInvite: () -> Unit,
    onOpenMaster: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    var selectedMemberId by remember { mutableStateOf<ResidentId?>(null) }
    var inviteOpen by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.surface2)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
    ) {
        // 1. header
        Column(modifier = Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            AppText(stringResource(Res.string.settings_eyebrow), style = MindstockType.greeting(), color = tokens.faint)
            AppText(stringResource(Res.string.settings_tab_title), style = MindstockType.screenTitle(), color = tokens.ink)
        }

        // 2. account card
        AccountCard(
            displayName = state.displayName,
            onRenameDisplayName = onRenameDisplayName,
        )

        // 3. household section
        SectionLabel(stringResource(Res.string.settings_section_household))
        if (state.activeId == null) {
            NoHouseholdFallback(onOpenSwitcher = onOpenSwitcher)
        } else {
            HouseholdCard(
                state = state,
                onRenameHousehold = onRenameHousehold,
                onOpenSwitcher = onOpenSwitcher,
                onSelectMember = { selectedMemberId = it.residentId },
                onOpenInvite = { inviteOpen = true },
                onLeave = onLeave,
            )
        }

        // 4. product master entry (owner only)
        if (state.activeId != null && state.isOwner) {
            MasterEntryCard(onOpenMaster = onOpenMaster)
        }

        // 5. preferences
        SectionLabel(stringResource(Res.string.settings_section_preferences))
        Card {
            ToggleRow(
                icon = AppIconName.Bell,
                label = stringResource(Res.string.settings_pref_push),
                sub = stringResource(Res.string.settings_pref_push_sub),
                top = false,
            )
            ToggleRow(
                icon = AppIconName.Bolt,
                label = stringResource(Res.string.settings_pref_offline),
                sub = stringResource(Res.string.settings_pref_offline_sub),
                top = true,
            )
        }

        // 6. other
        if (state.activeId != null) {
            SectionLabel(stringResource(Res.string.settings_section_other))
            Card {
                LinkRow(
                    icon = AppIconName.Trend,
                    label = stringResource(Res.string.settings_other_trend),
                    soon = true,
                    top = false,
                    onClick = null,
                )
                LinkRow(
                    icon = AppIconName.Archive,
                    label = stringResource(Res.string.settings_other_archived),
                    soon = false,
                    top = true,
                    onClick = onOpenArchived,
                )
            }
        }

        // 7. logout + footer
        AppButton(
            onClick = onLogout,
            variant = ButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppText(stringResource(Res.string.settings_logout))
        }
        AppText(
            stringResource(Res.string.settings_footer),
            style = MindstockType.unitCaption(),
            color = tokens.faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        )
    }

    val selectedMember = state.members.firstOrNull { it.residentId == selectedMemberId }
    MemberSheet(
        open = selectedMember != null,
        member = selectedMember,
        isOwnerSelf = state.isOwner,
        onClose = { selectedMemberId = null },
        onChangeRole = onChangeRole,
        onRemove = {
            onRemoveMember(it)
            selectedMemberId = null
        },
    )
    InviteSheet(
        open = inviteOpen,
        householdName = state.activeName,
        issuedInvite = state.issuedInvite,
        onClose = { inviteOpen = false },
        onIssue = onIssueInvite,
        onRevoke = onRevokeInvite,
    )
}

@Composable
private fun AccountCard(
    displayName: String,
    onRenameDisplayName: (DisplayName) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(displayName) }

    Box(
        modifier = Modifier.cardSurface().padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(99.dp)).background(tokens.accent),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    displayName.take(1),
                    style = MindstockType.bigQty().copy(fontSize = 24.sp, lineHeight = 24.sp),
                    color = tokens.onAccent,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(
                            value = draft,
                            onValueChange = { if (it.length <= DisplayName.MAX_LENGTH) draft = it },
                            placeholder = stringResource(Res.string.settings_display_name_edit),
                            modifier = Modifier.weight(1f),
                        )
                        val canSave = draft.isNotBlank()
                        AppButton(
                            onClick = {
                                if (canSave) onRenameDisplayName(DisplayName(draft))
                                editing = false
                            },
                            icon = AppIconName.Check,
                            enabled = canSave,
                        ) {}
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppText(
                            displayName,
                            style = MindstockType.summaryTitle(),
                            color = tokens.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        AppIcon(
                            AppIconName.Pencil,
                            contentDescription = stringResource(Res.string.settings_display_name_edit),
                            size = 15.dp,
                            tint = tokens.accent,
                            modifier =
                                Modifier.clickable {
                                    draft = displayName
                                    editing = true
                                },
                        )
                    }
                }
                AppText(stringResource(Res.string.settings_account_provider), style = MindstockType.summarySub(), color = tokens.faint)
            }
        }
    }
}

@Composable
private fun NoHouseholdFallback(onOpenSwitcher: () -> Unit) {
    Column(
        modifier = Modifier.cardSurface().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyState(
            icon = AppIconName.Home,
            title = stringResource(Res.string.need_household_title),
            sub = stringResource(Res.string.need_household_sub),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            AppButton(
                onClick = onOpenSwitcher,
                icon = AppIconName.Home,
                modifier = Modifier.weight(1f),
            ) {
                AppText(stringResource(Res.string.need_household_create))
            }
            AppButton(
                onClick = onOpenSwitcher,
                variant = ButtonVariant.Ghost,
                icon = AppIconName.Link,
                modifier = Modifier.weight(1f),
            ) {
                AppText(stringResource(Res.string.need_household_join))
            }
        }
    }
}

@Composable
private fun HouseholdCard(
    state: SettingsUiState,
    onRenameHousehold: (HouseholdName) -> Unit,
    onOpenSwitcher: () -> Unit,
    onSelectMember: (MemberRow) -> Unit,
    onOpenInvite: () -> Unit,
    onLeave: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    var hhEditing by remember { mutableStateOf(false) }
    var hhDraft by remember { mutableStateOf(state.activeName) }
    var leaveConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.cardSurface(),
    ) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tokens.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconName.Home, contentDescription = null, size = 18.dp, tint = tokens.accent)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (hhEditing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextInput(
                                value = hhDraft,
                                onValueChange = { if (it.length <= HouseholdName.MAX_LENGTH) hhDraft = it },
                                placeholder = state.activeName,
                                modifier = Modifier.weight(1f),
                            )
                            val canSave = hhDraft.isNotBlank()
                            AppIcon(
                                AppIconName.Check,
                                contentDescription = null,
                                size = 16.dp,
                                tint = if (canSave) tokens.accent else tokens.faint,
                                modifier =
                                    Modifier.clickable {
                                        if (canSave) onRenameHousehold(HouseholdName(hhDraft))
                                        hhEditing = false
                                    },
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            AppText(
                                state.activeName,
                                style = MindstockType.cardTitle(),
                                color = tokens.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (state.isOwner) {
                                AppIcon(
                                    AppIconName.Pencil,
                                    contentDescription = stringResource(Res.string.settings_household_rename),
                                    size = 14.dp,
                                    tint = tokens.accent,
                                    modifier =
                                        Modifier.clickable {
                                            hhDraft = state.activeName
                                            hhEditing = true
                                        },
                                )
                            }
                        }
                    }
                    AppText(
                        stringResource(Res.string.settings_household_member_count, state.members.size),
                        style = MindstockType.unitCaption(),
                        color = tokens.faint,
                    )
                }
            }
            // switch pill
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(tokens.accentSoft)
                        .clickable { onOpenSwitcher() }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppIcon(AppIconName.Swap, contentDescription = null, size = 15.dp, tint = tokens.accent)
                AppText(stringResource(Res.string.settings_switch), style = MindstockType.summarySub(), color = tokens.accent)
            }
        }

        // member rows
        state.members.forEach { m ->
            MemberRowItem(member = m, onClick = { onSelectMember(m) })
        }

        // invite section
        Column(modifier = Modifier.fillMaxWidth().topDivider(tokens.lineSoft).padding(14.dp)) {
            if (state.isOwner) {
                AppButton(
                    onClick = onOpenInvite,
                    variant = ButtonVariant.Soft,
                    icon = AppIconName.Plus,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(stringResource(Res.string.invite_title))
                }
            } else {
                val ownerName = state.members.firstOrNull { it.role == HouseholdMemberRole.世帯主 }?.name ?: ""
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    AppIcon(AppIconName.Crown, contentDescription = null, size = 14.dp, tint = tokens.faint)
                    AppText(
                        stringResource(Res.string.settings_invite_owner_only, ownerName),
                        style = MindstockType.summarySub(),
                        color = tokens.faint,
                    )
                }
            }
        }

        // leave affordance
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
            if (leaveConfirm) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.radiusMd))
                            .background(tokens.statusOutSoft)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppText(stringResource(Res.string.leave_confirm), style = MindstockType.summarySub(), color = tokens.statusOut)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AppButton(
                            onClick = { leaveConfirm = false },
                            variant = ButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(stringResource(Res.string.leave_cancel))
                        }
                        AppButton(
                            onClick = {
                                onLeave()
                                leaveConfirm = false
                            },
                            variant = ButtonVariant.Danger,
                            icon = AppIconName.Trash,
                            modifier = Modifier.weight(1f),
                        ) {
                            AppText(stringResource(Res.string.leave_do))
                        }
                    }
                }
            } else {
                AppButton(
                    onClick = { leaveConfirm = true },
                    variant = ButtonVariant.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(stringResource(Res.string.settings_leave), color = tokens.statusOut)
                }
            }
        }
    }
}

@Composable
private fun MemberRowItem(
    member: MemberRow,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val ownerLike = member.role == HouseholdMemberRole.世帯主
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .topDivider(tokens.lineSoft)
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // mock 準拠: メンバーアバターは利用者別色で塗りつぶし + 白頭文字。
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(99.dp)).background(avatarColorOf(member.name)),
            contentAlignment = Alignment.Center,
        ) {
            AppText(member.name.take(1), style = MindstockType.cardTitle().copy(fontSize = 15.sp), color = tokens.onAccent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppText(
                    member.name,
                    style = MindstockType.summarySub(),
                    color = tokens.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.isMe) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(tokens.surface2)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        AppText(stringResource(Res.string.member_you), style = MindstockType.unitCaption(), color = tokens.sub)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AppIcon(
                    roleIcon(member.role),
                    contentDescription = null,
                    size = 12.dp,
                    tint = if (ownerLike) tokens.accent else tokens.faint,
                )
                AppText(
                    stringResource(roleLabelResource(member.role)),
                    style = MindstockType.unitCaption(),
                    color = if (ownerLike) tokens.accent else tokens.faint,
                )
            }
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 16.dp, tint = tokens.faint)
    }
}

@Composable
private fun MasterEntryCard(onOpenMaster: () -> Unit) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            Modifier
                .cardSurface()
                .clickable { onOpenMaster() }
                .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tokens.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Box, contentDescription = null, size = 20.dp, tint = tokens.accent)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppText(stringResource(Res.string.settings_master_entry), style = MindstockType.summarySub(), color = tokens.ink)
                Row(
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(6.dp),
                            ).background(tokens.accentSoft)
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppIcon(AppIconName.Crown, contentDescription = null, size = 11.dp, tint = tokens.accent)
                    AppText(stringResource(Res.string.settings_owner_badge), style = MindstockType.unitCaption(), color = tokens.accent)
                }
            }
            AppText(stringResource(Res.string.settings_master_entry_sub), style = MindstockType.unitCaption(), color = tokens.faint)
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 17.dp, tint = tokens.faint)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalMindstockTokens.current
    AppText(
        text,
        style = MindstockType.summarySub(),
        color = tokens.faint,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(modifier = Modifier.cardSurface()) {
        content()
    }
}

@Composable
private fun ToggleRow(
    icon: AppIconName,
    label: String,
    sub: String,
    top: Boolean,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (top) Modifier.topDivider(tokens.lineSoft) else Modifier)
                .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tokens.surface2),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 18.dp, tint = tokens.faint)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(label, style = MindstockType.summarySub(), color = tokens.sub)
                FutureBadge(stringResource(Res.string.settings_badge_future))
            }
            AppText(sub, style = MindstockType.unitCaption(), color = tokens.faint)
        }
        Toggle(checked = false, onCheckedChange = {}, enabled = false)
    }
}

@Composable
private fun LinkRow(
    icon: AppIconName,
    label: String,
    soon: Boolean,
    top: Boolean,
    onClick: (() -> Unit)?,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (top) Modifier.topDivider(tokens.lineSoft) else Modifier)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tokens.surface2),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 18.dp, tint = tokens.sub)
        }
        AppText(label, style = MindstockType.summarySub(), color = tokens.ink, modifier = Modifier.weight(1f))
        if (soon) {
            FutureBadge(stringResource(Res.string.settings_badge_soon))
        } else {
            AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 17.dp, tint = tokens.faint)
        }
    }
}

/** 行間の 1px 上区切り線(モックの borderTop 相当)。 */
private fun Modifier.topDivider(color: Color): Modifier =
    drawBehind {
        drawLine(color = color, start = Offset(0f, 0f), end = Offset(size.width, 0f), strokeWidth = 1f)
    }

/** カード共通の表層(影 → 角丸 → 面 → 枠 + 下マージン)。設定画面の 4 カードで共有。 */
@Composable
private fun Modifier.cardSurface(): Modifier {
    val tokens = LocalMindstockTokens.current
    return this
        .fillMaxWidth()
        .padding(bottom = 16.dp)
        .softShadow(ShadowLevel.Sm, RoundedCornerShape(tokens.radiusLg))
        .clip(RoundedCornerShape(tokens.radiusLg))
        .background(tokens.surface)
        .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusLg))
}

@Composable
private fun FutureBadge(text: String) {
    val tokens = LocalMindstockTokens.current
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(tokens.surface2)
                .border(1.dp, tokens.lineSoft, RoundedCornerShape(99.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        AppText(text, style = MindstockType.unitCaption(), color = tokens.faint)
    }
}
