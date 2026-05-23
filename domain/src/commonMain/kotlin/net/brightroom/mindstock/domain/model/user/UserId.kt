package net.brightroom.mindstock.domain.model.user

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class UserId(
    private val value: Uuid,
) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value

    companion object {
        fun create(): UserId = UserId(Uuid.generateV7())
    }
}
