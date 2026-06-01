package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val name: HouseholdName,
)
