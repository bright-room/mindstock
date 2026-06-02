package net.brightroom.mindstock.infrastructure.datasource

import kotlinx.datetime.LocalDateTime
import net.brightroom.mindstock.extensions.kotlinx.datetime.now

@JvmInline
value class Created(
    private val value: LocalDateTime,
) {
    operator fun invoke(): LocalDateTime = value

    override fun toString() = value.toString()

    companion object {
        fun now(): Created = Created(LocalDateTime.now())
    }
}
