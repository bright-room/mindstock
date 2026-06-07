package net.brightroom.mindstock.frontend.feature.household

import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.household.InvitationPreview

data class NeedHouseholdUiState(
    val preview: InvitationPreview? = null,
    val previewError: UiText? = null,
    val busy: Boolean = false,
)
