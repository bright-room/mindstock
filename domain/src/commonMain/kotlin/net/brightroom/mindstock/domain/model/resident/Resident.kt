package net.brightroom.mindstock.domain.model.resident

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.Profile

@Serializable
data class Resident(
    val id: ResidentId,
    val profile: Profile,
)
