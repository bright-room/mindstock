package net.brightroom.mindstock.frontend.core.auth

/** 起動〜認証の画面状態。 */
sealed interface AuthState {
    /** 起動処理中(callback 交換 or token 検証 or me() 問い合わせ)。 */
    data object Booting : AuthState

    /** 認証済み・登録済み。app 本体へ。 */
    data object Ready : AuthState

    /** 認証済みだが Resident 未登録。表示名登録 → 世帯作成へ。 */
    data object NeedOnboarding : AuthState

    /** 失敗。message を表示し再ログイン可能に。 */
    data class Failed(
        val message: String,
    ) : AuthState
}
