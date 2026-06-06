package net.brightroom.mindstock.frontend.feature.activity

import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.stock.ActivityFeed

sealed interface ActivityUiState {
    data object Loading : ActivityUiState

    data class Content(
        val feed: ActivityFeed,
    ) : ActivityUiState

    data class Error(
        val text: UiText,
    ) : ActivityUiState
}
