package net.brightroom.mindstock.domain.model.household.member

import kotlinx.serialization.Serializable

@Serializable
enum class HouseholdMemberRole {
    世帯主,
    メンバー,
    閲覧者,
    ;

    fun is世帯主(): Boolean = this == 世帯主
}
