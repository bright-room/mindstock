package net.brightroom.mindstock.frontend.feature.activity.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.activity_empty_sub
import mindstock.frontend.generated.resources.activity_empty_title
import mindstock.frontend.generated.resources.activity_row_summary
import mindstock.frontend.generated.resources.activity_subtitle
import mindstock.frontend.generated.resources.activity_title
import mindstock.frontend.generated.resources.history_consume
import mindstock.frontend.generated.resources.history_corrected_badge
import mindstock.frontend.generated.resources.history_replenish
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementIdentity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.extensions.kotlinx.datetime.JST
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.EmptyState
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.designsystem.theme.ShadowLevel
import net.brightroom.mindstock.frontend.designsystem.theme.softShadow
import net.brightroom.mindstock.frontend.feature.activity.ActivityUiState
import net.brightroom.mindstock.frontend.feature.activity.DayLabel
import net.brightroom.mindstock.frontend.feature.activity.groupedByDay
import net.brightroom.mindstock.frontend.feature.activity.hm
import net.brightroom.mindstock.rpc.stock.ActivityEntry
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onOpenProduct: (ProductId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppText(stringResource(Res.string.activity_subtitle), style = MindstockType.greeting(), color = tokens.sub)
                AppText(stringResource(Res.string.activity_title), style = MindstockType.screenTitle(), color = tokens.ink)
            }
        }

        when (state) {
            is ActivityUiState.Loading -> {
                item { AppText(stringResource(Res.string.loading), color = tokens.sub) }
            }

            is ActivityUiState.Error -> {
                item { AppText(state.text.resolve(), color = tokens.statusOut) }
            }

            is ActivityUiState.Content -> {
                val today = LocalDate.now(TimeZone.JST)
                val correctedIds: Set<MovementId> =
                    state.feed.list
                        .map { it.movement }
                        .filterIsInstance<StockMovement.Correction>()
                        .map { it.target }
                        .toSet()
                // 活動行は補充/消費のみ。訂正は商品履歴側で扱うため除外する。
                val visible = ActivityFeed(state.feed.list.filter { it.movement !is StockMovement.Correction })
                val groups = visible.groupedByDay(today)
                if (groups.isEmpty()) {
                    item {
                        EmptyState(
                            icon = AppIconName.Clock,
                            title = stringResource(Res.string.activity_empty_title),
                            sub = stringResource(Res.string.activity_empty_sub),
                        )
                    }
                } else {
                    groups.forEach { group ->
                        item {
                            AppText(
                                dayLabelText(group.label),
                                style = MindstockType.statusLabel(),
                                color = tokens.faint,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        item { DayCard(group.entries, correctedIds, onOpenProduct) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DayCard(
    entries: List<ActivityEntry>,
    correctedIds: Set<MovementId>,
    onOpenProduct: (ProductId) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .softShadow(ShadowLevel.Sm, shape)
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.lineSoft, shape),
    ) {
        entries.forEachIndexed { i, entry ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
            val corrected = (entry.movement.identity as? MovementIdentity.Persisted)?.id in correctedIds
            ActivityRow(entry, corrected, onOpenProduct)
        }
    }
}

@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    corrected: Boolean,
    onOpenProduct: (ProductId) -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val m = entry.movement
    val isReplenish = m is StockMovement.Replenishment
    val verb = if (isReplenish) stringResource(Res.string.history_replenish) else stringResource(Res.string.history_consume)
    val summary =
        stringResource(Res.string.activity_row_summary, verb, m.quantity(), entry.product.setting.unit(), m.actor.profile.displayName())
    val meta = if (corrected) summary + " · " + stringResource(Res.string.history_corrected_badge) else summary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenProduct(entry.product.id) }
                .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isReplenish) tokens.accentSoft else tokens.surface2)
                    .border(1.dp, tokens.lineSoft, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                if (isReplenish) AppIconName.Plus else AppIconName.Minus,
                contentDescription = null,
                tint = if (isReplenish) tokens.accent else tokens.sub,
                size = 17.dp,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppText(
                entry.product.name(),
                style = MindstockType.cardTitle(),
                color = tokens.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(meta, style = MindstockType.summarySub(), color = tokens.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AppText(hm(m.occurredAt()), style = MindstockType.summarySub(), color = tokens.faint)
    }
}

@Composable
private fun dayLabelText(label: DayLabel): String =
    when (label) {
        is DayLabel.Resource -> stringResource(label.resource)
        is DayLabel.NDaysAgo -> stringResource(label.resource, label.days)
        is DayLabel.Date -> label.iso
    }
