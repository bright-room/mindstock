package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

/**
 * 訂正イベントに付与する理由文。空文字許容(必須化は UseCase/UI で運用)。
 */
@Serializable
@JvmInline
public value class Reason(private val value: String) {
    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
