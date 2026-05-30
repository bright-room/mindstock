package net.brightroom.mindstock.application.scenario.onboarding

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.user.UserRegisterService
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.DisplayName
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 初回サインインのオンボーディング。User + Household(デフォルト名)+ OWNER membership を 1 ユースケースで揃える。
 *
 * 冪等: 既に当該 identity の User が存在する場合は何も作らず既存 Profile を返す。
 * トランザクション境界は呼び出し側(Controller の `tx()`)が張る。
 */
class RegisterFirstHouseholdScenario(
    private val userRepository: UserRepository,
    private val userRegisterService: UserRegisterService,
    private val householdRegisterService: HouseholdRegisterService,
) {
    fun run(
        identity: AuthIdentity,
        displayName: DisplayName,
    ): Profile {
        val existing =
            try {
                userRepository.findProfileByAuthIdentity(identity)
            } catch (e: ResourceNotFoundException) {
                null
            }
        if (existing != null) return existing

        val profile = userRegisterService.register(identity, displayName)
        householdRegisterService.create(profile.userId, defaultHouseholdName(displayName))
        return profile
    }

    /** 表示名から導出するデフォルト世帯名。HouseholdName(100) を超えないよう表示名を丸める。 */
    private fun defaultHouseholdName(displayName: DisplayName): HouseholdName = HouseholdName("${displayName().take(97)}の家")
}
