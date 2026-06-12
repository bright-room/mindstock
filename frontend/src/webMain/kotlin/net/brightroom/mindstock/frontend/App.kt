package net.brightroom.mindstock.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import mindstock.frontend.generated.resources.loading
import mindstock.frontend.generated.resources.need_household_title
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
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
import net.brightroom.mindstock.frontend.feature.inventory.InventoryViewModel
import net.brightroom.mindstock.frontend.feature.inventory.ProductDetailViewModel
import net.brightroom.mindstock.frontend.feature.inventory.data.InventoryRepository
import net.brightroom.mindstock.frontend.feature.inventory.ui.DetailTarget
import net.brightroom.mindstock.frontend.feature.inventory.ui.InventoryRoute
import net.brightroom.mindstock.frontend.feature.inventory.ui.ProductDetailOverlay
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
                            var opened by remember { mutableStateOf<DetailTarget?>(null) }
                            var catalogOverlay by remember { mutableStateOf<CatalogOverlay?>(null) }
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
                            var settingsSheet by remember { mutableStateOf<SettingsSheet?>(null) }
                            val settingsHhVm =
                                remember(householdId) {
                                    NeedHouseholdViewModel(
                                        createHousehold = householdRepository::create,
                                        previewInvite = householdRepository::previewInvite,
                                        joinByCode = householdRepository::join,
                                        flow = vm,
                                        toast = toast,
                                        reauth = reauth,
                                    )
                                }
                            val settingsHhState by settingsHhVm.state.collectAsState()
                            // 世帯作成/参加 or 切替が成立して active が変わったら、開いていたシートを閉じる。
                            LaunchedEffect(householdId) { settingsSheet = null }
                            var selectedTab by remember { mutableStateOf(Tab.Stock) }
                            val shellHousehold =
                                sessionState.households?.list?.firstOrNull { it.id == householdId }
                            AppShell(
                                selectedTab = selectedTab,
                                onSelectTab = { selectedTab = it },
                                onAdd = { catalogOverlay = CatalogOverlay.AddProduct },
                                onOpenSwitcher = { settingsSheet = SettingsSheet.Switcher },
                                onBell = {},
                                displayName = sessionState.displayName?.invoke() ?: "",
                                householdName = shellHousehold?.profile?.name?.invoke() ?: "",
                                stockContent = {
                                    InventoryRoute(
                                        homeViewModel = homeVm,
                                        refresh = refresh,
                                        onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
                                        onAddProduct = { catalogOverlay = CatalogOverlay.AddProduct },
                                        displayName = sessionState.displayName?.invoke() ?: "",
                                        householdName = shellHousehold?.profile?.name?.invoke() ?: "",
                                        memberCount = shellHousehold?.members?.size() ?: 1,
                                        onShop = { selectedTab = Tab.Shop },
                                        onOpenSettings = { selectedTab = Tab.Profile },
                                    )
                                },
                                shopContent = {
                                    val shopState by shopVm.state.collectAsState()
                                    LoadWithRefresh(shopVm, refresh) { shopVm.load() }
                                    ShoppingListScreen(
                                        state = shopState,
                                        onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
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
                                        onOpenProduct = { pid -> opened = DetailTarget(pid, null) },
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
                                        onOpenMaster = { catalogOverlay = CatalogOverlay.Master },
                                        onOpenArchived = { catalogOverlay = CatalogOverlay.Archived },
                                        onOpenSwitcher = { settingsSheet = SettingsSheet.Switcher },
                                        onLogout = { reauth.request() },
                                    )
                                },
                            )
                            HouseholdSwitcher(
                                open = settingsSheet == SettingsSheet.Switcher,
                                households = settingsVm.state.value.households,
                                onClose = { settingsSheet = null },
                                onChoose = { id ->
                                    settingsVm.switchHousehold(id)
                                    settingsSheet = null
                                },
                                onCreate = { settingsSheet = SettingsSheet.Create },
                                onJoin = {
                                    settingsHhVm.clearPreview()
                                    settingsSheet = SettingsSheet.Join
                                },
                            )
                            CreateHouseholdSheet(
                                open = settingsSheet == SettingsSheet.Create,
                                busy = settingsHhState.busy,
                                onClose = { settingsSheet = null },
                                onCreate = { name -> scope.launch { settingsHhVm.create(name) } },
                            )
                            JoinCodeSheet(
                                open = settingsSheet == SettingsSheet.Join,
                                state = settingsHhState,
                                onClose = {
                                    settingsSheet = null
                                    settingsHhVm.clearPreview()
                                },
                                onCodeChange = { code ->
                                    if (code.length == 6) scope.launch { settingsHhVm.preview(code) } else settingsHhVm.clearPreview()
                                },
                                onJoin = { code -> scope.launch { settingsHhVm.join(code) } },
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
                                    onOpenSettings =
                                        if (owner) {
                                            { target.seed?.let { catalogOverlay = CatalogOverlay.Settings(it) } }
                                        } else {
                                            null
                                        },
                                    modifier = Modifier.fillMaxSize(),
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
                            when (val ov = catalogOverlay) {
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
                                        if (addState is AddProductUiState.Done) catalogOverlay = null
                                    }
                                    AddProductScreen(
                                        state = addState,
                                        onQuery = { scope.launch { addVm.search(it) } },
                                        onLookupJan = { scope.launch { addVm.lookupByJan(it) } },
                                        onPickCatalog = { addVm.pickCatalog(it) },
                                        onPickCustom = { addVm.pickCustom(it) },
                                        onBack = {
                                            if (addState is AddProductUiState.Browsing) {
                                                catalogOverlay = null
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
                                        onBack = { catalogOverlay = null },
                                        onAdd = { catalogOverlay = CatalogOverlay.AddProduct },
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
                                        onBack = { catalogOverlay = null },
                                        onRestore = { pid -> scope.launch { archVm.unarchive(pid) } },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                is CatalogOverlay.Settings -> {
                                    ProductSettingsSheetWithImage(
                                        open = true,
                                        stock = ov.stock,
                                        viewModel = productMasterVm,
                                        onClose = { catalogOverlay = null },
                                        onChangeUnit = { u ->
                                            scope.launch { productMasterVm.changeUnit(ov.stock.product.id, u) }
                                        },
                                        onChangeMinimum = { m ->
                                            scope.launch { productMasterVm.changeMinimum(ov.stock.product.id, m) }
                                        },
                                        onArchive = {
                                            scope.launch { productMasterVm.archive(ov.stock.product.id) }
                                            catalogOverlay = null
                                        },
                                    )
                                }
                            }
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
 * [ProductSettingsSheet] に画像欄を配線したラッパ。画像状態(楽観表示フラグ・再入抑止)と RPC
 * オーケストレーション(ピッカー起動/アップロード/削除・loader 無効化・一覧 refresh・エラー処理)は
 * [ProductMasterViewModel] が持ち、ここは VM の状態を購読して描画とイベント委譲に徹する表示専任。
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
    val stored by viewModel.imageStored.collectAsState()
    // 表示対象が変わったら楽観フラグを stock の現状で初期化する。
    LaunchedEffect(productId) { viewModel.beginImageEdit(stock) }
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
            scope.launch { viewModel.pickAndUploadImage(id) }
        },
        onRemoveImage = {
            val id = productId ?: return@ProductSettingsSheet
            scope.launch { viewModel.removeImageFor(id) }
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
