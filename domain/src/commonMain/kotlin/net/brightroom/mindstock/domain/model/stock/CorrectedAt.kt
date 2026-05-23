package net.brightroom.mindstock.domain.model.stock

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * 訂正日時を表す Value Object。
 *
 * DB の `created_at` カラムを「訂正がいつ行われたか」という domain 概念として
 * 読み替えて使う。集約ルートの createdAt(インフラメタ)とは扱いが異なる。
 */
@Serializable
@JvmInline
value class CorrectedAt(private val value: Instant) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Instant = value
}
