package net.brightroom.mindstock.domain.model.user

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class UserId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
