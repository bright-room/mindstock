package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import net.brightroom.mindstock.extensions.kotlinx.datetime.now
import kotlin.jvm.JvmInline

/**
 * 評価基準時刻。消費予測などの「いつ時点で評価したか」を表す(`OccurredAt` = 出来事の発生時刻 とは別概念)。
 * frontend は [now]、テストは固定値を渡す。
 */
@Serializable
@JvmInline
value class EvaluatedTime(
    private val value: LocalDateTime,
) {
    operator fun invoke(): LocalDateTime = value

    override fun toString(): String = value.toString()

    companion object {
        fun now(): EvaluatedTime = EvaluatedTime(LocalDateTime.now())
    }
}
