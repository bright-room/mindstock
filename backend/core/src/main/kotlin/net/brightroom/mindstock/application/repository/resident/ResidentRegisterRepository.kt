package net.brightroom.mindstock.application.repository.resident

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

interface ResidentRegisterRepository {
    /** residents + auth + 初回 display_name を INSERT して Resident を返す(id はここで採番)。 */
    fun registerResident(
        authIdentity: AuthIdentity,
        displayName: DisplayName,
    ): Resident

    /** display_name を 1 行 append(registerDisplayName/rename 兼用)。 */
    fun appendDisplayName(
        residentId: ResidentId,
        displayName: DisplayName,
    )
}
