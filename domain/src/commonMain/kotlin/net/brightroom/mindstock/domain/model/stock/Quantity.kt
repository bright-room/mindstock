package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
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
        require(value > 0) { "quantity must be > 0, got $value" }
    }

    override fun toString(): String = value.toString()

    operator fun invoke(): Int = value
}
