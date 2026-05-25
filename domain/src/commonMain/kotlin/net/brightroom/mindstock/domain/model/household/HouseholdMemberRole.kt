package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
enum class HouseholdMemberRole {
    OWNER,
    MEMBER,
}
