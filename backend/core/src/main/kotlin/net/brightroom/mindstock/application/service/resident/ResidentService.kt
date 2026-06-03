package net.brightroom.mindstock.application.service.resident

import net.brightroom.mindstock.application.repository.resident.ResidentRepository
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

class ResidentService(
    private val residentRepository: ResidentRepository,
) {
    fun me(actor: ResidentId): Resident = residentRepository.findById(actor)
}
