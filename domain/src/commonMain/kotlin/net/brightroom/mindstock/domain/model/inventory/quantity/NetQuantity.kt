package net.brightroom.mindstock.domain.model.inventory.quantity

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 全 movement を畳み込んだ正味在庫数量。補充の加算・消費の減算・訂正の上書きの結果で、
 * 0 や(訂正途中の不整合では)負にもなり得るため、正の数しか持てない [Quantity] とは別の VO。
 */
@Serializable
@JvmInline
value class NetQuantity(
    private val value: Int,
) {
    operator fun invoke(): Int = value

    override fun toString(): String = value.toString()
}
