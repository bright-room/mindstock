package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
public value class HouseholdId(private val value: Uuid) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value
}
