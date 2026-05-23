package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * 商品の最低在庫(これを下回ると買い物リストに載る)。非負整数。
 */
@Serializable
@JvmInline
public value class MinimumStock(
    private val value: Int,
) {
    init {
        if (value < 0) throw DomainException.InvalidMinimumStock(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Int = value
}
