package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable

@Serializable
data class ResidentProfile(
    val displayName: DisplayName,
)
