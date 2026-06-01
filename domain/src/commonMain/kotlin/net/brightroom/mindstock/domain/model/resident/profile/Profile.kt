package net.brightroom.mindstock.domain.model.resident.profile

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val displayName: DisplayName,
)
