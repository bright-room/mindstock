package net.brightroom.mindstock.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.need_household
import mindstock.frontend.generated.resources.onboarding_placeholder
import mindstock.frontend.generated.resources.stock_placeholder
import net.brightroom.mindstock.frontend.app.AuthViewModel
import net.brightroom.mindstock.frontend.app.shell.AppShell
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.frontend.core.rpc.RpcClientProvider
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun App() {
    val http =
        remember {
            HttpClient {
                install(ContentNegotiation) { json() }
                install(WebSockets)
            }
        }
    val authClient = remember { AuthClient(http, AuthConfig.ISSUER, AuthConfig.CLIENT_ID, AuthConfig.REDIRECT_URI) }
    val session = remember { AppSession() }
    val rpc =
        remember {
            val wsBase =
                window.location.origin
                    .replaceFirst("https://", "wss://")
                    .replaceFirst("http://", "ws://")
            RpcClientProvider(http, baseUrl = wsBase)
        }
    val vm = remember { AuthViewModel(WebAuthDeps(authClient, rpc, session)) }
    val state by vm.state.collectAsState()

    DisposableEffect(http) {
        onDispose { http.close() }
    }
    LaunchedEffect(Unit) { vm.boot() }

    MindstockTheme {
        when (state) {
            is AuthState.Booting -> AppText(stringResource(Res.string.loading))
            is AuthState.Failed -> AppText((state as AuthState.Failed).message)
            is AuthState.NeedHousehold -> AppText(stringResource(Res.string.need_household))
            is AuthState.NeedOnboarding -> AppText(stringResource(Res.string.onboarding_placeholder))
            is AuthState.Ready -> AppShell(stockContent = { AppText(stringResource(Res.string.stock_placeholder)) })
        }
    }
}
