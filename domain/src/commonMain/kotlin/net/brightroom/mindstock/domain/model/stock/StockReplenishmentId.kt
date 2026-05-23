package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import net.brightroom.mindstock.domain.exception.DomainException

@Serializable
@JvmInline
public value class StockReplenishmentId(private val value: Long) {
    init {
        if (value < 0) throw DomainException.InvalidIdentity(value)
    }

    override fun toString(): String = value.toString()

    internal operator fun invoke(): Long = value
}
