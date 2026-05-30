package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * 世帯名。空白のみ(空文字含む)禁止、最大 100 文字。後から変更可能(履歴は household_names 事実テーブル)。
 */
@Serializable
@JvmInline
value class HouseholdName(
    private val value: String,
) {
    init {
        require(value.isNotBlank()) { "household name must not be blank" }
        require(value.length <= 100) { "household name length ${value.length} > 100" }
    }

    override fun toString(): String = value

    operator fun invoke(): String = value
}
