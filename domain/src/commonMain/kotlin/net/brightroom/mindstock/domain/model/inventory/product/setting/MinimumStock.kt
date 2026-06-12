package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.quantity.NetQuantity
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MinimumStock(
    private val value: Int,
) {
    init {
        require(value >= 0) { "MinimumStock must be >= 0: $value" }
    }

    fun isBelow(current: NetQuantity): Boolean = current() <= value

    fun shortage(current: NetQuantity): Int = (value - current()).coerceAtLeast(0)

    operator fun invoke(): Int = value

    override fun toString(): String = value.toString()
}
