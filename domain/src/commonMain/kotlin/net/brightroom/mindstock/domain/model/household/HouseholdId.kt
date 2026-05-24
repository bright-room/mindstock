package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class HouseholdId(
    private val value: Uuid,
) {
    override fun toString(): String = value.toString()

    operator fun invoke(): Uuid = value

    companion object {
        fun create(): HouseholdId = HouseholdId(Uuid.generateV7())
    }
}
