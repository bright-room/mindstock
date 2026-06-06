package net.brightroom.mindstock.frontend.feature.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.shop_add_action
import mindstock.frontend.generated.resources.shop_add_empty_all
import mindstock.frontend.generated.resources.shop_add_search_placeholder
import mindstock.frontend.generated.resources.shop_add_sheet_title
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingEntry
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddToListSheet(
    open: Boolean,
    candidates: List<ShoppingEntry>,
    onClose: () -> Unit,
    onAdd: (ProductId) -> Unit,
) {
    if (!open) return
    var query by remember { mutableStateOf("") }
    val results =
        candidates.filter {
            query.isBlank() ||
                it.stock.product
                    .name()
                    .contains(query, ignoreCase = true)
        }
    Sheet(open = true, title = stringResource(Res.string.shop_add_sheet_title), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(Res.string.shop_add_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isEmpty()) {
                AppText(stringResource(Res.string.shop_add_empty_all))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { entry ->
                        PrimaryButton(onClick = { onAdd(entry.stock.product.id) }) {
                            AppText(entry.stock.product.name() + " · " + stringResource(Res.string.shop_add_action))
                        }
                    }
                }
            }
        }
    }
}
