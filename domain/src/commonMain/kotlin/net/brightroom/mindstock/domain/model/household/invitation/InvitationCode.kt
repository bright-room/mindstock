package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
@JvmInline
value class InvitationCode(
    private val value: String,
) {
    init {
        require(value.length == LENGTH && value.all { it in ALPHABET }) {
            "InvitationCode must be $LENGTH chars from the unambiguous alphabet: '$value'"
        }
    }

    internal operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        const val LENGTH = 6

        // 曖昧字 0 / O / 1 / I を除外した英数字
        const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        fun generate(): InvitationCode =
            InvitationCode(
                buildString {
                    repeat(LENGTH) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
                },
            )
    }
}
