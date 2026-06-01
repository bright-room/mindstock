@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.resident.identity

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class ResidentId(
    private val value: Uuid,
) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): ResidentId = ResidentId(Uuid.generateV7())
    }
}
