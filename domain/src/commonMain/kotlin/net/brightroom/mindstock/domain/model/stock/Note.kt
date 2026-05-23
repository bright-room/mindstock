package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 在庫イベントに付与する自由記述。空文字許容。
 */
@Serializable
@JvmInline
public value class Note(private val value: String) {
    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
