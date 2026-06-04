package net.brightroom.mindstock.frontend.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.AuthState

/**
 * boot に必要な副作用の境界。本番実装は app/ で web 用に束ね、テストは fake を差す。
 * registered 判定は me() の throw を未登録に倒す(前提 #3)。
 */
interface AuthDeps {
    fun currentPath(): String

    /** /auth/callback の code を交換し token を保存して "/" へ replace。失敗時 throw。 */
    suspend fun handleCallback()

    /** 保存済みの有効トークン。無ければ null。 */
    fun loadValidToken(): Tokens?

    /** code_verifier 生成→保存→authorize へ redirect。 */
    suspend fun redirectToAuthorize()

    /** me() を呼ぶ。登録済みなら Resident、未登録/拒否なら throw。 */
    suspend fun fetchMe(token: Tokens): Resident

    /** 取得済み Resident をセッションに反映。 */
    fun onAuthenticated(resident: Resident)
}

class AuthViewModel(
    private val deps: AuthDeps,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Booting)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun boot() {
        if (deps.currentPath() == "/auth/callback") {
            runCatching { deps.handleCallback() }
                .onFailure { _state.value = AuthState.Failed("ログインに失敗しました") }
            // 成功時は replace("/") で再起動するため state は Booting のまま離脱
            return
        }
        val token = deps.loadValidToken()
        if (token == null) {
            deps.redirectToAuthorize()
            return // redirect でページ離脱。Booting のまま
        }
        try {
            val resident = deps.fetchMe(token)
            deps.onAuthenticated(resident)
            _state.value = AuthState.Ready
        } catch (_: Throwable) {
            // /resident は登録済み必須ルート。未登録は WS handshake で拒否され throw。
            // ブラウザは 401 を JS に公開しないため、ここは未登録に倒す(前提 #3)。
            _state.value = AuthState.NeedOnboarding
        }
    }
}
