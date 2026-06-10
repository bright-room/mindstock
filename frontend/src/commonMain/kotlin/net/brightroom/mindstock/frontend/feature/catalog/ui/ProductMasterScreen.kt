package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_back
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.master_add
import mindstock.frontend.generated.resources.master_empty_sub
import mindstock.frontend.generated.resources.master_empty_title
import mindstock.frontend.generated.resources.master_hint
import mindstock.frontend.generated.resources.master_row_meta
import mindstock.frontend.generated.resources.master_subtitle
import mindstock.frontend.generated.resources.master_title
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.core.image.LocalProductImageLoader
import net.brightroom.mindstock.frontend.core.image.rememberProductImage
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AddTile
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.EmptyState
import net.brightroom.mindstock.frontend.designsystem.atom.NavIconButton
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.catalog.ProductMasterUiState
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProductMasterScreen(
    state: ProductMasterUiState,
    householdName: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSelect: (Stock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val count = (state as? ProductMasterUiState.Content)?.stocks?.list?.size ?: 0

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.bg),
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
            NavIconButton(
                icon = AppIconName.Back,
                contentDescription = stringResource(Res.string.action_back),
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = stringResource(Res.string.master_title),
                    style = MindstockType.summaryTitle(),
                    color = tokens.ink,
                )
                AppText(
                    text = stringResource(Res.string.master_subtitle, householdName, count),
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
            AddTile(
                label = stringResource(Res.string.master_add),
                onClick = onAdd,
                accent = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AppText(
                text = stringResource(Res.string.master_hint),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
            )

            when (state) {
                is ProductMasterUiState.Loading -> {
                    AppText(
                        text = stringResource(Res.string.loading),
                        style = MindstockType.sectionMeta(),
                        color = tokens.faint,
                    )
                }

                is ProductMasterUiState.Error -> {
                    AppText(
                        text = state.text.resolve(),
                        style = MindstockType.sectionMeta(),
                        color = tokens.sub,
                    )
                }

                is ProductMasterUiState.Content -> {
                    if (state.stocks.list.isEmpty()) {
                        EmptyState(
                            icon = AppIconName.Box,
                            title = stringResource(Res.string.master_empty_title),
                            sub = stringResource(Res.string.master_empty_sub),
                        )
                    } else {
                        state.stocks.list.forEach { stock ->
                            MasterStockRow(
                                stock = stock,
                                onClick = { onSelect(stock) },
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
private fun MasterStockRow(
    stock: Stock,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(tokens.radiusMd)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .softShadow(ShadowLevel.Sm, shape)
                .clip(shape)
                .border(1.dp, tokens.lineSoft, shape)
                .background(tokens.surface)
                .clickable(onClick = onClick)
                .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        val loader = LocalProductImageLoader.current
        val img = rememberProductImage(loader, stock.product.id, stock.product.image is ProductImage.Stored)
        Thumb(icon = glyphForProductName(stock.product.name()), size = 46.dp, image = img)
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stock.product.name(),
                style = MindstockType.cardTitle().copy(fontSize = 14.5f.sp),
                color = tokens.ink,
                maxLines = 1,
            )
            AppText(
                text =
                    stringResource(
                        Res.string.master_row_meta,
                        stock.product.setting.unit(),
                        stock.currentQuantity()(),
                        stock.product.setting.unit(),
                    ),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        AppIcon(
            name = AppIconName.Pencil,
            contentDescription = null,
            size = 17.dp,
            tint = tokens.faint,
        )
    }
}
