package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class HouseholdProfile(
    val name: HouseholdName,
)
