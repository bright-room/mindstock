package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * 在庫 movement(補充・消費)の数量。常に正の整数。
 */
@Serializable
@JvmInline
value class Quantity(
    private val value: Int,
) {
    init {
        if (value <= 0) throw DomainException.InvalidQuantity(value)
    }

    override fun toString(): String = value.toString()

    operator fun invoke(): Int = value
}
