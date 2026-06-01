@file:OptIn(ExperimentalUuidApi::class)

package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class HouseholdId(
    private val value: Uuid,
) {
    internal operator fun invoke(): Uuid = value

    override fun toString(): String = value.toString()

    companion object {
        fun create(): HouseholdId = HouseholdId(Uuid.generateV7())
    }
}
