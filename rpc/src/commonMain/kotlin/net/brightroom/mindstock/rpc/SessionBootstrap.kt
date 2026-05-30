package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.profile.DisplayName

/**
 * 起動時セッション初期化情報。`bootstrap()` の戻り値。
 *
 * KrpcJson(POLYMORPHIC discriminator)で wire を通るため sealed で表現でき、nullable を持たない。
 */
@Serializable
sealed interface SessionBootstrap {
    @Serializable
    data object Unregistered : SessionBootstrap

    @Serializable
    data class Registered(
        val displayName: DisplayName,
        val householdId: HouseholdId,
        val householdName: HouseholdName,
    ) : SessionBootstrap
}
