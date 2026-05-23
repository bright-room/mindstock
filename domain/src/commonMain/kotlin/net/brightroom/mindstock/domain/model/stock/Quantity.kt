package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import net.brightroom.mindstock.domain.exception.DomainException

/**
 * 在庫イベント(補充・消費・訂正)の数量。常に正の整数。
 */
@Serializable
@JvmInline
public value class Quantity(private val value: Int) {
    init {
        if (value <= 0) throw DomainException.InvalidQuantity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Int = value
}
