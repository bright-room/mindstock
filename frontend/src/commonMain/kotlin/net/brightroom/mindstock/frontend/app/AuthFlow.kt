package net.brightroom.mindstock.frontend.app

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.resident.Resident

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
}
