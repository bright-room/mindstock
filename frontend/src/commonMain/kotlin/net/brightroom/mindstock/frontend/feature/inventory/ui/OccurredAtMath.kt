package net.brightroom.mindstock.frontend.feature.inventory.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt

/** 選択された日付に現在時刻(時・分・秒)を合わせて OccurredAt を作る。モックの「今日=now / 昨日=now-1d」を一般化したもの。 */
fun occurredAtOf(
    date: LocalDate,
    now: LocalDateTime,
): OccurredAt = OccurredAt(LocalDateTime(date, now.time))
