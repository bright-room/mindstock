package net.brightroom.mindstock.application.service.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRegisterRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

class ResidentRegisterService(
    private val residentRegisterRepository: ResidentRegisterRepository,
) {
    /** UC2 初回登録。authIdentity は session 由来。Resident をここで採番して返す。 */
    fun register(
        authIdentity: AuthIdentity,
        displayName: DisplayName,
    ): Resident = residentRegisterRepository.registerResident(authIdentity, displayName)

    /** 表示名変更(append-only)。 */
    fun rename(
        actor: ResidentId,
        displayName: DisplayName,
    ) = residentRegisterRepository.appendDisplayName(actor, displayName)
}
