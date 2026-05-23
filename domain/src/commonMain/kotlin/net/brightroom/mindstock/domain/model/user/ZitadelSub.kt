package net.brightroom.mindstock.domain.model.user

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

/**
 * Zitadel が発行するユーザーのサブジェクト識別子。空文字禁止。
 */
@Serializable
@JvmInline
value class ZitadelSub(
    private val value: String,
) {
    init {
        if (value.isBlank()) throw DomainException.ZitadelSubBlank()
    }

    override fun toString(): String = value

    internal operator fun invoke(): String = value
}
