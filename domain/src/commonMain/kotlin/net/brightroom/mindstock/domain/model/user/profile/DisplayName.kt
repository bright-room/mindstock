package net.brightroom.mindstock.domain.model.user.profile

import kotlinx.serialization.Serializable
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
        require(value.isNotBlank()) { "display name must not be blank" }
        require(value.length <= 100) { "display name length ${value.length} > 100" }
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
