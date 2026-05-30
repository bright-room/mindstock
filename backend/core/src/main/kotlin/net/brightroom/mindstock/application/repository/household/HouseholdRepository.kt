package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.user.UserId

interface HouseholdRepository {
    /**
     * ユーザーが所属する世帯(MVP は 1 ユーザー 1 世帯前提)。
     * 未所属なら `ResourceNotFoundException` を throw する。
     */
    suspend fun findOf(userId: UserId): Household

    /**
     * id 引き(主に RPC 経由)。
     * 該当 household が存在しなければ `ResourceNotFoundException` を throw する。
     */
    suspend fun findById(id: HouseholdId): Household
}
