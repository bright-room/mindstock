package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.date_day_before
import mindstock.frontend.generated.resources.date_picker_cancel
import mindstock.frontend.generated.resources.date_picker_ok
import mindstock.frontend.generated.resources.date_picker_open_calendar
import mindstock.frontend.generated.resources.date_today
import mindstock.frontend.generated.resources.date_yesterday
import mindstock.frontend.generated.resources.move_when_label
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * 出来事の日付を選ぶ。3 チップ(今日/昨日/おととい)+ カレンダーボタンで過去日を選択。
 * モック screens-b.jsx の `DatePick`: チップ height42/radius12/`600 13.5px`、カレンダーボタン 46x42。
 * 選択結果は [LocalDate](時刻成分は持たない。OccurredAt 合成は呼び出し側で)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    var dialogOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        AppText(
            text = stringResource(Res.string.move_when_label),
            style = MindstockType.statusLabel().copy(fontSize = 12.5.sp),
            color = tokens.faint,
            modifier = Modifier.padding(bottom = 9.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips =
                listOf(
                    today to stringResource(Res.string.date_today),
                    today.minus(DatePeriod(days = 1)) to stringResource(Res.string.date_yesterday),
                    today.minus(DatePeriod(days = 2)) to stringResource(Res.string.date_day_before),
                )
            chips.forEach { (date, label) ->
                DateChip(
                    label = label,
                    active = date == selected,
                    onClick = { onSelect(date) },
                    modifier = Modifier.weight(1f),
                )
            }
            CalendarButton(
                contentDescription = stringResource(Res.string.date_picker_open_calendar),
                onClick = { dialogOpen = true },
            )
        }
    }

    if (dialogOpen) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = selected.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val d = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date
                            return d <= today
                        }
                    },
            )
        DatePickerDialog(
            onDismissRequest = { dialogOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onSelect(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                    dialogOpen = false
                }) { AppText(stringResource(Res.string.date_picker_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogOpen = false }) { AppText(stringResource(Res.string.date_picker_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun DateChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            modifier
                .height(42.dp)
                .clip(shape)
                .background(if (active) tokens.accentSoft else tokens.surface)
                .border(1.dp, if (active) tokens.accent else tokens.line, shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label,
            style = MindstockType.statusLabel().copy(fontSize = 13.5.sp),
            color = if (active) tokens.accent else tokens.sub,
        )
    }
}

@Composable
private fun CalendarButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            Modifier
                .width(46.dp)
                .height(42.dp)
                .clip(shape)
                .background(tokens.surface)
                .border(1.dp, tokens.line, shape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIconName.Calendar, contentDescription = contentDescription, size = 19.dp, tint = tokens.sub)
    }
}
