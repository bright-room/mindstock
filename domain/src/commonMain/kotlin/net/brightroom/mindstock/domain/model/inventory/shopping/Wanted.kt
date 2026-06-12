package net.brightroom.mindstock.domain.model.inventory.shopping

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** 手動の買い物希望フラグ。`@JvmInline` のため wire 上は素の Boolean に unwrap される。 */
@Serializable
@JvmInline
value class Wanted(
    private val value: Boolean,
) {
    operator fun invoke(): Boolean = value

    override fun toString(): String = value.toString()
}
