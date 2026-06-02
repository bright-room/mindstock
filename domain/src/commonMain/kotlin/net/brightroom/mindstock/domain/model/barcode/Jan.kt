package net.brightroom.mindstock.domain.model.barcode

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class Jan(
    private val value: String,
) {
    init {
        require(value.length == LENGTH && value.all { it.isDigit() }) {
            "Jan must be $LENGTH digits: '$value'"
        }
        require(hasValidCheckDigit(value)) {
            "Jan has invalid EAN-13 check digit: '$value'"
        }
    }

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val LENGTH = 13

        private fun hasValidCheckDigit(value: String): Boolean {
            val digits = value.map { it - '0' }
            val sum =
                digits
                    .take(LENGTH - 1)
                    .mapIndexed { i, d ->
                        if (i % 2 == 0) d else d * 3
                    }.sum()
            val check = (10 - sum % 10) % 10
            return check == digits[LENGTH - 1]
        }
    }
}
