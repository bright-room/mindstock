package net.brightroom.mindstock.frontend.feature.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.shop_add_action
import mindstock.frontend.generated.resources.shop_add_added
import mindstock.frontend.generated.resources.shop_add_close
import mindstock.frontend.generated.resources.shop_add_empty_all
import mindstock.frontend.generated.resources.shop_add_empty_none
import mindstock.frontend.generated.resources.shop_add_search_placeholder
import mindstock.frontend.generated.resources.shop_add_sheet_title
import mindstock.frontend.generated.resources.shop_stock_qty
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.SearchField
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.inventory.glyphForProductName
import org.jetbrains.compose.resources.stringResource

/** モック `screens-c.jsx:AddToListSheet`(在庫から追加)準拠。検索 + 候補行(サムネ/在庫数/追加ボタン)。 */
@Composable
fun AddToListSheet(
    open: Boolean,
    candidates: List<ShoppingEntry>,
    onClose: () -> Unit,
    onAdd: (ProductId) -> Unit,
) {
    if (!open) return
    val tokens = LocalMindstockTokens.current
    var query by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(emptySet<ProductId>()) }
    val results =
        candidates.filter {
            query.isBlank() ||
                it.stock.product
                    .name()
                    .contains(query, ignoreCase = true)
        }
    Sheet(open = true, title = stringResource(Res.string.shop_add_sheet_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.shop_add_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isEmpty()) {
                AppText(
                    stringResource(
                        if (candidates.isEmpty()) Res.string.shop_add_empty_all else Res.string.shop_add_empty_none,
                    ),
                    style = MindstockType.summarySub(),
                    color = tokens.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = {
                        it.stock.product.id
                            .toString()
                    }) { entry ->
                        CandidateRow(
                            entry = entry,
                            isAdded = entry.stock.product.id in added,
                            onAdd = {
                                onAdd(entry.stock.product.id)
                                added = added + entry.stock.product.id
                            },
                        )
                    }
                }
            }
            AppButton(onClick = onClose, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth()) {
                AppText(stringResource(Res.string.shop_add_close))
            }
        }
    }
}

@Composable
private fun CandidateRow(
    entry: ShoppingEntry,
    isAdded: Boolean,
    onAdd: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val product = entry.stock.product
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMd))
                .background(tokens.surface)
                .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusMd))
                .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(icon = glyphForProductName(product.name()), size = 40.dp, radius = 12.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(
                product.name(),
                style = MindstockType.summarySub().copy(fontSize = 14.sp),
                color = tokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                stringResource(Res.string.shop_stock_qty, entry.stock.currentQuantity()(), product.setting.unit()),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
            )
        }
        // 追加 / 追加済み の小ボタン(mock: height36/radius10・accent↔surface2)
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAdded) tokens.surface2 else tokens.accent)
                    .then(if (isAdded) Modifier else Modifier.clickable(onClick = onAdd))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(
                    if (isAdded) AppIconName.Check else AppIconName.Plus,
                    contentDescription = null,
                    size = 15.dp,
                    tint = if (isAdded) tokens.sub else tokens.onAccent,
                )
                AppText(
                    stringResource(if (isAdded) Res.string.shop_add_added else Res.string.shop_add_action),
                    style = MindstockType.sectionMeta(),
                    color = if (isAdded) tokens.sub else tokens.onAccent,
                )
            }
        }
    }
}
