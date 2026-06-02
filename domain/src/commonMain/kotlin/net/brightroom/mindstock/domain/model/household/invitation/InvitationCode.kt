package net.brightroom.mindstock.domain.model.household.invitation

import kotlinx.serialization.Serializable
import org.kotlincrypto.random.CryptoRand
import kotlin.jvm.JvmInline

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

    operator fun invoke(): String = value

    override fun toString(): String = value

    companion object {
        private const val LENGTH = 6

        // 曖昧字 0 / O / 1 / I を除外した英数字
        private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

        fun generate(): InvitationCode {
            // ALPHABET.length(32) は 256 を割り切るので (byte % 32) は不偏
            val bytes = CryptoRand.Default.nextBytes(ByteArray(LENGTH))
            return InvitationCode(
                bytes.joinToString("") { ALPHABET[(it.toInt() and 0xFF) % ALPHABET.length].toString() },
            )
        }
    }
}
