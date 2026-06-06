package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.action_back
import mindstock.frontend.generated.resources.add_product_adopt_title
import mindstock.frontend.generated.resources.add_product_adopted_badge
import mindstock.frontend.generated.resources.add_product_custom_add
import mindstock.frontend.generated.resources.add_product_custom_title
import mindstock.frontend.generated.resources.add_product_image_note
import mindstock.frontend.generated.resources.add_product_jan_lookup
import mindstock.frontend.generated.resources.add_product_loading_jan
import mindstock.frontend.generated.resources.add_product_min_caption
import mindstock.frontend.generated.resources.add_product_min_label
import mindstock.frontend.generated.resources.add_product_name_editable
import mindstock.frontend.generated.resources.add_product_name_label
import mindstock.frontend.generated.resources.add_product_name_locked_jan
import mindstock.frontend.generated.resources.add_product_name_locked_master
import mindstock.frontend.generated.resources.add_product_name_placeholder
import mindstock.frontend.generated.resources.add_product_search_hint
import mindstock.frontend.generated.resources.add_product_search_placeholder
import mindstock.frontend.generated.resources.add_product_submit_adopt
import mindstock.frontend.generated.resources.add_product_submit_custom
import mindstock.frontend.generated.resources.add_product_title
import mindstock.frontend.generated.resources.add_product_unit_label
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.designsystem.atom.AddTile
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.designsystem.atom.RoundBtn
import net.brightroom.mindstock.frontend.designsystem.atom.SearchField
import net.brightroom.mindstock.frontend.designsystem.atom.Stepper
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.atom.Thumb
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.catalog.AddProductUiState
import net.brightroom.mindstock.frontend.feature.catalog.BrowsePhase
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddProductScreen(
    state: AddProductUiState,
    onQuery: (String) -> Unit,
    onLookupJan: (Jan) -> Unit,
    onPickCatalog: (CatalogItem) -> Unit,
    onPickCustom: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onAdopt: (CatalogItem, ProductUnit, MinimumStock) -> Unit,
    onAddCustom: (ProductName, Jan?, ProductUnit, MinimumStock) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is AddProductUiState.Done) return

    val tokens = LocalMindstockTokens.current

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(tokens.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                val title =
                    when (state) {
                        is AddProductUiState.Browsing -> stringResource(Res.string.add_product_title)
                        is AddProductUiState.AdoptForm -> stringResource(Res.string.add_product_adopt_title)
                        is AddProductUiState.CustomForm -> stringResource(Res.string.add_product_custom_title)
                        is AddProductUiState.Done -> ""
                    }
                AppText(title, style = MindstockType.summaryTitle(), color = tokens.ink)
            }

            when (state) {
                is AddProductUiState.Browsing -> {
                    BrowsingContent(
                        state = state,
                        onQuery = onQuery,
                        onLookupJan = onLookupJan,
                        onPickCatalog = onPickCatalog,
                        onPickCustom = onPickCustom,
                    )
                }

                is AddProductUiState.AdoptForm -> {
                    AdoptFormContent(
                        state = state,
                        onAdopt = onAdopt,
                    )
                }

                is AddProductUiState.CustomForm -> {
                    CustomFormContent(
                        state = state,
                        onAddCustom = onAddCustom,
                    )
                }

                is AddProductUiState.Done -> {
                    Unit
                }
            }
        }

        // JAN loading overlay
        if (state is AddProductUiState.Browsing && state.phase == BrowsePhase.JanLookingUp) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(tokens.ink.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(tokens.radiusLg))
                            .background(tokens.surface)
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppText(
                        text = stringResource(Res.string.add_product_loading_jan),
                        style = MindstockType.sectionMeta(),
                        color = tokens.sub,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowsingContent(
    state: AddProductUiState.Browsing,
    onQuery: (String) -> Unit,
    onLookupJan: (Jan) -> Unit,
    onPickCatalog: (CatalogItem) -> Unit,
    onPickCustom: (String) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    var query by remember { mutableStateOf("") }

    val digits = query.filter { it.isDigit() }
    val jan = runCatching { Jan(digits) }.getOrNull()
    val isJanQuery = jan != null && digits == query.trim()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchField(
            value = query,
            onValueChange = { newValue ->
                query = newValue
                val d = newValue.filter { it.isDigit() }
                val j = runCatching { Jan(d) }.getOrNull()
                val isJan = j != null && d == newValue.trim()
                if (!isJan) onQuery(newValue)
            },
            placeholder = stringResource(Res.string.add_product_search_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )

        AppText(
            text = stringResource(Res.string.add_product_search_hint),
            style = MindstockType.unitCaption(),
            color = tokens.faint,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // JAN lookup button — jan is guaranteed non-null when isJanQuery is true
        val resolvedJan = jan
        if (isJanQuery && resolvedJan != null) {
            JanLookupRow(
                digits = digits,
                onClick = { onLookupJan(resolvedJan) },
            )
        }

        // Result items
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            state.results.list.forEach { item ->
                CatalogItemRow(
                    item = item,
                    onClick = { onPickCatalog(item) },
                )
            }

            // Custom add row when query is non-blank and not a JAN
            if (query.trim().isNotEmpty() && !isJanQuery) {
                Spacer(Modifier.height(4.dp))
                AddTile(
                    label = stringResource(Res.string.add_product_custom_add, query.trim()),
                    onClick = { onPickCustom(query.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun JanLookupRow(
    digits: String,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusLg))
                .border(1.dp, tokens.accent, RoundedCornerShape(tokens.radiusLg))
                .background(tokens.surface)
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Barcode, contentDescription = null, size = 22.dp, tint = tokens.accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = stringResource(Res.string.add_product_jan_lookup, digits),
                style = MindstockType.cardTitle(),
                color = tokens.ink,
            )
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 18.dp, tint = tokens.faint)
    }
}

@Composable
private fun CatalogItemRow(
    item: CatalogItem,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMd))
                .border(1.dp, tokens.lineSoft, RoundedCornerShape(tokens.radiusMd))
                .background(tokens.surface)
                .clickable(onClick = onClick)
                .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Thumb(size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = item.name(),
                style = MindstockType.cardTitle(),
                color = tokens.ink,
                maxLines = 1,
            )
            AppText(
                text = "JAN ${item.jan()}",
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null, size = 17.dp, tint = tokens.faint)
    }
}

@Composable
private fun AdoptFormContent(
    state: AddProductUiState.AdoptForm,
    onAdopt: (CatalogItem, ProductUnit, MinimumStock) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val item = state.item
    var unit by remember(item.id) { mutableStateOf("個") }
    var min by remember(item.id) { mutableStateOf(1) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Item summary card
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusLg))
                    .background(tokens.surface2)
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Thumb(size = 52.dp)
            Column {
                AppText(
                    text = item.name(),
                    style = MindstockType.summaryTitle(),
                    color = tokens.ink,
                )
                AppText(
                    text = stringResource(Res.string.add_product_name_locked_master),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }

        // Name section (locked)
        SectionLabel(stringResource(Res.string.add_product_name_label))
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.radiusMd))
                        .border(1.dp, tokens.line, RoundedCornerShape(tokens.radiusMd))
                        .background(tokens.surface2)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                AppText(
                    text = item.name(),
                    style = MindstockType.button(),
                    color = tokens.ink,
                    modifier = Modifier.weight(1f),
                )
            }
            AppText(
                text = stringResource(Res.string.add_product_name_locked_master),
                style = MindstockType.unitCaption(),
                color = tokens.faint,
                modifier = Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
            )
        }

        // Unit section
        SectionLabel(stringResource(Res.string.add_product_unit_label))
        UnitPicker(value = unit, onChange = { unit = it }, modifier = Modifier.fillMaxWidth())

        // Min stock section
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = tokens.lineSoft,
                        shape = RoundedCornerShape(tokens.radiusMd),
                    ).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
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
            Stepper(value = min, onChange = { min = it }, unit = "", min = 0)
        }

        // Submit
        PrimaryButton(
            onClick = {
                if (unit.trim().isNotBlank()) {
                    onAdopt(item, ProductUnit(unit.trim()), MinimumStock(min))
                }
            },
            enabled = unit.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppText(stringResource(Res.string.add_product_submit_adopt))
        }

        // Footer note
        AppText(
            text = stringResource(Res.string.add_product_image_note),
            style = MindstockType.unitCaption(),
            color = tokens.faint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CustomFormContent(
    state: AddProductUiState.CustomForm,
    onAddCustom: (ProductName, Jan?, ProductUnit, MinimumStock) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val seedName = state.seedName
    val nameLocked = state.nameLocked
    var name by remember(seedName) { mutableStateOf(seedName) }
    var unit by remember(seedName) { mutableStateOf("個") }
    var min by remember(seedName) { mutableStateOf(1) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Summary card
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusLg))
                    .background(tokens.surface2)
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Thumb(size = 52.dp)
            Column {
                AppText(
                    text = name.ifBlank { "新しい商品" },
                    style = MindstockType.summaryTitle(),
                    color = tokens.ink,
                )
                AppText(
                    text =
                        if (nameLocked) {
                            stringResource(Res.string.add_product_name_locked_jan)
                        } else {
                            stringResource(Res.string.add_product_name_editable)
                        },
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }

        // Name section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionLabel(stringResource(Res.string.add_product_name_label))
            if (!nameLocked) {
                AppIcon(AppIconName.Pencil, contentDescription = null, size = 13.dp, tint = tokens.faint)
            }
        }
        if (nameLocked) {
            Column {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.radiusMd))
                            .border(1.dp, tokens.line, RoundedCornerShape(tokens.radiusMd))
                            .background(tokens.surface2)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    AppText(
                        text = name,
                        style = MindstockType.button(),
                        color = tokens.ink,
                        modifier = Modifier.weight(1f),
                    )
                }
                AppText(
                    text = stringResource(Res.string.add_product_name_locked_jan),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    modifier = Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
                )
            }
        } else {
            Column {
                TextInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(Res.string.add_product_name_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                AppText(
                    text = stringResource(Res.string.add_product_name_editable),
                    style = MindstockType.unitCaption(),
                    color = tokens.faint,
                    modifier = Modifier.padding(top = 9.dp, start = 2.dp, end = 2.dp),
                )
            }
        }

        // JAN display (optional, read-only)
        if (state.jan != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.radiusMd))
                        .border(1.dp, tokens.line, RoundedCornerShape(tokens.radiusMd))
                        .background(tokens.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(AppIconName.Barcode, contentDescription = null, size = 20.dp, tint = tokens.accent)
                AppText(
                    text = state.jan(),
                    style = MindstockType.sectionMeta(),
                    color = tokens.ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Unit section
        SectionLabel(stringResource(Res.string.add_product_unit_label))
        UnitPicker(value = unit, onChange = { unit = it }, modifier = Modifier.fillMaxWidth())

        // Min stock section
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = tokens.lineSoft,
                        shape = RoundedCornerShape(tokens.radiusMd),
                    ).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
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
            Stepper(value = min, onChange = { min = it }, unit = "", min = 0)
        }

        // Submit
        val submitEnabled = unit.trim().isNotBlank() && name.trim().isNotEmpty()
        PrimaryButton(
            onClick = {
                if (submitEnabled) {
                    onAddCustom(
                        ProductName(name.trim()),
                        state.jan,
                        ProductUnit(unit.trim()),
                        MinimumStock(min),
                    )
                }
            },
            enabled = submitEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppText(stringResource(Res.string.add_product_submit_custom))
        }

        // Footer note
        AppText(
            text = stringResource(Res.string.add_product_image_note),
            style = MindstockType.unitCaption(),
            color = tokens.faint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalMindstockTokens.current
    AppText(
        text = text,
        style = MindstockType.sectionMeta(),
        color = tokens.faint,
        modifier = Modifier.padding(start = 2.dp),
    )
}
