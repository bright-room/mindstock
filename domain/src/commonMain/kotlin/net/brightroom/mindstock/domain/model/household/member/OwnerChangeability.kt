package net.brightroom.mindstock.domain.model.household.member

import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

enum class OwnerChangeability(
    val allowed: Boolean,
) {
    可能(true),
    最後の世帯主(false),
    ;

    companion object {
        fun on(
            members: Members,
            target: ResidentId,
        ): OwnerChangeability {
            val owners = members.list.filter { it.role.is世帯主() }
            val targetIsSoleOwner = owners.size == 1 && owners.first().resident.id == target
            return if (targetIsSoleOwner) 最後の世帯主 else 可能
        }
    }
}
