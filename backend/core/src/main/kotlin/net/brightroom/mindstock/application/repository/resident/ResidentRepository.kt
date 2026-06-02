package net.brightroom.mindstock.application.repository.resident

import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.identity.auth.AuthIdentity

interface ResidentRepository {
    /** 認証境界 VO で resident を解決(初回ログイン=未登録 sub は ResourceNotFoundException)。 */
    fun findByAuth(authIdentity: AuthIdentity): Resident

    fun findById(id: ResidentId): Resident
}
