package net.brightroom.mindstock.frontend.app

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName

/**
 * オンボーディング/世帯作成・参加の完了時に呼び戻す coordinator。AuthViewModel が実装する。
 */
interface AuthFlow {
    /** 登録済み Resident を session に反映し、WS を再接続して Registered セッションを獲得する。失敗時 throw。 */
    suspend fun onResidentRegistered(resident: Resident)

    /** 世帯一覧を再ロードし、activeId をアクティブにして session 反映+永続化し、Ready に遷移。失敗時 throw。 */
    suspend fun enterApp(activeId: HouseholdId)

    /** 世帯ゼロ(スキップ)へ。NeedHousehold に遷移。 */
    fun needHousehold()

    /** アクティブ世帯を切り替える(WS 再接続なし)。session 反映 + 永続化。 */
    fun switchActiveHousehold(id: HouseholdId)

    /** 世帯一覧を再ロードし、現在のアクティブを維持して session 反映。失敗時 throw。 */
    suspend fun refreshHouseholds()

    /** 表示名を session に反映する(resident rename は Unit 戻りのため VM が DisplayName を渡す)。 */
    fun applyDisplayName(name: DisplayName)

    /** アクティブ世帯から退出した後の再ロード + アクティブ再選択。ゼロなら NeedHousehold。失敗時 throw。 */
    suspend fun leaveActiveHousehold()
}
