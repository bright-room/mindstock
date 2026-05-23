package net.brightroom.mindstock.domain.model.household

import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@OptIn(ExperimentalUuidApi::class)
@Serializable
@JvmInline
value class HouseholdId(
    private val value: Uuid,
) {
    override fun toString(): String = value.toString()

    internal operator fun invoke(): Uuid = value

    companion object {
        fun create(): HouseholdId = HouseholdId(Uuid.generateV7())
    }
}
