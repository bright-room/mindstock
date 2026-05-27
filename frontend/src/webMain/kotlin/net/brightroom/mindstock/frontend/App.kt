package net.brightroom.mindstock.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.rpc.withService
import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.frontend.auth.AuthBootstrap
import net.brightroom.mindstock.frontend.auth.AuthCallbackHandler
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.auth.AuthState
import net.brightroom.mindstock.frontend.auth.OidcException
import net.brightroom.mindstock.frontend.auth.PingResult
import net.brightroom.mindstock.frontend.auth.Pkce
import net.brightroom.mindstock.frontend.auth.TokenStore
import net.brightroom.mindstock.frontend.auth.Tokens
import net.brightroom.mindstock.frontend.rpc.RpcClientFactory
import net.brightroom.mindstock.frontend.rpc.UnauthorizedException
import net.brightroom.mindstock.frontend.ui.callback.AuthCallbackScreen
import net.brightroom.mindstock.frontend.ui.login.LoginScreen
import net.brightroom.mindstock.frontend.ui.register.RegisterDialog
import net.brightroom.mindstock.frontend.ui.shell.AppShell
import net.brightroom.mindstock.presentation.rpc.HouseholdRpcService
import net.brightroom.mindstock.presentation.rpc.UserPublicRpcService

private const val STATE_KEY = "mindstock.oauth.state.v1"
private const val VERIFIER_KEY = "mindstock.oauth.verifier.v1"
private const val RETURN_TO_KEY = "mindstock.oauth.return_to.v1"

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AuthState>(AuthState.Authenticating) }
    var displayName by remember { mutableStateOf<String?>(null) }
    var registerError by remember { mutableStateOf<String?>(null) }
    var registerSubmitting by remember { mutableStateOf(false) }

    val httpClient = remember {
        HttpClient {
            install(ContentNegotiation) { json() }
            install(WebSockets)
        }
    }
    val authClient = remember {
        AuthClient(httpClient, AuthConfig.ISSUER, AuthConfig.CLIENT_ID, AuthConfig.REDIRECT_URI)
    }
    val rpcFactory = remember {
        RpcClientFactory(httpClient, baseUrl = window.location.origin)
    }

    LaunchedEffect(Unit) {
        if (window.location.pathname == "/auth/callback") {
            handleCallback(authClient) { newState -> state = newState }
            return@LaunchedEffect
        }
        val ping: suspend (Tokens) -> PingResult = { tokens ->
            try {
                val rpc = rpcFactory.open("household", tokens.accessToken)
                rpc.withService<HouseholdRpcService>().findOf()
                PingResult.Success
            } catch (_: UnauthorizedException) {
                PingResult.Unauthorized
            } catch (_: Throwable) {
                PingResult.Other
            }
        }
        state = AuthBootstrap(authClient, ping).start()
    }

    MaterialTheme {
        when (val s = state) {
            is AuthState.LoggedOut -> LoginScreen(onLogin = { scope.launch { startLogin() } })
            is AuthState.Authenticating -> AuthCallbackScreen()
            is AuthState.NeedRegister -> RegisterDialog(
                errorMessage = registerError,
                submitting = registerSubmitting,
                onSubmit = { name ->
                    registerSubmitting = true
                    registerError = null
                    scope.launch {
                        try {
                            val tokens = TokenStore.load() ?: error("no tokens")
                            val rpc = rpcFactory.open("user/public", tokens.accessToken)
                            rpc.withService<UserPublicRpcService>().register(DisplayName(name))
                            displayName = name
                            state = AuthState.Ready(tokens)
                        } catch (e: Throwable) {
                            registerError = e.message ?: "登録に失敗しました"
                        } finally {
                            registerSubmitting = false
                        }
                    }
                },
            )
            is AuthState.Ready -> AppShell(
                displayName = displayName ?: "user",
                onLogout = {
                    val idToken = s.tokens.idToken
                    TokenStore.clear()
                    rpcFactory.closeAll()
                    window.location.assign(
                        AuthClient.endSessionUrl(AuthConfig.ISSUER, idToken, AuthConfig.POST_LOGOUT_REDIRECT_URI),
                    )
                },
            )
            is AuthState.Error -> Text(s.message)
        }
    }
}

private suspend fun startLogin() {
    val verifier = Pkce.newVerifier()
    val state = Pkce.newVerifier(length = 43)
    val returnTo = window.location.pathname + window.location.search
    window.sessionStorage.setItem(STATE_KEY, state)
    window.sessionStorage.setItem(VERIFIER_KEY, verifier)
    window.sessionStorage.setItem(RETURN_TO_KEY, returnTo)
    val challenge = Pkce.challenge(verifier)
    val scope = "openid profile offline_access urn:zitadel:iam:org:project:id:${AuthConfig.PROJECT_ID}:aud"
    val url = AuthClient.buildAuthorizeUrl(
        issuer = AuthConfig.ISSUER,
        clientId = AuthConfig.CLIENT_ID,
        redirectUri = AuthConfig.REDIRECT_URI,
        scope = scope,
        state = state,
        codeChallenge = challenge,
    )
    window.location.assign(url)
}

private suspend fun handleCallback(authClient: AuthClient, setState: (AuthState) -> Unit) {
    val params = window.location.search.removePrefix("?").split("&").mapNotNull {
        if (it.isBlank()) return@mapNotNull null
        val idx = it.indexOf('=')
        val rawKey = if (idx < 0) it else it.substring(0, idx)
        val rawVal = if (idx < 0) "" else it.substring(idx + 1)
        runCatching { decodeUriComponent(rawKey) to decodeUriComponent(rawVal) }.getOrNull()
    }.toMap()
    val savedState = window.sessionStorage.getItem(STATE_KEY)
    val savedVerifier = window.sessionStorage.getItem(VERIFIER_KEY)
    val handler = AuthCallbackHandler(authClient, savedState, savedVerifier)
    try {
        val tokens = handler.handle(receivedState = params["state"] ?: "", code = params["code"] ?: "")
        TokenStore.save(tokens)
        window.sessionStorage.removeItem(STATE_KEY)
        window.sessionStorage.removeItem(VERIFIER_KEY)
        val returnTo = window.sessionStorage.getItem(RETURN_TO_KEY) ?: "/"
        window.sessionStorage.removeItem(RETURN_TO_KEY)
        window.location.replace(returnTo)
    } catch (e: OidcException) {
        setState(AuthState.Error("ログインに失敗しました: ${e.errorCode}"))
    }
}
