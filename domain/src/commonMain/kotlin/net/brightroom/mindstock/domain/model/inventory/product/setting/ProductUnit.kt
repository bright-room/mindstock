package net.brightroom.mindstock.domain.model.inventory.product.setting

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.support.requireTrimmedWithin
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProductUnit private constructor(
    private val value: String,
) {
    init {
        value.requireTrimmedWithin(MAX_LENGTH, "ProductUnit")
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 10

        operator fun invoke(raw: String): ProductUnit = ProductUnit(raw.trim())
    }
}
