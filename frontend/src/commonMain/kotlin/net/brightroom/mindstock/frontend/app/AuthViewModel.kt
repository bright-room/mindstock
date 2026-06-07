package net.brightroom.mindstock.frontend.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.rpc.session.SessionStatus

/**
 * boot に必要な副作用の境界。本番実装は app/ で web 用に束ね、テストは fake を差す。
 * registered 判定は whoami() が返す SessionStatus で明示的に分岐する(例外は本当の通信失敗のみ)。
 */
interface AuthDeps {
    fun currentPath(): String

    /** /auth/callback の code を交換し token を保存して "/" へ replace。失敗時 throw。 */
    suspend fun handleCallback()

    /** 保存済みの有効トークン。無ければ null。 */
    fun loadValidToken(): Tokens?

    /** code_verifier 生成→保存→authorize へ redirect。 */
    suspend fun redirectToAuthorize()

    /** 単一接続を張り whoami を呼んで登録状態を返す。失敗時 throw。 */
    suspend fun fetchSessionStatus(token: Tokens): SessionStatus

    /** 取得済み Resident をセッションに反映。 */
    fun onAuthenticated(resident: Resident)

    /** 所属世帯一覧をロード（whoami=Registered 後）。失敗時 throw。 */
    suspend fun loadHouseholds(): Households

    /** ロードした世帯と先頭アクティブをセッションに反映。 */
    fun onHouseholdsLoaded(
        households: Households,
        active: HouseholdId,
    )

    /** WS を貼り直す(close → connect)。登録直後にセッションを Registered へ昇格させる。 */
    suspend fun reconnect(token: Tokens)

    /** アクティブ世帯を永続化する。 */
    fun persistActiveHousehold(id: HouseholdId)

    /** 永続化済みアクティブ世帯。無ければ null。 */
    fun savedActiveHousehold(): HouseholdId?
}

class AuthViewModel(
    private val deps: AuthDeps,
) : ViewModel(),
    AuthFlow {
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
            when (val status = deps.fetchSessionStatus(token)) {
                is SessionStatus.Registered -> {
                    deps.onAuthenticated(status.resident)
                    val households = deps.loadHouseholds()
                    val saved = deps.savedActiveHousehold()
                    val active =
                        households.list.firstOrNull { it.id == saved }
                            ?: households.list.firstOrNull()
                    if (active == null) {
                        _state.value = AuthState.NeedHousehold
                    } else {
                        deps.onHouseholdsLoaded(households, active.id)
                        deps.persistActiveHousehold(active.id)
                        _state.value = AuthState.Ready
                    }
                }

                is SessionStatus.Unregistered -> {
                    _state.value = AuthState.NeedOnboarding
                }
            }
        } catch (cancellation: CancellationException) {
            // 構造化並行性: キャンセルは握り潰さず伝播させる。
            throw cancellation
        } catch (_: Exception) {
            // 登録状態は whoami が明示的に返すため、ここに来るのは通信失敗等の本当のエラー。
            // Error(OOM 等)は捕捉しない(回復不能なのでクラッシュさせる)。
            _state.value = AuthState.Failed("起動に失敗しました")
        }
    }

    override suspend fun onResidentRegistered(resident: Resident) {
        deps.onAuthenticated(resident)
        val token = deps.loadValidToken() ?: error("token lost during registration")
        deps.reconnect(token)
    }

    override suspend fun enterApp(activeId: HouseholdId) {
        val households = deps.loadHouseholds()
        deps.onHouseholdsLoaded(households, activeId)
        deps.persistActiveHousehold(activeId)
        _state.value = AuthState.Ready
    }

    override fun needHousehold() {
        _state.value = AuthState.NeedHousehold
    }
}
