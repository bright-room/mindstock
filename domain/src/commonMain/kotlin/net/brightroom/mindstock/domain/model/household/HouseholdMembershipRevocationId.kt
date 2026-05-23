package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.exception.DomainException
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
public value class HouseholdMembershipRevocationId(
    private val value: Long,
) {
    init {
        if (value < 0) throw DomainException.InvalidIdentity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Long = value
}
