package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProductUnit private constructor(
    private val value: String,
) {
    init {
        require(value.isNotEmpty() && value.length <= MAX_LENGTH && value == value.trim()) {
            "ProductUnit must be 1..$MAX_LENGTH chars after trim"
        }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 10

        operator fun invoke(raw: String): ProductUnit = ProductUnit(raw.trim())
    }
}
