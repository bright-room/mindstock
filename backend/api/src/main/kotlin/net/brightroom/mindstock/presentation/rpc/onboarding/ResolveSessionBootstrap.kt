package net.brightroom.mindstock.presentation.rpc.onboarding

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.SessionBootstrap

/**
 * session から起動時初期化情報を組み立てる。
 *
 * 重要: session.userId は handshake 時に固定された値で、同一接続で register した直後はまだ null の
 * まま(MindstockSession は接続単位 immutable)。よって登録状態は session.identity で DB を引き直して判定する。
 *
 * - identity の User が存在しない(ResourceNotFoundException)→ Unregistered
 * - 存在する → Registered(displayName / householdId / householdName)
 *
 * 例外 → 値の変換は presentation の腐敗防止。DB アクセスを含むため呼び出し側が transaction 境界を張る。
 */
fun resolveSessionBootstrap(
    session: MindstockSession,
    userService: UserService,
    householdService: HouseholdService,
): SessionBootstrap {
    val profile =
        try {
            userService.findByIdentity(session.identity)
        } catch (e: ResourceNotFoundException) {
            return SessionBootstrap.Unregistered
        }
    val household = householdService.findOf(profile.userId)
    return SessionBootstrap.Registered(
        displayName = profile.displayName,
        householdId = household.id,
        householdName = household.name,
    )
}
