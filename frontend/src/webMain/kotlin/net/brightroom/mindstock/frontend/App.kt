package net.brightroom.mindstock.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.need_household_title
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.EvaluatedTime
import net.brightroom.mindstock.domain.model.inventory.stock.Stock
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.OccurredAt
import net.brightroom.mindstock.frontend.app.AuthViewModel
import net.brightroom.mindstock.frontend.app.isOwner
import net.brightroom.mindstock.frontend.app.settings.SettingsScreen
import net.brightroom.mindstock.frontend.app.shell.AppShell
import net.brightroom.mindstock.frontend.app.shell.Tab
import net.brightroom.mindstock.frontend.app.welcome.WelcomeScreen
import net.brightroom.mindstock.frontend.auth.AuthClient
import net.brightroom.mindstock.frontend.auth.AuthConfig
import net.brightroom.mindstock.frontend.auth.TokenStore
import net.brightroom.mindstock.frontend.core.auth.AuthState
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.image.LocalProductImageLoader
import net.brightroom.mindstock.frontend.core.image.ProductImageLoader
import net.brightroom.mindstock.frontend.core.image.rememberProductThumbnail
import net.brightroom.mindstock.frontend.core.rpc.RpcClientProvider
import net.brightroom.mindstock.frontend.core.session.AppSession
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.resolve
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.Toast
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockTheme
import net.brightroom.mindstock.frontend.feature.activity.ActivityViewModel
import net.brightroom.mindstock.frontend.feature.activity.ui.ActivityScreen
import net.brightroom.mindstock.frontend.feature.catalog.AddProductUiState
import net.brightroom.mindstock.frontend.feature.catalog.AddProductViewModel
import net.brightroom.mindstock.frontend.feature.catalog.ArchivedViewModel
import net.brightroom.mindstock.frontend.feature.catalog.ProductMasterViewModel
import net.brightroom.mindstock.frontend.feature.catalog.data.CatalogRepository
import net.brightroom.mindstock.frontend.feature.catalog.ui.AddProductScreen
import net.brightroom.mindstock.frontend.feature.catalog.ui.ArchivedScreen
import net.brightroom.mindstock.frontend.feature.catalog.ui.CatalogOverlay
import net.brightroom.mindstock.frontend.feature.catalog.ui.ProductMasterScreen
import net.brightroom.mindstock.frontend.feature.catalog.ui.ProductSettingsSheet
import net.brightroom.mindstock.frontend.feature.household.NeedHouseholdViewModel
import net.brightroom.mindstock.frontend.feature.household.SettingsViewModel
import net.brightroom.mindstock.frontend.feature.household.data.HouseholdRepository
import net.brightroom.mindstock.frontend.feature.household.ui.CreateHouseholdSheet
import net.brightroom.mindstock.frontend.feature.household.ui.HouseholdSwitcher
import net.brightroom.mindstock.frontend.feature.household.ui.JoinCodeSheet
import net.brightroom.mindstock.frontend.feature.household.ui.NeedHouseholdScreen
import net.brightroom.mindstock.frontend.feature.inventory.InventoryUiState
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel
import net.brightroom.mindstock.frontend.feature.inventory.data.InventoryRepository
import net.brightroom.mindstock.frontend.feature.inventory.ui.DetailTarget
import net.brightroom.mindstock.frontend.feature.inventory.ui.InventoryRoute
import net.brightroom.mindstock.frontend.feature.inventory.ui.ProductDetailOverlay
import net.brightroom.mindstock.frontend.feature.notification.stockAlerts
import net.brightroom.mindstock.frontend.feature.notification.ui.NotifSheet
import net.brightroom.mindstock.frontend.feature.onboarding.OnboardingViewModel
import net.brightroom.mindstock.frontend.feature.onboarding.ui.OnboardingScreen
import net.brightroom.mindstock.frontend.feature.resident.data.ResidentRepository
import net.brightroom.mindstock.frontend.feature.shopping.ShoppingListViewModel
import net.brightroom.mindstock.frontend.feature.shopping.ui.ShoppingListScreen
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRegisterRpcService
import net.brightroom.mindstock.rpc.household.HouseholdRpcService
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.resident.ResidentRegisterRpcService
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
        val catalogRepository =
            remember {
                CatalogRepository(
                    catalogService = { rpc.service<CatalogRpcService>() },
                    productRegisterService = { rpc.service<ProductRegisterRpcService>() },
                    productService = { rpc.service<ProductRpcService>() },
                )
            }
        val residentRepository =
            remember { ResidentRepository(residentRegisterService = { rpc.service<ResidentRegisterRpcService>() }) }
        val householdRepository =
            remember {
                HouseholdRepository(
                    householdService = { rpc.service<HouseholdRpcService>() },
                    householdRegisterService = { rpc.service<HouseholdRegisterRpcService>() },
                )
            }
        val imageLoader = remember { ProductImageLoader(http, fetchUrl = { pid -> repository.imageUrl(pid) }) }

        Box(Modifier.fillMaxSize()) {
            when (state) {
                is AuthState.Booting -> {
                    AppText(stringResource(Res.string.loading))
                }

                is AuthState.Unauthenticated -> {
                    WelcomeScreen(
                        onSignIn = { scope.launch { deps.redirectToAuthorize() } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is AuthState.Failed -> {
                    val tokens = LocalMindstockTokens.current
                    AppText((state as AuthState.Failed).message.resolve(), color = tokens.statusOut)
                }

                is AuthState.NeedOnboarding -> {
                    val onbVm =
                        remember {
                            OnboardingViewModel(
                                registerDisplayName = residentRepository::register,
                                createHousehold = householdRepository::create,
                                flow = vm,
                                toast = toast,
                                reauth = reauth,
                            )
                        }
                    val onbState by onbVm.state.collectAsState()
                    OnboardingScreen(
                        state = onbState,
                        onName = onbVm::setName,
                        onHouseholdName = onbVm::setHouseholdName,
                        onNext = onbVm::next,
                        onBack = onbVm::back,
                        onSubmit = { scope.launch { onbVm.submit() } },
                        onSkip = {
                            onbVm.setHouseholdName("")
                            scope.launch { onbVm.submit() }
                        },
                        // 別アカウントでログイン: 既存の再認証導線(トークン破棄 → authorize)を再利用。
                        onCancel = { reauth.request() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is AuthState.NeedHousehold -> {
                    val nhVm =
                        remember {
                            NeedHouseholdViewModel(
                                createHousehold = householdRepository::create,
                                previewInvite = householdRepository::previewInvite,
                                joinByCode = householdRepository::join,
                                flow = vm,
                                toast = toast,
                                reauth = reauth,
                            )
                        }
                    val nhState by nhVm.state.collectAsState()
                    var sheet by remember { mutableStateOf<NeedHouseholdSheet?>(null) }
                    NeedHouseholdScreen(
                        onCreate = { sheet = NeedHouseholdSheet.Create },
                        onJoin = {
                            nhVm.clearPreview()
                            sheet = NeedHouseholdSheet.Join
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    CreateHouseholdSheet(
                        open = sheet == NeedHouseholdSheet.Create,
                        busy = nhState.busy,
                        onClose = { sheet = null },
                        onCreate = { name -> scope.launch { nhVm.create(name) } },
                    )
                    JoinCodeSheet(
                        open = sheet == NeedHouseholdSheet.Join,
                        state = nhState,
                        onClose = {
                            sheet = null
                            nhVm.clearPreview()
                        },
                        onCodeChange = { code ->
                            if (code.length == 6) scope.launch { nhVm.preview(code) } else nhVm.clearPreview()
                        },
                        onJoin = { code -> scope.launch { nhVm.join(code) } },
                    )
                }

                is AuthState.Ready -> {
                    CompositionLocalProvider(LocalProductImageLoader provides imageLoader) {
                        val householdId = sessionState.activeHouseholdId
                        if (householdId == null) {
                            AppText(stringResource(Res.string.need_household_title))
                        } else {
                            val owner =
                                isOwner(sessionState.households, sessionState.activeHouseholdId, sessionState.residentId)
                            // 各 content 関数で共有する状態は呼び出し側(ここ)に hoist する。
                            val openedState = remember { mutableStateOf<DetailTarget?>(null) }
                            val catalogOverlayState = remember { mutableStateOf<CatalogOverlay?>(null) }
                            val settingsSheetState = remember { mutableStateOf<SettingsSheet?>(null) }
                            // 世帯作成/参加 or 切替が成立して active が変わったら、開いていたシートを閉じる。
                            LaunchedEffect(householdId) { settingsSheetState.value = null }
                            // 設定タブ画面と世帯スイッチャの両方で使う単一 VM。
                            val settingsVm =
                                remember(householdId, sessionState.residentId) {
                                    SettingsViewModel(
                                        session = session,
                                        renameDisplayNameRpc = residentRepository::rename,
                                        renameHouseholdRpc = householdRepository::rename,
                                        changeRoleRpc = householdRepository::changeRole,
                                        removeMemberRpc = householdRepository::removeMember,
                                        leaveRpc = householdRepository::leave,
                                        createInviteRpc = householdRepository::createInvite,
                                        revokeInviteRpc = householdRepository::revokeInvite,
                                        flow = vm,
                                        toast = toast,
                                        reauth = reauth,
                                    )
                                }
                            // Master/Settings 両オーバーレイで共有する単一 VM(二重生成を避ける)。
                            val productMasterVm =
                                remember(householdId) {
                                    ProductMasterViewModel(
                                        householdId = householdId,
                                        loadStocks = repository::list,
                                        changeUnitOf = catalogRepository::changeUnit,
                                        changeMinimumOf = catalogRepository::changeMinimum,
                                        archiveProduct = catalogRepository::archive,
                                        uploadImageOf = repository::uploadImage,
                                        removeImageOf = repository::removeImage,
                                        invalidateImage = imageLoader::invalidate,
                                        refresh = refresh,
                                        toast = toast,
                                        reauth = reauth,
                                    )
                                }
                            ReadyContent(
                                householdId = householdId,
                                sessionState = sessionState,
                                settingsVm = settingsVm,
                                repository = repository,
                                refresh = refresh,
                                toast = toast,
                                reauth = reauth,
                                scope = scope,
                                opened = openedState,
                                catalogOverlay = catalogOverlayState,
                                settingsSheet = settingsSheetState,
                            )
                            HouseholdSheets(
                                householdId = householdId,
                                authFlow = vm,
                                householdRepository = householdRepository,
                                settingsVm = settingsVm,
                                toast = toast,
                                reauth = reauth,
                                scope = scope,
                                settingsSheet = settingsSheetState,
                            )
                            ProductDetailOverlayContent(
                                householdId = householdId,
                                owner = owner,
                                repository = repository,
                                refresh = refresh,
                                toast = toast,
                                reauth = reauth,
                                opened = openedState,
                                catalogOverlay = catalogOverlayState,
                            )
                            CatalogOverlayContent(
                                householdId = householdId,
                                sessionState = sessionState,
                                owner = owner,
                                productMasterVm = productMasterVm,
                                catalogRepository = catalogRepository,
                                refresh = refresh,
                                toast = toast,
                                reauth = reauth,
                                scope = scope,
                                opened = openedState,
                                catalogOverlay = catalogOverlayState,
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

/** load() の初回実行と refresh シグナル購読での再 load をまとめる定型ヘルパー。 */
@Composable
private fun LoadWithRefresh(
    key: Any?,
    refresh: InventoryRefreshController,
    load: suspend () -> Unit,
) {
    LaunchedEffect(key) { load() }
    LaunchedEffect(refresh) { refresh.signal.collect { load() } }
}

/**
 * 在庫/買い物/活動/設定の 4 タブシェル。各タブ用 VM を生成し配線する。
 * 状態([opened] / [catalogOverlay] / [settingsSheet])は呼び出し側で hoist されたものを受ける。
 * 商品詳細オーバーレイは描画順保持のため呼び出し側で別途配置する([ProductDetailOverlayContent])。
 */
@Composable
private fun ReadyContent(
    householdId: HouseholdId,
    sessionState: AppSession.State,
    settingsVm: SettingsViewModel,
    repository: InventoryRepository,
    refresh: InventoryRefreshController,
    toast: ToastController,
    reauth: ReauthController,
    scope: CoroutineScope,
    opened: MutableState<DetailTarget?>,
    catalogOverlay: MutableState<CatalogOverlay?>,
    settingsSheet: MutableState<SettingsSheet?>,
) {
    val homeVm =
        remember(householdId) {
            InventoryViewModel(
                householdId = householdId,
                loadStocks = repository::list,
                loadShoppingList = repository::shoppingList,
                replenishStock = repository::replenish,
                consumeStock = repository::consume,
                refresh = refresh,
                toast = toast,
                reauth = reauth,
            )
        }
    val homeState by homeVm.state.collectAsState()
    // ベルのバッジ/シートを全タブで正しくするため、Stock タブに入る前に在庫をロードする。
    LaunchedEffect(householdId) { homeVm.load() }
    // Shop/Activity 等 Stock タブ以外にいても在庫変更でバッジが更新されるよう、
    // InventoryRoute と同じ refresh シグナルをここでも購読する(ベルは全タブ表示のため)。
    LaunchedEffect(refresh) { refresh.signal.collect { homeVm.load() } }
    val alerts =
        (homeState as? InventoryUiState.Content)
            ?.let { stockAlerts(it.stocks, EvaluatedTime.now()) }
            ?: emptyList()
    var notifOpen by remember(householdId) { mutableStateOf(false) }
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
    var selectedTab by remember { mutableStateOf(Tab.Stock) }
    val shellHousehold =
        sessionState.households?.list?.firstOrNull { it.id == householdId }
    AppShell(
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        onAdd = { catalogOverlay.value = CatalogOverlay.AddProduct },
        onOpenSwitcher = { settingsSheet.value = SettingsSheet.Switcher },
        onBell = { notifOpen = true },
        displayName = sessionState.displayName?.invoke() ?: "",
        householdName = shellHousehold?.profile?.name?.invoke() ?: "",
        stockContent = {
            InventoryRoute(
                homeViewModel = homeVm,
                refresh = refresh,
                onOpenProduct = { pid, seed -> opened.value = DetailTarget(pid, seed) },
                onAddProduct = { catalogOverlay.value = CatalogOverlay.AddProduct },
                displayName = sessionState.displayName?.invoke() ?: "",
                householdName = shellHousehold?.profile?.name?.invoke() ?: "",
                memberCount = shellHousehold?.members?.size() ?: 1,
                onShop = { selectedTab = Tab.Shop },
                onOpenSettings = { selectedTab = Tab.Profile },
                onBell = { notifOpen = true },
                hasAlerts = alerts.isNotEmpty(),
            )
        },
        shopContent = {
            val shopState by shopVm.state.collectAsState()
            LoadWithRefresh(shopVm, refresh) { shopVm.load() }
            ShoppingListScreen(
                state = shopState,
                onOpenProduct = { pid, seed -> opened.value = DetailTarget(pid, seed) },
                onSetWanted = { pid, w -> scope.launch { shopVm.setWanted(pid, w) } },
                onReplenish = { pid, q, n ->
                    scope.launch { shopVm.replenish(pid, Quantity(q), Note(n), OccurredAt.now()) }
                },
            )
        },
        activityContent = {
            val activityState by activityVm.state.collectAsState()
            LoadWithRefresh(activityVm, refresh) { activityVm.load() }
            ActivityScreen(
                state = activityState,
                onOpenProduct = { pid -> opened.value = DetailTarget(pid, null) },
            )
        },
        profileContent = {
            val sState by settingsVm.state.collectAsState()
            SettingsScreen(
                state = sState,
                onRenameDisplayName = { scope.launch { settingsVm.renameDisplayName(it) } },
                onRenameHousehold = { scope.launch { settingsVm.renameHousehold(it) } },
                onChangeRole = { t, r -> scope.launch { settingsVm.changeRole(t, r) } },
                onRemoveMember = { scope.launch { settingsVm.removeMember(it) } },
                onLeave = { scope.launch { settingsVm.leave() } },
                onIssueInvite = { scope.launch { settingsVm.createInvite(it) } },
                onRevokeInvite = { scope.launch { settingsVm.revokeInvite() } },
                onOpenMaster = { catalogOverlay.value = CatalogOverlay.Master },
                onOpenArchived = { catalogOverlay.value = CatalogOverlay.Archived },
                onOpenSwitcher = { settingsSheet.value = SettingsSheet.Switcher },
                onLogout = { reauth.request() },
            )
        },
    )
    NotifSheet(
        open = notifOpen,
        alerts = alerts,
        onClose = { notifOpen = false },
        onOpen = { stock ->
            // シートを閉じるのは onClose の責務(AlertRow が onOpen の前に onClose を呼ぶ)。
            opened.value = DetailTarget(stock.product.id, stock)
        },
    )
}

/**
 * 商品詳細オーバーレイ。元の描画順(AppShell → 世帯シート → 商品詳細 → catalog)を保つため、
 * 呼び出し側で [HouseholdSheets] の後・[CatalogOverlayContent] の前に配置する。
 */
@Composable
private fun ProductDetailOverlayContent(
    householdId: HouseholdId,
    owner: Boolean,
    repository: InventoryRepository,
    refresh: InventoryRefreshController,
    toast: ToastController,
    reauth: ReauthController,
    opened: MutableState<DetailTarget?>,
    catalogOverlay: MutableState<CatalogOverlay?>,
) {
    val target = opened.value
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
            onBack = { opened.value = null },
            onOpenSettings =
                if (owner) {
                    { target.seed?.let { catalogOverlay.value = CatalogOverlay.Settings(it) } }
                } else {
                    null
                },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 世帯スイッチャ・世帯作成・参加コードの 3 シート。切替/作成/参加用の [NeedHouseholdViewModel] を生成し、
 * スイッチャの一覧と切替は [settingsVm] を使う。シート開閉状態は呼び出し側で hoist された [settingsSheet]。
 */
@Composable
private fun HouseholdSheets(
    householdId: HouseholdId,
    authFlow: AuthViewModel,
    householdRepository: HouseholdRepository,
    settingsVm: SettingsViewModel,
    toast: ToastController,
    reauth: ReauthController,
    scope: CoroutineScope,
    settingsSheet: MutableState<SettingsSheet?>,
) {
    val settingsHhVm =
        remember(householdId) {
            NeedHouseholdViewModel(
                createHousehold = householdRepository::create,
                previewInvite = householdRepository::previewInvite,
                joinByCode = householdRepository::join,
                flow = authFlow,
                toast = toast,
                reauth = reauth,
            )
        }
    val settingsHhState by settingsHhVm.state.collectAsState()
    HouseholdSwitcher(
        open = settingsSheet.value == SettingsSheet.Switcher,
        households = settingsVm.state.value.households,
        onClose = { settingsSheet.value = null },
        onChoose = { id ->
            settingsVm.switchHousehold(id)
            settingsSheet.value = null
        },
        onCreate = { settingsSheet.value = SettingsSheet.Create },
        onJoin = {
            settingsHhVm.clearPreview()
            settingsSheet.value = SettingsSheet.Join
        },
    )
    CreateHouseholdSheet(
        open = settingsSheet.value == SettingsSheet.Create,
        busy = settingsHhState.busy,
        onClose = { settingsSheet.value = null },
        onCreate = { name -> scope.launch { settingsHhVm.create(name) } },
    )
    JoinCodeSheet(
        open = settingsSheet.value == SettingsSheet.Join,
        state = settingsHhState,
        onClose = {
            settingsSheet.value = null
            settingsHhVm.clearPreview()
        },
        onCodeChange = { code ->
            if (code.length == 6) scope.launch { settingsHhVm.preview(code) } else settingsHhVm.clearPreview()
        },
        onJoin = { code -> scope.launch { settingsHhVm.join(code) } },
    )
}

/**
 * 商品追加/マスタ/アーカイブ/設定シートのオーバーレイ群。表示中の種別は [catalogOverlay] で切り替わる。
 * Master/Settings は呼び出し側で単一生成された [productMasterVm] を共有する。
 */
@Composable
private fun CatalogOverlayContent(
    householdId: HouseholdId,
    sessionState: AppSession.State,
    owner: Boolean,
    productMasterVm: ProductMasterViewModel,
    catalogRepository: CatalogRepository,
    refresh: InventoryRefreshController,
    toast: ToastController,
    reauth: ReauthController,
    scope: CoroutineScope,
    opened: MutableState<DetailTarget?>,
    catalogOverlay: MutableState<CatalogOverlay?>,
) {
    when (val ov = catalogOverlay.value) {
        null -> {
            Unit
        }

        is CatalogOverlay.AddProduct -> {
            val addVm =
                remember(householdId) {
                    AddProductViewModel(
                        searchCatalog = catalogRepository::search,
                        lookupJan = catalogRepository::lookupByJan,
                        adoptProduct = { id, u, m -> catalogRepository.adopt(householdId, id, u, m) },
                        addCustomProduct = { req -> catalogRepository.addCustom(householdId, req) },
                        refresh = refresh,
                        toast = toast,
                        reauth = reauth,
                    )
                }
            val addState by addVm.state.collectAsState()
            LaunchedEffect(addState) {
                if (addState is AddProductUiState.Done) catalogOverlay.value = null
            }
            AddProductScreen(
                state = addState,
                onQuery = { scope.launch { addVm.search(it) } },
                onLookupJan = { scope.launch { addVm.lookupByJan(it) } },
                onPickCatalog = { addVm.pickCatalog(it) },
                onPickCustom = { addVm.pickCustom(it) },
                onBack = {
                    if (addState is AddProductUiState.Browsing) {
                        catalogOverlay.value = null
                    } else {
                        addVm.backToBrowsing()
                    }
                },
                onAdopt = { item, u, m -> scope.launch { addVm.adopt(item, u, m) } },
                onAddCustom = { name, jan, u, m -> scope.launch { addVm.addCustom(name, jan, u, m) } },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is CatalogOverlay.Master -> {
            val mState by productMasterVm.state.collectAsState()
            var settingsStock by remember { mutableStateOf<Stock?>(null) }
            LoadWithRefresh(productMasterVm, refresh) { productMasterVm.load() }
            ProductMasterScreen(
                state = mState,
                householdName = activeHouseholdName(sessionState),
                onBack = { catalogOverlay.value = null },
                onAdd = { catalogOverlay.value = CatalogOverlay.AddProduct },
                onSelect = { settingsStock = it },
                modifier = Modifier.fillMaxSize(),
            )
            ProductSettingsSheetWithImage(
                open = settingsStock != null,
                stock = settingsStock,
                viewModel = productMasterVm,
                onClose = { settingsStock = null },
                onChangeUnit = { u ->
                    settingsStock?.let { s ->
                        scope.launch { productMasterVm.changeUnit(s.product.id, u) }
                    }
                },
                onChangeMinimum = { m ->
                    settingsStock?.let { s ->
                        scope.launch { productMasterVm.changeMinimum(s.product.id, m) }
                    }
                },
                onArchive = {
                    settingsStock?.let { s -> scope.launch { productMasterVm.archive(s.product.id) } }
                    settingsStock = null
                },
            )
        }

        is CatalogOverlay.Archived -> {
            val archVm =
                remember(householdId) {
                    ArchivedViewModel(
                        householdId = householdId,
                        loadArchived = catalogRepository::listArchived,
                        unarchiveProduct = catalogRepository::unarchive,
                        refresh = refresh,
                        toast = toast,
                        reauth = reauth,
                    )
                }
            val aState by archVm.state.collectAsState()
            LoadWithRefresh(archVm, refresh) { archVm.load() }
            ArchivedScreen(
                state = aState,
                householdName = activeHouseholdName(sessionState),
                canRestore = owner,
                onBack = { catalogOverlay.value = null },
                onRestore = { pid -> scope.launch { archVm.unarchive(pid) } },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is CatalogOverlay.Settings -> {
            ProductSettingsSheetWithImage(
                open = true,
                stock = ov.stock,
                viewModel = productMasterVm,
                onClose = { catalogOverlay.value = null },
                onChangeUnit = { u ->
                    scope.launch { productMasterVm.changeUnit(ov.stock.product.id, u) }
                },
                onChangeMinimum = { m ->
                    scope.launch { productMasterVm.changeMinimum(ov.stock.product.id, m) }
                },
                onArchive = {
                    scope.launch { productMasterVm.archive(ov.stock.product.id) }
                    catalogOverlay.value = null
                    // 詳細(歯車)起点のアーカイブ。詳細オーバーレイも閉じないと
                    // アーカイブ済み商品に補充/消費できてしまう。
                    opened.value = null
                },
            )
        }
    }
}

/**
 * [ProductSettingsSheet] に画像欄を配線したラッパ。RPC オーケストレーション(ピッカー起動/アップロード/
 * 削除・loader 無効化・一覧 refresh・エラー処理)と再入抑止(busy)は [ProductMasterViewModel] が持つ。
 * 楽観表示フラグ([stored])は「今開いているシートの productId キーの view state」なのでここでローカルに同期
 * 保持し(共有 VM 由来の stale を避ける)、VM の成否 Boolean を受けて更新する。
 */
@Composable
private fun ProductSettingsSheetWithImage(
    open: Boolean,
    stock: Stock?,
    viewModel: ProductMasterViewModel,
    onClose: () -> Unit,
    onChangeUnit: (ProductUnit) -> Unit,
    onChangeMinimum: (MinimumStock) -> Unit,
    onArchive: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val productId = stock?.product?.id
    // 楽観表示フラグ。productId キーで stock の現状から同期シードする(初フレームから正しい表示)。
    var stored by remember(productId) { mutableStateOf(stock?.product?.image is ProductImage.Stored) }
    val image = if (productId != null) rememberProductThumbnail(productId, stored) else null

    ProductSettingsSheet(
        open = open,
        stock = stock,
        onClose = onClose,
        onChangeUnit = onChangeUnit,
        onChangeMinimum = onChangeMinimum,
        onArchive = onArchive,
        image = image,
        onPickImage = {
            val id = productId ?: return@ProductSettingsSheet
            scope.launch { if (viewModel.pickAndUploadImage(id)) stored = true }
        },
        onRemoveImage = {
            val id = productId ?: return@ProductSettingsSheet
            scope.launch { if (viewModel.removeImageFor(id)) stored = false }
        },
    )
}

private fun activeHouseholdName(s: AppSession.State): String =
    s.households
        ?.list
        ?.firstOrNull { it.id == s.activeHouseholdId }
        ?.profile
        ?.name
        ?.invoke() ?: ""

private enum class NeedHouseholdSheet { Create, Join }

private enum class SettingsSheet { Switcher, Create, Join }
