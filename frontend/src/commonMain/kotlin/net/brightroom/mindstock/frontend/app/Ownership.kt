package net.brightroom.mindstock.frontend.app

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

/**
 * active 世帯で当該住人が世帯主か。session 欠落・非メンバー時は false。
 * サーバが owner を強制するので、これは UX ゲート(押せないボタンの非表示)専用。
 */
fun isOwner(
    households: Households?,
    activeHouseholdId: HouseholdId?,
    residentId: ResidentId?,
): Boolean {
    if (households == null || activeHouseholdId == null || residentId == null) return false
    val household = households.list.firstOrNull { it.id == activeHouseholdId } ?: return false
    if (!household.members.contains(residentId)) return false
    return household.members.roleOf(residentId).is世帯主()
}
