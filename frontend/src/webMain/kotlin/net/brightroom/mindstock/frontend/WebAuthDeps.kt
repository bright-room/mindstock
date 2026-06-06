package net.brightroom.mindstock.frontend

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.frontend.app.AuthDeps
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.auth.OidcException
import net.brightroom.mindstock.frontend.auth.Pkce
import net.brightroom.mindstock.frontend.auth.SessionStorage
import net.brightroom.mindstock.frontend.auth.TokenStore
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.core.auth.BrowserNav
import net.brightroom.mindstock.frontend.core.rpc.RpcClientProvider
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.result.RpcResult
import net.brightroom.mindstock.rpc.session.SessionRpcService
import net.brightroom.mindstock.rpc.session.SessionStatus

private const val STATE_KEY = "mindstock.oauth.state.v1"
private const val VERIFIER_KEY = "mindstock.oauth.verifier.v1"

class WebAuthDeps(
    private val authClient: AuthClient,
    private val rpc: RpcClientProvider,
    private val session: AppSession,
) : AuthDeps {
    override fun currentPath(): String = BrowserNav.currentPath()

    override suspend fun handleCallback() {
        // OAuth 2.0: 認可失敗時は code ではなく error/error_description が返る(同意拒否・認可サーバエラー等)。
        val oauthError = BrowserNav.currentQueryParam("error")
        if (oauthError != null) {
            throw OidcException(
                oauthError,
                BrowserNav.currentQueryParam("error_description"),
                reauthRequired = oauthError == "access_denied",
            )
        }
        val savedState = SessionStorage.get(STATE_KEY)
        val savedVerifier = SessionStorage.get(VERIFIER_KEY) ?: error("no verifier")
        val receivedState = BrowserNav.currentQueryParam("state") ?: ""
        require(savedState != null && savedState == receivedState) { "state mismatch" }
        val code = BrowserNav.currentQueryParam("code") ?: error("no code")
        val tokens = authClient.exchangeCode(code, savedVerifier)
        TokenStore.save(tokens)
        SessionStorage.remove(STATE_KEY)
        SessionStorage.remove(VERIFIER_KEY)
        BrowserNav.replace("/")
    }

    override fun loadValidToken(): Tokens? = TokenStore.load()?.takeUnless { it.willExpireWithin(30) }

    override suspend fun redirectToAuthorize() {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newVerifier(length = 43)
        SessionStorage.set(STATE_KEY, state)
        SessionStorage.set(VERIFIER_KEY, verifier)
        val scope = "openid profile offline_access urn:zitadel:iam:org:project:id:${AuthConfig.PROJECT_ID}:aud"
        val url =
            AuthClient.buildAuthorizeUrl(
                issuer = AuthConfig.ISSUER,
                clientId = AuthConfig.CLIENT_ID,
                redirectUri = AuthConfig.REDIRECT_URI,
                scope = scope,
                state = state,
                codeChallenge = Pkce.challenge(verifier),
            )
        BrowserNav.assign(url)
    }

    override suspend fun fetchSessionStatus(token: Tokens): SessionStatus {
        rpc.connect(token.accessToken)
        return when (val r = rpc.service<SessionRpcService>().whoami()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("whoami failed: ${r.error}")
        }
    }

    override fun onAuthenticated(resident: Resident) {
        session.setResident(resident.id, resident.profile.displayName)
    }

    override suspend fun loadHouseholds(): Households =
        when (val r = rpc.service<HouseholdRpcService>().list()) {
            is RpcResult.Ok -> r.value
            is RpcResult.Err -> error("household list failed: ${r.error}")
        }

    override fun onHouseholdsLoaded(
        households: Households,
        active: HouseholdId,
    ) {
        session.setHouseholds(households, active)
    }
}
