package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.add_product_min_caption
import mindstock.frontend.generated.resources.add_product_min_label
import mindstock.frontend.generated.resources.add_product_unit_label
import mindstock.frontend.generated.resources.settings_archive
import mindstock.frontend.generated.resources.settings_archive_blocked
import mindstock.frontend.generated.resources.settings_archive_note
import mindstock.frontend.generated.resources.settings_master_edit_title
import mindstock.frontend.generated.resources.settings_name_immutable
import mindstock.frontend.generated.resources.settings_save
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonVariant
import net.brightroom.mindstock.frontend.designsystem.atom.MiniStepper
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.Sheet
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource

/** 商品の単位/最低在庫/アーカイブを編集するモーダルボトムシート。 */
@Composable
fun ProductSettingsSheet(
    open: Boolean,
    stock: Stock?,
    onClose: () -> Unit,
    onChangeUnit: (ProductUnit) -> Unit,
    onChangeMinimum: (MinimumStock) -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stock == null) return

    var unit by remember(stock.product.name()) { mutableStateOf(stock.product.setting.unit()) }
    var min by remember(stock.product.name()) { mutableStateOf(stock.product.setting.minimumStock()) }

    val changed = unit.trim() != stock.product.setting.unit() || min != stock.product.setting.minimumStock()

    Sheet(open = open, title = stringResource(Res.string.settings_master_edit_title), onClose = onClose) {
        val tokens = LocalMindstockTokens.current

        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Product name + immutable caption
            Column {
                AppText(
                    text = stock.product.name(),
                    style = MindstockType.summaryTitle(),
                    color = tokens.ink,
                )
                AppText(
                    text = stringResource(Res.string.settings_name_immutable),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 2. Unit section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppText(
                    text = stringResource(Res.string.add_product_unit_label),
                    style = MindstockType.sectionMeta(),
                    color = tokens.faint,
                    modifier = Modifier.padding(start = 2.dp),
                )
                UnitPicker(value = unit, onChange = { unit = it }, modifier = Modifier.fillMaxWidth())
            }

            // 3. Min stock section（モック: 上下ボーダー・背景なし）
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = stringResource(Res.string.add_product_min_label),
                            style = MindstockType.cardTitle(),
                            color = tokens.ink,
                        )
                        AppText(
                            text = stringResource(Res.string.add_product_min_caption),
                            style = MindstockType.unitCaption(),
                            color = tokens.faint,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    MiniStepper(value = min, onChange = { min = it }, min = 0)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
            }

            // 4. Save button
            PrimaryButton(
                onClick = {
                    if (unit.trim() != stock.product.setting.unit()) onChangeUnit(ProductUnit(unit.trim()))
                    if (min != stock.product.setting.minimumStock()) onChangeMinimum(MinimumStock(min))
                    onClose()
                },
                enabled = changed && unit.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppText(stringResource(Res.string.settings_save))
            }

            // 5. Archive area
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val qty = stock.currentQuantity()()
                if (qty > 0) {
                    // Blocked: archive disabled
                    AppButton(
                        onClick = {},
                        variant = ButtonVariant.Quiet,
                        icon = AppIconName.Archive,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(Res.string.settings_archive))
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.radiusMd))
                                .background(tokens.surface)
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AppIcon(
                            AppIconName.Box,
                            contentDescription = null,
                            size = 16.dp,
                            tint = tokens.faint,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        AppText(
                            text =
                                stringResource(
                                    Res.string.settings_archive_blocked,
                                    qty,
                                    stock.product.setting.unit(),
                                ),
                            style = MindstockType.unitCaption(),
                            color = tokens.sub,
                        )
                    }
                } else {
                    // Enabled: archive available
                    AppButton(
                        onClick = {
                            onArchive()
                            onClose()
                        },
                        variant = ButtonVariant.Quiet,
                        icon = AppIconName.Archive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(Res.string.settings_archive))
                    }
                    AppText(
                        text = stringResource(Res.string.settings_archive_note),
                        style = MindstockType.unitCaption(),
                        color = tokens.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
