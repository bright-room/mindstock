package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_back
import mindstock.frontend.generated.resources.archived_empty_sub
import mindstock.frontend.generated.resources.archived_empty_title
import mindstock.frontend.generated.resources.archived_hint_owner
import mindstock.frontend.generated.resources.archived_hint_viewer
import mindstock.frontend.generated.resources.archived_restore
import mindstock.frontend.generated.resources.archived_row_meta
import mindstock.frontend.generated.resources.archived_subtitle
import mindstock.frontend.generated.resources.archived_title
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.EmptyState
import net.brightroom.mindstock.frontend.designsystem.atom.RoundBtn
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.catalog.ArchivedUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArchivedScreen(
    state: ArchivedUiState,
    householdName: String,
    canRestore: Boolean,
    onBack: () -> Unit,
    onRestore: (ProductId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val count = (state as? ArchivedUiState.Content)?.products?.list?.size ?: 0

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.surface),
    ) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundBtn(
                icon = AppIconName.Back,
                contentDescription = stringResource(Res.string.action_back),
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = stringResource(Res.string.archived_title),
                    style = MindstockType.summaryTitle(),
                    color = tokens.ink,
                )
                AppText(
                    text = stringResource(Res.string.archived_subtitle, householdName, count),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                )
            }
        }

        // Scrollable body
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AppText(
                text =
                    if (canRestore) {
                        stringResource(Res.string.archived_hint_owner)
                    } else {
                        stringResource(Res.string.archived_hint_viewer)
                    },
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
            )

            when (state) {
                is ArchivedUiState.Loading -> {
                    AppText(
                        text = stringResource(Res.string.loading),
                        style = MindstockType.sectionMeta(),
                        color = tokens.faint,
                    )
                }

                is ArchivedUiState.Error -> {
                    AppText(
                        text = state.text.resolve(),
                        style = MindstockType.sectionMeta(),
                        color = tokens.sub,
                    )
                }

                is ArchivedUiState.Content -> {
                    if (state.products.list.isEmpty()) {
                        EmptyState(
                            icon = AppIconName.Archive,
                            title = stringResource(Res.string.archived_empty_title),
                            sub = stringResource(Res.string.archived_empty_sub),
                        )
                    } else {
                        state.products.list.forEach { product ->
                            ArchivedProductRow(
                                product = product,
                                canRestore = canRestore,
                                onRestore = { onRestore(product.id) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ArchivedProductRow(
    product: Product,
    canRestore: Boolean,
    onRestore: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(tokens.radiusMd)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, tokens.lineSoft, shape)
                .background(tokens.surface)
                .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Thumb(size = 46.dp)
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = product.name(),
                style = MindstockType.cardTitle(),
                color = tokens.ink,
                maxLines = 1,
            )
            AppText(
                text =
                    stringResource(
                        Res.string.archived_row_meta,
                        product.setting.unit(),
                        product.setting.minimumStock(),
                        product.setting.unit(),
                    ),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        if (canRestore) {
            AppButton(
                onClick = onRestore,
                variant = ButtonVariant.Soft,
                size = ButtonSize.Sm,
                icon = AppIconName.Restore,
            ) {
                AppText(stringResource(Res.string.archived_restore))
            }
        }
    }
}
