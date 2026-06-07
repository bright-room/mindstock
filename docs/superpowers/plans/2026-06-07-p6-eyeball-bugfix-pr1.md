# P6 Eyeball Bugfix PR1 (header household wiring + ProductDetail opacity) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the two real bugs found in the frontend eyeball: (#1) the inventory header household pill shows a hardcoded "わたしの家 / 1人" instead of the active household, and (#2) the ProductDetail overlay renders with a transparent background so the screen behind bleeds through.

**Architecture:** #1 threads the active household name + member count from `AppSession.state` into `StockHomeScreen` as plain params, mirroring the existing `displayName` seam (derived inline in `App.kt`, same as `SettingsViewModel` derives them). #2 gives `ProductDetailScreen`'s root `Column` an opaque `.background(tokens.surface)`, matching the catalog overlay screens.

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform (Wasm). Frontend module `:frontend`.

**Testing note:** Both changes are pure UI wiring with no new logic, so per the repo testing rule (logic-only `commonTest`, no UI-render tests) no unit tests are added. Verification is visual against the already-running dev server (`:8080`, hot-reload) via the headed Chromium CDP session.

---

### Task 1: Wire active household name + member count into the inventory header (#1)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/InventoryRoute.kt`
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: Add params to `StockHomeScreen` and use them in the pill**

In `StockHomeScreen.kt`, change the function signature to add the two params after `displayName`:

```kotlin
@Composable
fun StockHomeScreen(
    state: InventoryUiState,
    displayName: String = "",
    householdName: String = "",
    memberCount: Int = 1,
    onSelectView: (StockView) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpen: (Stock) -> Unit,
    onReplenish: (Stock) -> Unit,
    onConsume: (Stock) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Replace the hardcoded pill block (currently):

```kotlin
                    // 世帯名/人数は P6-1b で session から渡す。本パスは既定文言で表示のみ。
                    HouseholdPill(
                        name = stringResource(Res.string.household_default_name),
                        memberCount = 1,
                        onClick = {},
                    )
```

with:

```kotlin
                    HouseholdPill(
                        name = householdName,
                        memberCount = memberCount,
                        onClick = {},
                    )
```

Then remove the now-unused import line:

```kotlin
import mindstock.frontend.generated.resources.household_default_name
```

(Leave the `<string name="household_default_name">` resource in `strings.xml` — harmless, out of scope.)

- [ ] **Step 2: Pass the params through `InventoryRoute`**

In `InventoryRoute.kt`, add the two params to the signature after `displayName`:

```kotlin
fun InventoryRoute(
    homeViewModel: InventoryViewModel,
    refresh: InventoryRefreshController,
    onOpenProduct: (ProductId, Stock?) -> Unit,
    onAddProduct: () -> Unit,
    displayName: String = "",
    householdName: String = "",
    memberCount: Int = 1,
    modifier: Modifier = Modifier,
) {
```

And forward them in the `StockHomeScreen(...)` call (add after `displayName = displayName,`):

```kotlin
        displayName = displayName,
        householdName = householdName,
        memberCount = memberCount,
```

- [ ] **Step 3: Derive and pass from `App.kt`**

In `App.kt`, inside the `AuthState.Ready` branch where `householdId` is already in scope (the `InventoryRoute(...)` call near line 313), derive the active household and pass the two params. The `InventoryRoute` call becomes:

```kotlin
                            stockContent = {
                                val activeHousehold =
                                    sessionState.households?.list?.firstOrNull { it.id == householdId }
                                InventoryRoute(
                                    homeViewModel = homeVm,
                                    refresh = refresh,
                                    onOpenProduct = { pid, seed -> opened = DetailTarget(pid, seed) },
                                    onAddProduct = { catalogOverlay = CatalogOverlay.AddProduct },
                                    displayName = sessionState.displayName?.invoke() ?: "",
                                    householdName = activeHousehold?.profile?.name?.invoke() ?: "",
                                    memberCount = activeHousehold?.members?.size() ?: 1,
                                )
                            },
```

- [ ] **Step 4: Compile-check**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL (no unresolved-reference for `household_default_name`, no signature mismatch).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/InventoryRoute.kt \
        frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "fix(frontend): 在庫ヘッダの世帯名/人数をアクティブ世帯から表示"
```

---

### Task 2: Give the ProductDetail overlay an opaque background (#2)

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt`

- [ ] **Step 1: Add the `background` import**

Add to the import block (alphabetical, near the other `androidx.compose.foundation.layout.*` imports — `background` lives under `androidx.compose.foundation`):

```kotlin
import androidx.compose.foundation.background
```

- [ ] **Step 2: Hoist `tokens` above the root Column and paint the background**

Currently `val tokens = LocalMindstockTokens.current` is declared at line 84 (inside the Column, after the null-guard), so it is not in scope at the root Column modifier (line 75). Move it up: insert it right after the `wanted` val (line 73) and before the `Column(...)`, and **delete the duplicate declaration at line 84**.

After the change, lines 71-85 read:

```kotlin
    // ヘッダの Stock: Content があればそれ、無ければ seed
    val stock: Stock? = (detail as? ProductDetailUiState.Content)?.stock ?: seed
    val wanted: Boolean? = (detail as? ProductDetailUiState.Content)?.wanted
    val tokens = LocalMindstockTokens.current

    Column(
        modifier = modifier.fillMaxSize().background(tokens.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (stock == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(AppIconName.Back, contentDescription = "back", onClick = onBack)
            }
            if (detail is ProductDetailUiState.Error) AppText(detail.text.resolve()) else AppText(stringResource(Res.string.loading))
            return@Column
        }

        val statusColor =
```

(Note: `val tokens = LocalMindstockTokens.current` that was at line 84 is removed; `statusColor` now uses the hoisted `tokens`.)

- [ ] **Step 3: Compile-check**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL (no duplicate `tokens`, no unresolved `background`).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt
git commit -m "fix(frontend): ProductDetail オーバーレイに不透明背景を付与し透過を解消"
```

---

### Task 3: spotless + visual verification on the running dev server

**Files:** none (verification only)

- [ ] **Step 1: spotless**

Run: `./gradlew :frontend:spotlessApply && ./gradlew :frontend:spotlessCheck`
Expected: BUILD SUCCESSFUL. If `spotlessApply` changed files, amend them into the relevant commit.

- [ ] **Step 2: Let the dev server rebuild**

The dev server (`:frontend:wasmJsBrowserDevelopmentRun`) on `:8080` watches sources and recompiles. Confirm `/tmp/mindstock-frontend.log` shows a fresh `webpack ... compiled` line after the edits.

- [ ] **Step 3: Verify #1 in the browser (CDP on :9222)**

Drive the headed Chromium: rename the household in 設定 (or switch household) and confirm the 在庫 header pill reflects the real name + member count (no longer stuck on "わたしの家 / 1人"). Capture a screenshot.

- [ ] **Step 4: Verify #2 in the browser**

Open a product's detail overlay from the 在庫 list and confirm the background is opaque (the stock list no longer bleeds through). Capture a screenshot.

- [ ] **Step 5: Final gate**

Run: `./gradlew :frontend:compileKotlinWasmJs :frontend:compileKotlinJs :frontend:spotlessCheck`
Expected: all BUILD SUCCESSFUL.
