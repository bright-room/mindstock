package net.brightroom.mindstock.domain.model.user

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * ユーザーの表示名。空文字禁止、最大 100 文字。
 */
@Serializable
@JvmInline
value class DisplayName(
    private val value: String,
) {
    init {
        if (value.isBlank()) throw DomainException.DisplayNameBlank()
        if (value.length > 100) throw DomainException.DisplayNameTooLong(value.length)
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
