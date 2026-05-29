package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 商品の最低在庫(これを下回ると買い物リストに載る)。非負整数。
 */
@Serializable
@JvmInline
value class MinimumStock(
    private val value: Int,
) {
    init {
        require(value >= 0) { "minimum stock must be >= 0, got $value" }
    }

    override fun toString(): String = value.toString()

    operator fun invoke(): Int = value
}
