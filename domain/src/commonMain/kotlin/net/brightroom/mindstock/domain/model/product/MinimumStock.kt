package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable

@Serializable
sealed interface MinimumStock {
    fun isBelow(quantity: Int): Boolean

    fun shortage(quantity: Int): Int

    @Serializable
    data object NotSet : MinimumStock {
        override fun isBelow(quantity: Int): Boolean = false

        override fun shortage(quantity: Int): Int = 0
    }

    @Serializable
    data class Set(
        private val value: Int,
    ) : MinimumStock {
        init {
            require(value >= 0) { "minimum_stock must be >= 0, got $value" }
        }

        override fun isBelow(quantity: Int): Boolean = quantity < value

        override fun shortage(quantity: Int): Int = (value - quantity).coerceAtLeast(0)

        operator fun invoke(): Int = value
    }
}
