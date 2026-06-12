package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class SearchLimit(
    private val value: Int,
) {
    init {
        require(value in 1..MAX) { "SearchLimit must be in 1..$MAX: $value" }
    }

    operator fun invoke(): Int = value

    override fun toString(): String = value.toString()

    companion object {
        const val MAX = 100
    }
}
