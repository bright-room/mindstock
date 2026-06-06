package net.brightroom.mindstock.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.feature_coming_soon
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.need_household
import mindstock.frontend.generated.resources.onboarding_placeholder
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.frontend.app.AuthViewModel
import net.brightroom.mindstock.frontend.app.shell.AppShell
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.auth.TokenStore
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcClientProvider
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.Toast
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockTheme
import net.brightroom.mindstock.frontend.feature.activity.ActivityViewModel
import net.brightroom.mindstock.frontend.feature.activity.ui.ActivityScreen
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel
import net.brightroom.mindstock.frontend.feature.inventory.data.InventoryRepository
import net.brightroom.mindstock.frontend.feature.inventory.ui.DetailTarget
import net.brightroom.mindstock.frontend.feature.inventory.ui.InventoryRoute
import net.brightroom.mindstock.frontend.feature.inventory.ui.ProductDetailOverlay
import net.brightroom.mindstock.frontend.feature.shopping.ShoppingListViewModel
import net.brightroom.mindstock.frontend.feature.shopping.ui.ShoppingListScreen
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.stock.StockRegisterRpcService
import net.brightroom.mindstock.rpc.stock.StockRpcService
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
    val deps = remember { WebAuthDeps(authClient, rpc, session) }
    val vm = remember { AuthViewModel(deps) }
    val state by vm.state.collectAsState()

    DisposableEffect(http) {
        onDispose { http.close() }
    }
    LaunchedEffect(Unit) { vm.boot() }

    MindstockTheme {
        val scope = rememberCoroutineScope()
        val toast = remember { ToastController() }
        val reauth = remember { ReauthController() }
        val refresh = remember { InventoryRefreshController() }
        val sessionState by session.state.collectAsState()
        val toastMessage by toast.current.collectAsState()

        // 再認証受け口（単一）: token 破棄 → WS 閉じ → authorize へ redirect（ページ離脱）
        LaunchedEffect(reauth) {
            reauth.signal.collectLatest {
                TokenStore.clear()
                rpc.close()
                deps.redirectToAuthorize()
            }
        }

        val repository =
            remember {
                InventoryRepository(
                    productService = { rpc.service<ProductRpcService>() },
                    stockService = { rpc.service<StockRpcService>() },
                    stockRegisterService = { rpc.service<StockRegisterRpcService>() },
                    productRegisterService = { rpc.service<ProductRegisterRpcService>() },
                )
            }

        Box(Modifier.fillMaxSize()) {
            when (state) {
                is AuthState.Booting -> {
                    AppText(stringResource(Res.string.loading))
                }

                is AuthState.Failed -> {
                    AppText((state as AuthState.Failed).message)
                }

                is AuthState.NeedOnboarding -> {
                    AppText(stringResource(Res.string.onboarding_placeholder))
                }

                is AuthState.NeedHousehold -> {
                    AppText(stringResource(Res.string.need_household))
                }

                is AuthState.Ready -> {
                    val householdId = sessionState.activeHouseholdId
                    if (householdId == null) {
                        AppText(stringResource(Res.string.need_household))
                    } else {
                        var opened by remember { mutableStateOf<DetailTarget?>(null) }
                        val homeVm =
                            remember(householdId) {
                                InventoryViewModel(
                                    householdId = householdId,
                                    loadStocks = repository::list,
                                    replenishStock = repository::replenish,
                                    consumeStock = repository::consume,
                                    refresh = refresh,
                                    toast = toast,
                                    reauth = reauth,
                                )
                            }
                        val shopVm =
                            remember(householdId) {
                                ShoppingListViewModel(
                                    householdId = householdId,
                                    loadShoppingList = repository::shoppingList,
                                    setWantedFlag = repository::setWanted,
                                    replenishStock = repository::replenish,
                                    refresh = refresh,
                                    toast = toast,
                                    reauth = reauth,
                                )
                            }
                        val activityVm =
                            remember(householdId) {
                                ActivityViewModel(
                                    householdId = householdId,
                                    loadActivity = repository::activity,
                                    toast = toast,
                                    reauth = reauth,
                                )
                            }
                        AppShell(
                            stockContent = {
                                InventoryRoute(
                                    homeViewModel = homeVm,
                                    refresh = refresh,
                                    onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
                                    onAddProduct = { toast.show(UiText(Res.string.feature_coming_soon)) },
                                    displayName = sessionState.displayName?.invoke() ?: "",
                                )
                            },
                            shopContent = {
                                val shopState by shopVm.state.collectAsState()
                                LaunchedEffect(shopVm) { shopVm.load() }
                                LaunchedEffect(refresh) { refresh.signal.collect { shopVm.load() } }
                                ShoppingListScreen(
                                    state = shopState,
                                    onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
                                    onSetWanted = { pid, w -> scope.launch { shopVm.setWanted(pid, w) } },
                                    onReplenish = { pid, q, n -> scope.launch { shopVm.replenish(pid, Quantity(q), Note(n)) } },
                                )
                            },
                            activityContent = {
                                val activityState by activityVm.state.collectAsState()
                                LaunchedEffect(activityVm) { activityVm.load() }
                                LaunchedEffect(refresh) { refresh.signal.collect { activityVm.load() } }
                                ActivityScreen(
                                    state = activityState,
                                    onOpenProduct = { pid -> opened = DetailTarget(pid, null) },
                                )
                            },
                        )
                        val target = opened
                        if (target != null) {
                            ProductDetailOverlay(
                                target = target,
                                viewModelFactory = { t ->
                                    ProductDetailViewModel(
                                        householdId = householdId,
                                        productId = t.productId,
                                        seed = t.seed,
                                        loadShoppingList = repository::shoppingList,
                                        loadHistory = repository::history,
                                        replenishStock = repository::replenish,
                                        consumeStock = repository::consume,
                                        correctMovement = repository::correct,
                                        setWantedFlag = repository::setWanted,
                                        refresh = refresh,
                                        toast = toast,
                                        reauth = reauth,
                                    )
                                },
                                refresh = refresh,
                                onBack = { opened = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            // トースト全体オーバーレイ
            Toast(message = toastMessage?.text?.resolve(), modifier = Modifier.align(Alignment.BottomCenter))
            LaunchedEffect(toastMessage) {
                if (toastMessage != null) {
                    delay(2500)
                    toast.dismiss()
                }
            }
        }
    }
}
