package net.brightroom.mindstock.frontend.feature.activity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import mindstock.frontend.generated.resources.history_replenish
import mindstock.frontend.generated.resources.loading
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement
import net.brightroom.mindstock.extensions.kotlinx.datetime.JST
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.PrimaryButton
import net.brightroom.mindstock.frontend.feature.activity.ActivityUiState
import net.brightroom.mindstock.frontend.feature.activity.DayLabel
import net.brightroom.mindstock.frontend.feature.activity.groupedByDay
import net.brightroom.mindstock.frontend.feature.activity.hm
import net.brightroom.mindstock.rpc.stock.ActivityFeed
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onOpenProduct: (ProductId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppText(stringResource(Res.string.activity_subtitle))
        AppText(stringResource(Res.string.activity_title))
        when (state) {
            is ActivityUiState.Loading -> {
                AppText(stringResource(Res.string.loading))
            }

            is ActivityUiState.Error -> {
                AppText(state.text.resolve())
            }

            is ActivityUiState.Content -> {
                val today = LocalDate.now(TimeZone.JST)
                // 活動行は補充/消費のみ。訂正は商品履歴側で扱うため除外する。
                val visible = ActivityFeed(state.feed.list.filter { it.movement !is StockMovement.Correction })
                val groups = visible.groupedByDay(today)
                if (groups.isEmpty()) {
                    AppText(stringResource(Res.string.activity_empty_title))
                    AppText(stringResource(Res.string.activity_empty_sub))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groups.forEach { group ->
                            item { AppText(dayLabelText(group.label)) }
                            items(group.entries) { entry ->
                                val m = entry.movement
                                val verb =
                                    when (m) {
                                        is StockMovement.Replenishment -> stringResource(Res.string.history_replenish)
                                        is StockMovement.Consumption -> stringResource(Res.string.history_consume)
                                        is StockMovement.Correction -> stringResource(Res.string.history_replenish) // 除外済みのため到達しない
                                    }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PrimaryButton(onClick = { onOpenProduct(entry.product.id) }) {
                                        AppText(entry.product.name())
                                    }
                                    AppText(
                                        stringResource(
                                            Res.string.activity_row_summary,
                                            verb,
                                            m.quantity(),
                                            entry.product.setting.unit(),
                                            m.actor.profile.displayName(),
                                        ),
                                    )
                                    AppText(hm(m.occurredAt()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabelText(label: DayLabel): String =
    when (label) {
        is DayLabel.Resource -> stringResource(label.resource)
        is DayLabel.NDaysAgo -> stringResource(label.resource, label.days)
        is DayLabel.Date -> label.iso
    }
