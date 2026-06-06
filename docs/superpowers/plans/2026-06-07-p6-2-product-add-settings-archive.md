# P6-2 商品追加 / 設定 / アーカイブ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 商品をカタログから採用/カスタム追加し、単位・最低在庫を設定し、アーカイブ/復元する frontend フローを既存 RPC に配線する。

**Architecture:** 新 feature `feature/catalog` を切る(`CatalogRepository` + 3 ViewModel + UI)。フルスクリーン overlay は `ProductDetailOverlay` と同様 app 層(`App.kt`)が state 駆動で重ねる。owner 権限は `session.households` から算出し UX ゲートに使う(サーバも強制)。画像設定・カメラスキャナは見送り(spec 参照)。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform(Wasm)/ kotlinx-rpc / androidx.lifecycle.ViewModel / Material3 Expressive(designsystem atom 経由)/ kotlin.test + Kotest assertions。

**Spec:** `docs/superpowers/specs/2026-06-07-p6-2-product-add-settings-archive-design.md`
**Mock:** `docs/ref/mindstock.zip` の `app/screens-b.jsx`(`AddProduct` L337-570)/ `app/screens-master.jsx`(`UnitPicker` L7-27, `MasterItemSheet` L57-116, `MasterScreen` L123-176, `ArchivedScreen` L179-220)/ `app/screens-c.jsx`(`EmptyState` L161-172)。

---

## File Structure

新規:
- `feature/catalog/data/CatalogRepository.kt` — `CatalogRpcService` + `ProductRegisterRpcService` + `ProductRpcService.listArchived` をラップし `RpcOutcome` を返す
- `feature/catalog/AddProductUiState.kt` / `AddProductViewModel.kt` — 検索/JAN照会/採用・カスタム分岐
- `feature/catalog/ProductMasterUiState.kt` / `ProductMasterViewModel.kt` — 採用中一覧 + 設定保存(changeUnit/changeMinimum/archive)
- `feature/catalog/ArchivedUiState.kt` / `ArchivedViewModel.kt` — listArchived / unarchive
- `feature/catalog/ui/UnitPicker.kt` — 単位チップ + 自由入力(feature-local composable)
- `feature/catalog/ui/AddProductScreen.kt` — フルスクリーン overlay(検索 → フォーム)
- `feature/catalog/ui/ProductSettingsSheet.kt` — 設定 Sheet(単位/最低在庫/アーカイブ)
- `feature/catalog/ui/ProductMasterScreen.kt` — フルスクリーン overlay(一覧)
- `feature/catalog/ui/ArchivedScreen.kt` — フルスクリーン overlay(アーカイブ一覧 + 復元)
- `feature/catalog/ui/CatalogOverlay.kt` — overlay 種別 sealed + app 層 host composable
- `app/Ownership.kt` — owner 算出 pure 関数
- `app/profile/ProfileScreen.kt` — 最小 Profile タブ(商品マスタ / アーカイブの 2 行)
- `designsystem/atom/EmptyState.kt` — 空状態 atom

変更:
- `designsystem/atom/AppIcon.kt` — `Settings` / `Barcode` / `Archive` / `Restore` / `Pencil` アイコン追加
- `feature/inventory/ui/ProductDetailScreen.kt` — owner 用設定歯車を追加(`onOpenSettings: (() -> Unit)?`)
- `feature/inventory/ui/ProductDetailOverlay.kt` — `onOpenSettings` を中継
- `app/shell/AppShell.kt` — `profileContent` 引数を受け placeholder を置換
- `webMain/.../App.kt` — CatalogRepository / 3 VM / overlay 状態 / Profile / isOwner を配線
- `commonMain/composeResources/values/strings.xml` — 文言追加

---

## Task 1: 文言・アイコン・EmptyState atom(基盤)

**Files:**
- Modify: `frontend/src/commonMain/composeResources/values/strings.xml`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/EmptyState.kt`

- [ ] **Step 1: 文言を strings.xml に追加**

`</resources>` の直前に追加:

```xml
    <!-- P6-2 商品追加 -->
    <string name="add_product_title">商品を追加</string>
    <string name="add_product_search_placeholder">商品名 または JANコードで検索</string>
    <string name="add_product_search_hint">商品マスタから選ぶか、見つからない商品はその場で在庫に追加できます。単位や最低在庫はあとから変えられます。</string>
    <string name="add_product_jan_hint">JANコード「%1$s」で商品を検索しています。見つからない場合も、その場で在庫に追加できます。</string>
    <string name="add_product_jan_lookup">JANコード「%1$s」で商品を探す</string>
    <string name="add_product_custom_add">「%1$s」を世帯に追加</string>
    <string name="add_product_adopted_badge">採用済み</string>
    <string name="add_product_already_jan">このJANコードの商品はすでに在庫にあります。</string>
    <string name="add_product_adopt_title">世帯に採用する</string>
    <string name="add_product_custom_title">世帯に追加する</string>
    <string name="add_product_name_label">商品名</string>
    <string name="add_product_name_placeholder">商品名（例: 麦茶パック）</string>
    <string name="add_product_name_locked_master">商品マスタに登録された商品名です。</string>
    <string name="add_product_name_locked_jan">JANコードから取得した商品名を使用します。</string>
    <string name="add_product_name_editable">商品名は自由に変更できます。</string>
    <string name="add_product_unit_label">数える単位</string>
    <string name="add_product_unit_other">その他（自由入力）</string>
    <string name="add_product_min_label">最低在庫</string>
    <string name="add_product_min_caption">これ以下で買い物リストへ</string>
    <string name="add_product_submit_adopt">この世帯に採用する</string>
    <string name="add_product_submit_custom">この世帯に追加する</string>
    <string name="add_product_image_note">画像は追加後、商品マスタの編集から設定できます。</string>
    <string name="add_product_loading_jan">JANコードから商品情報を取得中…</string>
    <!-- P6-2 商品マスタ / 設定 -->
    <string name="master_title">商品マスタ</string>
    <string name="master_subtitle">%1$s · %2$d品目</string>
    <string name="master_add">商品を追加</string>
    <string name="master_hint">各商品の単位と最低在庫を設定できます。タップして編集。</string>
    <string name="master_empty_title">まだ商品がありません</string>
    <string name="master_empty_sub">「商品を追加」から大元のマスタを選んで採用しましょう。</string>
    <string name="master_row_meta">単位: %1$s · 在庫 %2$d%3$s</string>
    <string name="settings_title">商品の設定</string>
    <string name="settings_name_immutable">商品名は変更できません。</string>
    <string name="settings_save">変更を保存</string>
    <string name="settings_archive">この商品をアーカイブ</string>
    <string name="settings_archive_blocked">在庫が %1$d%2$s 残っています。アーカイブは在庫が 0%2$s のときだけできます。先に使い切ってください。</string>
    <string name="settings_archive_note">在庫一覧・買い物リストから外れますが、記録も設定も残ります。「アーカイブした商品」からいつでも在庫に戻せます。</string>
    <!-- P6-2 アーカイブ -->
    <string name="archived_title">アーカイブした商品</string>
    <string name="archived_subtitle">%1$s · %2$d件</string>
    <string name="archived_hint_owner">いったん使うのをやめた商品。記録と設定はそのまま残っています。また必要になったら「在庫に戻す」。</string>
    <string name="archived_hint_viewer">いったん使うのをやめた商品。記録と設定はそのまま残っています。在庫に戻せるのはオーナーです。</string>
    <string name="archived_empty_title">アーカイブした商品はありません</string>
    <string name="archived_empty_sub">商品の設定からアーカイブすると、ここに集まります。</string>
    <string name="archived_row_meta">単位: %1$s · 最低 %2$d%3$s</string>
    <string name="archived_restore">在庫に戻す</string>
    <!-- P6-2 Profile -->
    <string name="profile_master_entry">商品マスタ</string>
    <string name="profile_master_entry_sub">商品の追加・単位・最低在庫の設定</string>
    <string name="profile_archived_entry">アーカイブした商品</string>
    <string name="profile_archived_entry_sub">使うのをやめた商品の確認・復元</string>
    <!-- P6-2 トースト -->
    <string name="toast_product_added">商品を追加しました</string>
    <string name="toast_product_adopted">商品を採用しました</string>
    <string name="toast_settings_saved">設定を保存しました</string>
    <string name="toast_archived">アーカイブしました</string>
    <string name="toast_unarchived">在庫に戻しました</string>
    <!-- P6-2 共通 -->
    <string name="action_back">戻る</string>
    <string name="action_settings">設定</string>
```

- [ ] **Step 2: AppIconName に semantic アイコンを追加**

`AppIcon.kt` の enum(`Leaf,` の後)に追加し、`vector()` の when に対応行を追加。`material-icons-extended` の vector を使う:

```kotlin
// enum class AppIconName { ... Leaf, の後に:
    Settings,
    Barcode,
    Archive,
    Restore,
    Pencil,
```

`vector()` の when(`AppIconName.Leaf -> MindstockGlyphs.Leaf` の後)に:

```kotlin
        AppIconName.Settings -> Icons.Outlined.Tune
        AppIconName.Barcode -> Icons.Outlined.QrCode
        AppIconName.Archive -> Icons.Outlined.Archive
        AppIconName.Restore -> Icons.Outlined.Unarchive
        AppIconName.Pencil -> Icons.Outlined.Edit
```

必要 import(ファイル先頭の Icons import 群に追加):

```kotlin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Unarchive
```

- [ ] **Step 3: EmptyState atom を作成**

```kotlin
package net.brightroom.mindstock.frontend.designsystem.atom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

/** 一覧の空状態。アイコン + タイトル + 補足。 */
@Composable
fun EmptyState(
    icon: AppIconName,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = LocalMindstockTokens.current
    Column(
        modifier = modifier.fillMaxWidth().padding(PaddingValues(vertical = 48.dp, horizontal = 24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 34.dp, tint = tokens.faint)
        }
        AppText(title, style = MindstockType.summaryTitle(), color = scheme.onSurface, textAlign = TextAlign.Center)
        AppText(sub, style = MindstockType.sectionMeta(), color = tokens.faint, textAlign = TextAlign.Center)
    }
}
```

> `AppText` が `textAlign` 引数を持つか確認。無ければ `AppText.kt` に `textAlign: TextAlign? = null` を足して `Text(textAlign = textAlign)` に渡す(1 行)。`MindstockType.summaryTitle()` / `sectionMeta()` は既存。

- [ ] **Step 4: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/composeResources/values/strings.xml \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppIcon.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/EmptyState.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/designsystem/atom/AppText.kt
git commit -m "feat(frontend): P6-2 の文言・アイコン・EmptyState atom を追加"
```

---

## Task 2: CatalogRepository(data 層)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/data/CatalogRepository.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/data/CatalogRepositoryTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog.data

import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult
import kotlin.test.Test

private abstract class FakeCatalogRpc : CatalogRpcService {
    override suspend fun search(name: CatalogItemName, limit: Int): RpcResult<CatalogItems, RpcError> = error("unused")
    override suspend fun lookupByJan(jan: Jan): RpcResult<net.brightroom.mindstock.domain.model.catalog.item.CatalogItem, RpcError> = error("unused")
}

private abstract class FakeArchiveProductRpc : ProductRpcService {
    override suspend fun list(householdId: HouseholdId) = error("unused")
    override suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError> = error("unused")
    override suspend fun shoppingList(householdId: HouseholdId) = error("unused")
}

class CatalogRepositoryTest {
    @Test
    fun search_returns_success_outcome_on_ok() =
        runTest {
            val fake = object : FakeCatalogRpc() {
                override suspend fun search(name: CatalogItemName, limit: Int) = RpcResult.Ok(CatalogItems(emptyList()))
            }
            val repo = CatalogRepository(
                catalogService = { fake },
                productRegisterService = { error("unused") },
                productService = { error("unused") },
            )
            repo.search(CatalogItemName("茶"), 20).shouldBeInstanceOf<RpcOutcome.Success<CatalogItems>>()
        }

    @Test
    fun list_archived_returns_success_outcome_on_ok() =
        runTest {
            val fake = object : FakeArchiveProductRpc() {
                override suspend fun listArchived(householdId: HouseholdId) = RpcResult.Ok(Products(emptyList()))
            }
            val repo = CatalogRepository(
                catalogService = { error("unused") },
                productRegisterService = { error("unused") },
                productService = { fake },
            )
            repo.listArchived(HouseholdId.create()).shouldBeInstanceOf<RpcOutcome.Success<Products>>()
        }
}
```

- [ ] **Step 2: テスト失敗を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*CatalogRepositoryTest*"`(または `:frontend:jsTest`)
Expected: FAIL(`CatalogRepository` 未定義)

> KMP test の実行ターゲットは既存テストに倣う(`InventoryRepositoryTest` が走るターゲットと同じ。`./gradlew :frontend:allTests` でも可)。OOM 回避のため wasmJs フルビルドは避け test タスク単体で。

- [ ] **Step 3: CatalogRepository を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog.data

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.toOutcome
import net.brightroom.mindstock.rpc.catalog.CatalogRpcService
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.product.ProductRegisterRpcService
import net.brightroom.mindstock.rpc.product.ProductRpcService

/**
 * 商品の採用・カスタム追加・マスタ設定・アーカイブまわりの RPC を隠蔽。
 * サービスは「開く関数」を遅延注入(認証後にトークン付きで open される)。
 */
class CatalogRepository(
    private val catalogService: () -> CatalogRpcService,
    private val productRegisterService: () -> ProductRegisterRpcService,
    private val productService: () -> ProductRpcService,
) {
    suspend fun search(name: CatalogItemName, limit: Int): RpcOutcome<CatalogItems> =
        catalogService().search(name, limit).toOutcome()

    suspend fun lookupByJan(jan: Jan): RpcOutcome<CatalogItem> =
        catalogService().lookupByJan(jan).toOutcome()

    suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcOutcome<Product> = productRegisterService().adopt(householdId, catalogItemId, unit, minimumStock).toOutcome()

    suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcOutcome<Product> = productRegisterService().addCustom(householdId, request).toOutcome()

    suspend fun changeUnit(productId: ProductId, unit: ProductUnit): RpcOutcome<Unit> =
        productRegisterService().changeUnit(productId, unit).toOutcome()

    suspend fun changeMinimum(productId: ProductId, minimumStock: MinimumStock): RpcOutcome<Unit> =
        productRegisterService().changeMinimum(productId, minimumStock).toOutcome()

    suspend fun archive(productId: ProductId): RpcOutcome<Unit> =
        productRegisterService().archive(productId).toOutcome()

    suspend fun unarchive(productId: ProductId): RpcOutcome<Unit> =
        productRegisterService().unarchive(productId).toOutcome()

    suspend fun listArchived(householdId: HouseholdId): RpcOutcome<Products> =
        productService().listArchived(householdId).toOutcome()
}
```

- [ ] **Step 4: テスト成功を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*CatalogRepositoryTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/data/ \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/data/
git commit -m "feat(frontend): CatalogRepository を追加(検索/採用/設定/アーカイブ)"
```

---

## Task 3: owner 算出 pure 関数

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/Ownership.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/OwnershipTest.kt`

- [ ] **Step 1: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.app

import io.kotest.matchers.shouldBe
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import kotlin.test.Test

private fun resident(id: ResidentId) = Resident(id, Profile(DisplayName("name")))

class OwnershipTest {
    @Test
    fun owner_member_is_owner() {
        val me = ResidentId.create()
        val hh = Household.create(HouseholdName("家"), resident(me))
        isOwner(Households(listOf(hh)), hh.id, me) shouldBe true
    }

    @Test
    fun non_owner_member_is_not_owner() {
        val owner = ResidentId.create()
        val me = ResidentId.create()
        val base = Household.create(HouseholdName("家"), resident(owner))
        val hh = base.copy(members = Members(base.members.list + HouseholdMember(resident(me), HouseholdMemberRole.メンバー)))
        isOwner(Households(listOf(hh)), hh.id, me) shouldBe false
    }

    @Test
    fun missing_session_is_not_owner() {
        isOwner(null, null, null) shouldBe false
    }
}
```

> `Resident` / `Profile` / `DisplayName` の正確なコンストラクタは `domain/.../resident/` を確認して合わせる。`Household.create` は既存(本ファイル末尾参照済)。

- [ ] **Step 2: テスト失敗を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*OwnershipTest*"`
Expected: FAIL(`isOwner` 未定義)

- [ ] **Step 3: 実装**

```kotlin
package net.brightroom.mindstock.frontend.app

import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId

/**
 * active 世帯で当該住人が世帯主か。session 欠落・非メンバー時は false。
 * サーバが owner を強制するので、これは UX ゲート(押せないボタンの非表示)専用。
 */
fun isOwner(
    households: Households?,
    activeHouseholdId: HouseholdId?,
    residentId: ResidentId?,
): Boolean {
    if (households == null || activeHouseholdId == null || residentId == null) return false
    val household = households.list.firstOrNull { it.id == activeHouseholdId } ?: return false
    if (!household.members.contains(residentId)) return false
    return household.members.roleOf(residentId).is世帯主()
}
```

- [ ] **Step 4: テスト成功を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*OwnershipTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/Ownership.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/app/OwnershipTest.kt
git commit -m "feat(frontend): owner 算出 pure 関数を追加"
```

---

## Task 4: AddProductViewModel + UiState

検索(名前) / JAN照会 / 採用 or カスタム追加の分岐を ViewModel が状態機械として持つ。単位・最低在庫・編集中の商品名は画面の local remember で扱う(一時 UI)。

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/AddProductUiState.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/AddProductViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/AddProductViewModelTest.kt`

- [ ] **Step 1: UiState を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems

/** 商品追加フローの段階。 */
sealed interface AddProductUiState {
    /** 検索段階。query は画面の local 入力をそのまま反映。 */
    data class Browsing(
        val results: CatalogItems = CatalogItems(emptyList()),
        val phase: BrowsePhase = BrowsePhase.Idle,
    ) : AddProductUiState

    /** マスタ採用フォーム(商品名ロック)。 */
    data class AdoptForm(
        val item: CatalogItem,
    ) : AddProductUiState

    /** カスタム追加フォーム。nameLocked=true(JAN照会ヒット)なら商品名固定。 */
    data class CustomForm(
        val seedName: String,
        val jan: Jan?,
        val nameLocked: Boolean,
    ) : AddProductUiState

    /** 採用/追加が成功し overlay を閉じる合図。 */
    data object Done : AddProductUiState
}

enum class BrowsePhase { Idle, Searching, JanLookingUp }
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private val sampleJan = Jan("4901234567894") // 有効な EAN-13(チェックディジット要確認・無効なら別の有効値に差し替え)

private fun item() = CatalogItem(CatalogItemId.create(), sampleJan, CatalogItemName("麦茶"))

private fun vm(
    search: suspend (CatalogItemName, Int) -> RpcOutcome<CatalogItems> = { _, _ -> RpcOutcome.Success(CatalogItems(emptyList())) },
    lookup: suspend (Jan) -> RpcOutcome<CatalogItem> = { RpcOutcome.Success(item()) },
    adopt: suspend (CatalogItemId, ProductUnit, MinimumStock) -> RpcOutcome<Product> = { _, _, _ -> RpcOutcome.Success(adoptedProduct()) },
    addCustom: suspend (AddCustomProductRequest) -> RpcOutcome<Product> = { RpcOutcome.Success(adoptedProduct()) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = AddProductViewModel(
    householdId = HouseholdId.create(),
    searchCatalog = search,
    lookupJan = lookup,
    adoptProduct = adopt,
    addCustomProduct = addCustom,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

private fun adoptedProduct(): Product = Product.adopt(item(), ProductUnit("個"), MinimumStock(1))

class AddProductViewModelTest {
    @Test
    fun search_blank_query_resets_to_idle() = runTest {
        val v = vm()
        v.search("")
        val s = v.state.value
        s.shouldBeInstanceOf<AddProductUiState.Browsing>()
        s.phase shouldBe BrowsePhase.Idle
    }

    @Test
    fun search_name_sets_results() = runTest {
        val v = vm(search = { _, _ -> RpcOutcome.Success(CatalogItems(listOf(item()))) })
        v.search("麦茶")
        val s = v.state.value
        s.shouldBeInstanceOf<AddProductUiState.Browsing>()
        s.results.size() shouldBe 1
    }

    @Test
    fun lookup_hit_moves_to_adopt_form() = runTest {
        val v = vm(lookup = { RpcOutcome.Success(item()) })
        v.lookupByJan(sampleJan)
        v.state.value.shouldBeInstanceOf<AddProductUiState.AdoptForm>()
    }

    @Test
    fun lookup_not_found_moves_to_custom_form_with_jan_locked() = runTest {
        val v = vm(lookup = { RpcOutcome.Failure(RpcError.NotFound("none")) })
        v.lookupByJan(sampleJan)
        val s = v.state.value
        s.shouldBeInstanceOf<AddProductUiState.CustomForm>()
        s.jan shouldBe sampleJan
        s.nameLocked shouldBe false
    }

    @Test
    fun adopt_success_sets_done_and_refresh_and_toast() = runTest {
        var refreshed = 0
        val refresh = InventoryRefreshController()
        val job = kotlinx.coroutines.GlobalScope.launch { refresh.signal.collect { refreshed++ } } // テスト内は launch + runCurrent でも可
        val v = vm(refresh = refresh)
        v.adopt(item(), ProductUnit("個"), MinimumStock(1))
        v.state.value shouldBe AddProductUiState.Done
        job.cancel()
    }

    @Test
    fun adopt_unauthorized_requests_reauth() = runTest {
        var reauthN = 0
        val reauth = ReauthController()
        val job = kotlinx.coroutines.launch { reauth.signal.collect { reauthN++ } }
        kotlinx.coroutines.test.runCurrent()
        val v = vm(adopt = { _, _, _ -> RpcOutcome.Failure(RpcError.Unauthorized("x")) }, reauth = reauth)
        v.adopt(item(), ProductUnit("個"), MinimumStock(1))
        kotlinx.coroutines.test.runCurrent()
        reauthN shouldBe 1
        job.cancel()
    }
}
```

> refresh 検証は `InventoryViewModelTest` の `launch { … } + runCurrent()` 形式に合わせて書き直してよい(上記 GlobalScope 例は雰囲気)。`sampleJan` は EAN-13 チェックディジットが通る値を使う(通らなければ `Jan` 構築時に IAE)。

- [ ] **Step 3: テスト失敗を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*AddProductViewModelTest*"`
Expected: FAIL(`AddProductViewModel` 未定義)

- [ ] **Step 4: AddProductViewModel を実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_product_added
import mindstock.frontend.generated.resources.toast_product_adopted
import net.brightroom.mindstock.domain.model.barcode.Barcode
import net.brightroom.mindstock.domain.model.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductName
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.product.AddCustomProductRequest
import net.brightroom.mindstock.rpc.result.RpcError

private const val SEARCH_LIMIT = 20

class AddProductViewModel(
    private val householdId: HouseholdId,
    private val searchCatalog: suspend (CatalogItemName, Int) -> RpcOutcome<CatalogItems>,
    private val lookupJan: suspend (Jan) -> RpcOutcome<CatalogItem>,
    private val adoptProduct: suspend (CatalogItemId, ProductUnit, MinimumStock) -> RpcOutcome<Product>,
    private val addCustomProduct: suspend (AddCustomProductRequest) -> RpcOutcome<Product>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<AddProductUiState>(AddProductUiState.Browsing())
    val state: StateFlow<AddProductUiState> = _state.asStateFlow()

    /** 名前検索。空 query は結果クリア。 */
    suspend fun search(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) {
            _state.value = AddProductUiState.Browsing()
            return
        }
        _state.value = AddProductUiState.Browsing(phase = BrowsePhase.Searching)
        _state.value =
            when (val out = searchCatalog(CatalogItemName(q), SEARCH_LIMIT)) {
                is RpcOutcome.Success -> AddProductUiState.Browsing(results = out.value, phase = BrowsePhase.Idle)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    AddProductUiState.Browsing(phase = BrowsePhase.Idle)
                }
            }
    }

    /** JAN 照会。ヒット→採用フォーム、NotFound→カスタムフォーム(JAN 紐付け・商品名手入力)。 */
    suspend fun lookupByJan(jan: Jan) {
        _state.value = AddProductUiState.Browsing(phase = BrowsePhase.JanLookingUp)
        _state.value =
            when (val out = lookupJan(jan)) {
                is RpcOutcome.Success -> AddProductUiState.AdoptForm(out.value)
                is RpcOutcome.Failure ->
                    if (out.error is RpcError.NotFound) {
                        AddProductUiState.CustomForm(seedName = "", jan = jan, nameLocked = false)
                    } else {
                        handleFailure(out.error)
                        AddProductUiState.Browsing(phase = BrowsePhase.Idle)
                    }
            }
    }

    fun pickCatalog(item: CatalogItem) {
        _state.value = AddProductUiState.AdoptForm(item)
    }

    /** マスタにない名前をその場でカスタム追加(JAN なし・商品名編集可)。 */
    fun pickCustom(seedName: String) {
        _state.value = AddProductUiState.CustomForm(seedName = seedName.trim(), jan = null, nameLocked = false)
    }

    fun backToBrowsing() {
        _state.value = AddProductUiState.Browsing()
    }

    suspend fun adopt(item: CatalogItem, unit: ProductUnit, minimumStock: MinimumStock) {
        submit(adoptProduct(item.id, unit, minimumStock), UiText(Res.string.toast_product_adopted))
    }

    suspend fun addCustom(name: ProductName, jan: Jan?, unit: ProductUnit, minimumStock: MinimumStock) {
        val barcode = if (jan == null) Barcode.Unlinked else Barcode.Linked(jan)
        val request = AddCustomProductRequest(name = name, unit = unit, barcode = barcode, minimumStock = minimumStock)
        submit(addCustomProduct(request), UiText(Res.string.toast_product_added))
    }

    private suspend fun submit(outcome: RpcOutcome<Product>, successText: UiText) {
        when (outcome) {
            is RpcOutcome.Success -> {
                refresh.request()
                toast.show(successText)
                _state.value = AddProductUiState.Done
            }
            is RpcOutcome.Failure -> handleFailure(outcome.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

- [ ] **Step 5: テスト成功を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*AddProductViewModelTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/AddProduct*.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/AddProductViewModelTest.kt
git commit -m "feat(frontend): AddProductViewModel(検索/JAN照会/採用/カスタム)を追加"
```

---

## Task 5: ProductMasterViewModel + UiState

採用中商品の一覧(`productService.list` を再利用)+ 設定保存(changeUnit/changeMinimum/archive)。一覧は `Stocks` をそのまま持ち、行は `Stock`(unit/qty 表示)。

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ProductMasterUiState.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ProductMasterViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ProductMasterViewModelTest.kt`

> `list`(Stocks 取得)は `InventoryRepository.list` と同じ RPC。`ProductMasterViewModel` には `loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>` を注入(App.kt では `inventoryRepository::list` を渡す。DRY:同じ Repository メソッドを使い、二重ラップしない)。設定系は `CatalogRepository` を注入。

- [ ] **Step 1: UiState を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ProductMasterUiState {
    data object Loading : ProductMasterUiState
    data class Content(val stocks: Stocks) : ProductMasterUiState
    data class Error(val text: UiText) : ProductMasterUiState
}
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    load: suspend (HouseholdId) -> RpcOutcome<Stocks> = { RpcOutcome.Success(Stocks(emptyList())) },
    changeUnit: suspend (ProductId, ProductUnit) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    changeMin: suspend (ProductId, MinimumStock) -> RpcOutcome<Unit> = { _, _ -> RpcOutcome.Success(Unit) },
    archive: suspend (ProductId) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ProductMasterViewModel(
    householdId = HouseholdId.create(),
    loadStocks = load,
    changeUnitOf = changeUnit,
    changeMinimumOf = changeMin,
    archiveProduct = archive,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ProductMasterViewModelTest {
    @Test
    fun load_success_sets_content() = runTest {
        val v = vm()
        v.load()
        v.state.value.shouldBeInstanceOf<ProductMasterUiState.Content>()
    }

    @Test
    fun load_failure_sets_error() = runTest {
        val v = vm(load = { RpcOutcome.Failure(RpcError.Internal("boom")) })
        v.load()
        v.state.value.shouldBeInstanceOf<ProductMasterUiState.Error>()
    }

    @Test
    fun change_unit_success_reloads_and_refreshes() = runTest {
        var loads = 0
        val v = vm(load = { loads++; RpcOutcome.Success(Stocks(emptyList())) })
        v.load()
        v.changeUnit(ProductId.create(), ProductUnit("本"))
        loads shouldBe 2
    }

    @Test
    fun archive_unauthorized_requests_reauth() = runTest {
        var reauthN = 0
        val reauth = ReauthController()
        val job = kotlinx.coroutines.launch { reauth.signal.collect { reauthN++ } }
        kotlinx.coroutines.test.runCurrent()
        val v = vm(archive = { RpcOutcome.Failure(RpcError.Unauthorized("x")) }, reauth = reauth)
        v.archive(ProductId.create())
        kotlinx.coroutines.test.runCurrent()
        reauthN shouldBe 1
        job.cancel()
    }
}
```

- [ ] **Step 3: テスト失敗を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*ProductMasterViewModelTest*"`
Expected: FAIL

- [ ] **Step 4: 実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_archived
import mindstock.frontend.generated.resources.toast_settings_saved
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ProductMasterViewModel(
    private val householdId: HouseholdId,
    private val loadStocks: suspend (HouseholdId) -> RpcOutcome<Stocks>,
    private val changeUnitOf: suspend (ProductId, ProductUnit) -> RpcOutcome<Unit>,
    private val changeMinimumOf: suspend (ProductId, MinimumStock) -> RpcOutcome<Unit>,
    private val archiveProduct: suspend (ProductId) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ProductMasterUiState>(ProductMasterUiState.Loading)
    val state: StateFlow<ProductMasterUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ProductMasterUiState.Loading
        _state.value =
            when (val out = loadStocks(householdId)) {
                is RpcOutcome.Success -> ProductMasterUiState.Content(out.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ProductMasterUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun changeUnit(productId: ProductId, unit: ProductUnit) =
        write(changeUnitOf(productId, unit), UiText(Res.string.toast_settings_saved))

    suspend fun changeMinimum(productId: ProductId, minimumStock: MinimumStock) =
        write(changeMinimumOf(productId, minimumStock), UiText(Res.string.toast_settings_saved))

    suspend fun archive(productId: ProductId) =
        write(archiveProduct(productId), UiText(Res.string.toast_archived))

    private suspend fun write(outcome: RpcOutcome<Unit>, successText: UiText) {
        when (outcome) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(successText)
            }
            is RpcOutcome.Failure -> handleFailure(outcome.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

- [ ] **Step 5: テスト成功を確認 → Commit**

Run: `./gradlew :frontend:wasmJsTest --tests "*ProductMasterViewModelTest*"`
Expected: PASS

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ProductMaster*.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ProductMasterViewModelTest.kt
git commit -m "feat(frontend): ProductMasterViewModel(一覧 + 単位/最低在庫/アーカイブ)を追加"
```

---

## Task 6: ArchivedViewModel + UiState

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ArchivedUiState.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ArchivedViewModel.kt`
- Test: `frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ArchivedViewModelTest.kt`

- [ ] **Step 1: UiState を作成**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.ui.UiText

sealed interface ArchivedUiState {
    data object Loading : ArchivedUiState
    data class Content(val products: Products) : ArchivedUiState
    data class Error(val text: UiText) : ArchivedUiState
}
```

- [ ] **Step 2: 失敗するテストを書く**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.rpc.result.RpcError
import kotlin.test.Test

private fun vm(
    load: suspend (HouseholdId) -> RpcOutcome<Products> = { RpcOutcome.Success(Products(emptyList())) },
    unarchive: suspend (ProductId) -> RpcOutcome<Unit> = { RpcOutcome.Success(Unit) },
    refresh: InventoryRefreshController = InventoryRefreshController(),
    toast: ToastController = ToastController(),
    reauth: ReauthController = ReauthController(),
) = ArchivedViewModel(
    householdId = HouseholdId.create(),
    loadArchived = load,
    unarchiveProduct = unarchive,
    refresh = refresh,
    toast = toast,
    reauth = reauth,
)

class ArchivedViewModelTest {
    @Test
    fun load_success_sets_content() = runTest {
        val v = vm()
        v.load()
        v.state.value.shouldBeInstanceOf<ArchivedUiState.Content>()
    }

    @Test
    fun unarchive_success_reloads_and_refreshes() = runTest {
        var loads = 0
        val v = vm(load = { loads++; RpcOutcome.Success(Products(emptyList())) })
        v.load()
        v.unarchive(ProductId.create())
        loads shouldBe 2
    }

    @Test
    fun load_failure_sets_error() = runTest {
        val v = vm(load = { RpcOutcome.Failure(RpcError.Internal("x")) })
        v.load()
        v.state.value.shouldBeInstanceOf<ArchivedUiState.Error>()
    }
}
```

- [ ] **Step 3: テスト失敗を確認**

Run: `./gradlew :frontend:wasmJsTest --tests "*ArchivedViewModelTest*"`
Expected: FAIL

- [ ] **Step 4: 実装**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.toast_unarchived
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.frontend.core.auth.ReauthController
import net.brightroom.mindstock.frontend.core.rpc.RpcOutcome
import net.brightroom.mindstock.frontend.core.rpc.errorText
import net.brightroom.mindstock.frontend.core.rpc.requiresReauth
import net.brightroom.mindstock.frontend.core.ui.InventoryRefreshController
import net.brightroom.mindstock.frontend.core.ui.ToastController
import net.brightroom.mindstock.frontend.core.ui.UiText
import net.brightroom.mindstock.rpc.result.RpcError

class ArchivedViewModel(
    private val householdId: HouseholdId,
    private val loadArchived: suspend (HouseholdId) -> RpcOutcome<Products>,
    private val unarchiveProduct: suspend (ProductId) -> RpcOutcome<Unit>,
    private val refresh: InventoryRefreshController,
    private val toast: ToastController,
    private val reauth: ReauthController,
) : ViewModel() {
    private val _state = MutableStateFlow<ArchivedUiState>(ArchivedUiState.Loading)
    val state: StateFlow<ArchivedUiState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = ArchivedUiState.Loading
        _state.value =
            when (val out = loadArchived(householdId)) {
                is RpcOutcome.Success -> ArchivedUiState.Content(out.value)
                is RpcOutcome.Failure -> {
                    handleFailure(out.error)
                    ArchivedUiState.Error(errorText(out.error))
                }
            }
    }

    suspend fun unarchive(productId: ProductId) {
        when (val out = unarchiveProduct(productId)) {
            is RpcOutcome.Success -> {
                load()
                refresh.request()
                toast.show(UiText(Res.string.toast_unarchived))
            }
            is RpcOutcome.Failure -> handleFailure(out.error)
        }
    }

    private fun handleFailure(error: RpcError) {
        if (error.requiresReauth()) reauth.request() else toast.show(errorText(error))
    }
}
```

- [ ] **Step 5: テスト成功を確認 → Commit**

Run: `./gradlew :frontend:wasmJsTest --tests "*ArchivedViewModelTest*"`
Expected: PASS

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/Archived*.kt \
        frontend/src/commonTest/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ArchivedViewModelTest.kt
git commit -m "feat(frontend): ArchivedViewModel(アーカイブ一覧 + 復元)を追加"
```

---

## Task 7: UnitPicker composable(feature-local)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/UnitPicker.kt`

mock: `screens-master.jsx` L4(`COMMON_UNITS`)/ L7-27。共通単位チップ(横並び wrap)+ 「その他(自由入力)」テキスト入力。選択中チップは accent。

- [ ] **Step 1: 実装(UI のみ・テストなし)**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType

private val COMMON_UNITS = listOf("個", "本", "袋", "パック", "箱", "ロール", "缶", "枚", "セット")

/** 単位選択。共通チップ + 自由入力。value はトリム前の生文字列。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitPicker(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val custom = value.isNotEmpty() && value !in COMMON_UNITS
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            COMMON_UNITS.forEach { u ->
                val active = value == u
                AppText(
                    text = u,
                    style = MindstockType.button(),
                    color = if (active) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .border(1.5.dp, if (active) scheme.primary else scheme.outline, RoundedCornerShape(99.dp))
                        .background(if (active) scheme.primaryContainer else scheme.surface)
                        .clickable { onChange(u) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        TextInput(
            value = if (custom) value else "",
            onValueChange = onChange,
            placeholder = stringResourceOther(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun stringResourceOther(): String =
    org.jetbrains.compose.resources.stringResource(mindstock.frontend.generated.resources.Res.string.add_product_unit_other)
```

> `TextInput` atom の引数(`value`/`onValueChange`/`placeholder`)を `TextInput.kt` で確認し合わせる。`FlowRow` は `androidx.compose.foundation.layout.FlowRow`(Compose Multiplatform で利用可)。

- [ ] **Step 2: コンパイル確認 → Commit**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/UnitPicker.kt
git commit -m "feat(frontend): UnitPicker(単位チップ + 自由入力)を追加"
```

---

## Task 8: AddProductScreen(UI)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/AddProductScreen.kt`

mock: `screens-b.jsx` L337-570。フルスクリーン overlay。`AddProductUiState` を購読し 2 段(検索 / フォーム)を描く。検索段階の transient query と、フォーム段階の unit/min/編集中名は **local remember**。

設計メモ(実装は mock の見た目に寄せる。clay は既存 atom と `LocalMindstockTokens` を使う):
- ヘッダ: `RoundBtn(AppIconName.Back, …)` + タイトル(`AppText` summaryTitle)。フォーム段階の戻るは `vm.backToBrowsing()`、検索段階は `onClose()`。
- 検索段階:
  - `SearchField`(local `query`)。`query` 変更で `LaunchedEffect(query)`(debounce 任意)→ JAN 判定して `vm.search(query)`(名前)。
  - JAN 判定: `query` の数字列を取り `Jan` を構築できる(13桁 + チェックディジット)なら JAN モード。`runCatching { Jan(digits) }.getOrNull()`。JAN モードでは「JANコード『…』で商品を探す」ボタン → `vm.lookupByJan(jan)`。
  - 結果一覧: `state.results.list` を行表示(`Thumb` + 名前 + meta)。タップ → `vm.pickCatalog(item)`。
  - マスタに無い場合: 「『query』を世帯に追加」ボタン → `vm.pickCustom(query)`。
  - `phase == JanLookingUp` 時はローディング(`add_product_loading_jan`)。
- フォーム段階(`AdoptForm` / `CustomForm`):
  - 上部に対象サマリ(`Thumb` + 名前)。
  - 商品名: `AdoptForm` はロック表示(`item.name()`)。`CustomForm` は nameLocked=true ならロック、false なら `TextInput`(local `name`、初期値 `seedName`)。
  - `UnitPicker`(local `unit`、初期値 "個")。
  - 最低在庫 `Stepper`(local `min`、初期値 1、`min=0` 許容なので `Stepper(min=0)`)。
  - 送信 `PrimaryButton`:
    - `AdoptForm` → `vm.adopt(item, ProductUnit(unit), MinimumStock(min))`
    - `CustomForm` → `vm.addCustom(ProductName(name), jan, ProductUnit(unit), MinimumStock(min))`
    - 活性条件: `unit.isNotBlank()` かつ(AdoptForm は常に / CustomForm は `name.isNotBlank()`)。
  - 画像ノート文(`add_product_image_note`)を下部に。
- `state == Done` の監視は overlay 側(Task 11)で行い `onClose()`。

> 入力値 VO 化(`ProductUnit`/`ProductName`/`Jan`)は送信時のみ。VO 構築は IAE を投げ得るので、活性条件で空/桁を弾いてから構築する(`MinimumStock` は 0 以上、`ProductUnit`/`ProductName` は非空)。

- [ ] **Step 1: AddProductScreen を実装**(上記設計に沿って。シグネチャ:)

```kotlin
@Composable
fun AddProductScreen(
    state: AddProductUiState,
    onQuery: (String) -> Unit,          // 名前検索(空でリセット)
    onLookupJan: (Jan) -> Unit,
    onPickCatalog: (CatalogItem) -> Unit,
    onPickCustom: (String) -> Unit,
    onBack: () -> Unit,                 // フォーム→検索 / 検索→閉じる は呼び出し側で分岐
    onClose: () -> Unit,
    onAdopt: (CatalogItem, ProductUnit, MinimumStock) -> Unit,
    onAddCustom: (ProductName, Jan?, ProductUnit, MinimumStock) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/AddProductScreen.kt
git commit -m "feat(frontend): AddProductScreen(検索 → 採用/カスタムフォーム)を追加"
```

---

## Task 9: ProductSettingsSheet(UI)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductSettingsSheet.kt`

mock: `screens-master.jsx` L57-116(`MasterItemSheet`、画像欄は**外す**)。`Sheet` atom を使うモーダル。

設計メモ:
- 引数: `open: Boolean`, `stock: Stock?`(現在の unit/min/qty を持つ), `onClose`, `onChangeUnit: (ProductUnit) -> Unit`, `onChangeMinimum: (MinimumStock) -> Unit`, `onArchive: () -> Unit`。
- local remember: `unit`(初期 `stock.product.setting.unit()`)、`min`(初期 `minimumStock()`)。`LaunchedEffect(stock?.product?.id)` で初期化。
- 本体: 商品名(`AppText`、変更不可ノート `settings_name_immutable`)/ `UnitPicker` / 最低在庫 `Stepper(min=0)`。
- 「変更を保存」`PrimaryButton`: unit か min が初期値と異なるとき活性。押下で差分のみ `onChangeUnit` / `onChangeMinimum` を呼び `onClose()`。
- アーカイブ領域: `stock.currentQuantity() > 0` なら `AppButton(variant=Quiet, enabled=false)` + 在庫残あり説明(`settings_archive_blocked`)。0 なら活性 + `onArchive()` 後 `onClose()`、説明 `settings_archive_note`。

> 画像欄(ImageField)は spec の見送りに従い**置かない**。`changeImage` は呼ばない。

- [ ] **Step 1: 実装 → Step 2: コンパイル → Step 3: Commit**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductSettingsSheet.kt
git commit -m "feat(frontend): ProductSettingsSheet(単位/最低在庫/アーカイブ)を追加"
```

---

## Task 10: ProductMasterScreen + ArchivedScreen(UI)

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductMasterScreen.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ArchivedScreen.kt`

mock: `MasterScreen` L123-176 / `ArchivedScreen` L179-220。両方フルスクリーン overlay(ヘッダ + スクロール一覧)。

ProductMasterScreen 設計:
- 引数: `state: ProductMasterUiState`, `householdName: String`, `onBack`, `onAdd`, `onSelect: (Stock) -> Unit`。
- ヘッダ: 戻る + タイトル `master_title` + サブ `master_subtitle(name, count)`。
- 本体: `AddTile(master_add, onAdd)` + ヒント `master_hint`。`Content.stocks.list` を行表示(`Thumb` + 名前 + `master_row_meta(unit, qty, unit)` + `Pencil` アイコン)。タップ → `onSelect(stock)`。空なら `EmptyState(Box, master_empty_title, master_empty_sub)`。

ArchivedScreen 設計:
- 引数: `state: ArchivedUiState`, `householdName: String`, `canRestore: Boolean`, `onBack`, `onRestore: (ProductId) -> Unit`。
- ヘッダ: 戻る + `archived_title` + `archived_subtitle(name, count)`。
- ヒント: `canRestore` で `archived_hint_owner` / `archived_hint_viewer`。
- 本体: `Content.products.list` を行表示(`Thumb` + 名前 + `archived_row_meta(unit, min, unit)` + `canRestore` なら `AppButton(Soft, Restore, archived_restore){…}` → `onRestore(product.id)`)。空なら `EmptyState(Archive, archived_empty_title, archived_empty_sub)`。

> Product から unit/min: `product.setting.unit()` / `product.setting.minimumStock()`。Stock から qty: `stock.currentQuantity()`。

- [ ] **Step 1: 両 Screen を実装 → Step 2: コンパイル → Step 3: Commit**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ProductMasterScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/ArchivedScreen.kt
git commit -m "feat(frontend): ProductMasterScreen / ArchivedScreen を追加"
```

---

## Task 11: ProductDetail に設定歯車を追加

**Files:**
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailOverlay.kt`

- [ ] **Step 1: ProductDetailScreen に `onOpenSettings: (() -> Unit)?` を追加**

`onToggleWanted` の後に引数追加:

```kotlin
    onToggleWanted: (wanted: Boolean) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
```

ヘッダの Row(`RoundBtn(AppIconName.Back …)` + `AppText(stock.product.name())` がある行)に、owner のときのみ右端へ歯車を出す。`Row` を `fillMaxWidth` にし name の後に `Spacer(Modifier.weight(1f))` + 条件付きボタン:

```kotlin
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(AppIconName.Back, contentDescription = stringResource(Res.string.action_back), onClick = onBack)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
            AppText(stock.product.name())
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (onOpenSettings != null) {
                RoundBtn(AppIconName.Settings, contentDescription = stringResource(Res.string.action_settings), onClick = onOpenSettings)
            }
        }
```

(`Res.string.action_back` / `action_settings` は Task 1 で追加済。`weight` 用に Row は `RowScope`。)

- [ ] **Step 2: ProductDetailOverlay に中継を追加**

`ProductDetailOverlay` に `onOpenSettings: (() -> Unit)? = null` 引数を追加し、`ProductDetailScreen(… onToggleWanted = …, onOpenSettings = onOpenSettings)` へ渡す。

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailOverlay.kt
git commit -m "feat(frontend): 商品詳細に owner 用設定歯車の seam を追加"
```

---

## Task 12: overlay host + Profile タブ + AppShell 配線

**Files:**
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/CatalogOverlay.kt`
- Create: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/profile/ProfileScreen.kt`
- Modify: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt`
- Modify: `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`

- [ ] **Step 1: CatalogOverlay 種別 sealed を定義**

```kotlin
package net.brightroom.mindstock.frontend.feature.catalog.ui

import net.brightroom.mindstock.domain.model.inventory.stock.Stock

/** app 層が重ねる商品系 overlay の種別。 */
sealed interface CatalogOverlay {
    data object AddProduct : CatalogOverlay
    data object Master : CatalogOverlay
    data object Archived : CatalogOverlay
    data class Settings(val stock: Stock) : CatalogOverlay
}
```

- [ ] **Step 2: ProfileScreen(最小)を実装**

```kotlin
package net.brightroom.mindstock.frontend.app.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.profile_archived_entry
import mindstock.frontend.generated.resources.profile_archived_entry_sub
import mindstock.frontend.generated.resources.profile_master_entry
import mindstock.frontend.generated.resources.profile_master_entry_sub
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import org.jetbrains.compose.resources.stringResource

/** 最小 Profile タブ。商品マスタ(owner のみ)/ アーカイブの 2 行。残りは P6-3。 */
@Composable
fun ProfileScreen(
    isOwner: Boolean,
    onOpenMaster: () -> Unit,
    onOpenArchived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isOwner) {
            ProfileRow(AppIconName.Settings, stringResource(Res.string.profile_master_entry), stringResource(Res.string.profile_master_entry_sub), onOpenMaster)
        }
        ProfileRow(AppIconName.Archive, stringResource(Res.string.profile_archived_entry), stringResource(Res.string.profile_archived_entry_sub), onOpenArchived)
    }
}

@Composable
private fun ProfileRow(icon: AppIconName, title: String, sub: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(icon, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            AppText(title)
            AppText(sub)
        }
        AppIcon(AppIconName.ChevronRight, contentDescription = null)
    }
}
```

> 行のレイアウトは clay に寄せて調整(`Row` の `fillMaxSize` は `fillMaxWidth` に。簡潔さ優先で骨子のみ提示)。

- [ ] **Step 3: AppShell に profileContent を追加**

`AppShell` の引数に `profileContent: @Composable () -> Unit` を足し、`Tab.Profile -> AppText(...)` を `Tab.Profile -> profileContent()` に置換。`tab_profile_placeholder` の import は不要になれば削除。

```kotlin
@Composable
fun AppShell(
    stockContent: @Composable () -> Unit,
    shopContent: @Composable () -> Unit,
    activityContent: @Composable () -> Unit,
    profileContent: @Composable () -> Unit,
) {
    // …
        when (selected) {
            Tab.Stock -> stockContent()
            Tab.Shop -> shopContent()
            Tab.Activity -> activityContent()
            Tab.Profile -> profileContent()
        }
}
```

- [ ] **Step 4: App.kt を配線**

`AuthState.Ready` の `householdId != null` ブロック内に以下を追加・変更:

1. `isOwner` を算出:
```kotlin
val owner = isOwner(sessionState.households, sessionState.activeHouseholdId, sessionState.residentId)
```
2. `CatalogRepository` を `repository` と並べて `remember`:
```kotlin
val catalogRepository = remember {
    CatalogRepository(
        catalogService = { rpc.service<CatalogRpcService>() },
        productRegisterService = { rpc.service<ProductRegisterRpcService>() },
        productService = { rpc.service<ProductRpcService>() },
    )
}
```
3. overlay 状態:
```kotlin
var catalogOverlay by remember { mutableStateOf<CatalogOverlay?>(null) }
```
4. `onAddProduct = { toast.show(...) }` を `onAddProduct = { catalogOverlay = CatalogOverlay.AddProduct }` に変更。
5. `AppShell(… )` に `profileContent` を追加:
```kotlin
profileContent = {
    ProfileScreen(
        isOwner = owner,
        onOpenMaster = { catalogOverlay = CatalogOverlay.Master },
        onOpenArchived = { catalogOverlay = CatalogOverlay.Archived },
    )
},
```
6. ProductDetailOverlay 呼び出しに `onOpenSettings` を追加(owner かつ stock 取得可のとき設定を開く):
```kotlin
onOpenSettings = if (owner) {
    { (/* state Content の stock or seed */ target.seed)?.let { catalogOverlay = CatalogOverlay.Settings(it) } }
} else null,
```
> 詳細の最新 stock は overlay 内なので、ここでは `target.seed` を使う(activity 由来で seed=null のときは設定不可で可。owner は通常 Stock タブ/詳細経由で seed あり)。厳密化が要れば後続で `ProductDetailOverlay` から stock を上げる。
7. overlay host を `Box` 末尾(`ProductDetailOverlay` if ブロックの後、Toast の前)に追加:

```kotlin
when (val ov = catalogOverlay) {
    null -> Unit
    is CatalogOverlay.AddProduct -> {
        val addVm = remember(householdId) {
            AddProductViewModel(
                householdId = householdId,
                searchCatalog = catalogRepository::search,
                lookupJan = catalogRepository::lookupByJan,
                adoptProduct = { id, u, m -> catalogRepository.adopt(householdId, id, u, m) },
                addCustomProduct = { req -> catalogRepository.addCustom(householdId, req) },
                refresh = refresh, toast = toast, reauth = reauth,
            )
        }
        val addState by addVm.state.collectAsState()
        LaunchedEffect(addState) { if (addState is AddProductUiState.Done) catalogOverlay = null }
        AddProductScreen(
            state = addState,
            onQuery = { scope.launch { addVm.search(it) } },
            onLookupJan = { scope.launch { addVm.lookupByJan(it) } },
            onPickCatalog = { addVm.pickCatalog(it) },
            onPickCustom = { addVm.pickCustom(it) },
            onBack = { if (addState is AddProductUiState.Browsing) catalogOverlay = null else addVm.backToBrowsing() },
            onClose = { catalogOverlay = null },
            onAdopt = { item, u, m -> scope.launch { addVm.adopt(item, u, m) } },
            onAddCustom = { name, jan, u, m -> scope.launch { addVm.addCustom(name, jan, u, m) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
    is CatalogOverlay.Master -> {
        val masterVm = remember(householdId) {
            ProductMasterViewModel(
                householdId = householdId,
                loadStocks = repository::list,
                changeUnitOf = catalogRepository::changeUnit,
                changeMinimumOf = catalogRepository::changeMinimum,
                archiveProduct = catalogRepository::archive,
                refresh = refresh, toast = toast, reauth = reauth,
            )
        }
        val mState by masterVm.state.collectAsState()
        var settingsStock by remember { mutableStateOf<Stock?>(null) }
        LaunchedEffect(masterVm) { masterVm.load() }
        LaunchedEffect(refresh) { refresh.signal.collect { masterVm.load() } }
        ProductMasterScreen(
            state = mState,
            householdName = activeHouseholdName(sessionState),
            onBack = { catalogOverlay = null },
            onAdd = { catalogOverlay = CatalogOverlay.AddProduct },
            onSelect = { settingsStock = it },
            modifier = Modifier.fillMaxSize(),
        )
        ProductSettingsSheet(
            open = settingsStock != null,
            stock = settingsStock,
            onClose = { settingsStock = null },
            onChangeUnit = { u -> settingsStock?.let { scope.launch { masterVm.changeUnit(it.product.id, u) } } },
            onChangeMinimum = { m -> settingsStock?.let { scope.launch { masterVm.changeMinimum(it.product.id, m) } } },
            onArchive = { settingsStock?.let { scope.launch { masterVm.archive(it.product.id) } }; settingsStock = null },
        )
    }
    is CatalogOverlay.Archived -> {
        val archVm = remember(householdId) {
            ArchivedViewModel(
                householdId = householdId,
                loadArchived = catalogRepository::listArchived,
                unarchiveProduct = catalogRepository::unarchive,
                refresh = refresh, toast = toast, reauth = reauth,
            )
        }
        val aState by archVm.state.collectAsState()
        LaunchedEffect(archVm) { archVm.load() }
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
        // 商品詳細からの単発設定。owner 専用 VM を一時生成。
        val settingsVm = remember(householdId) {
            ProductMasterViewModel(
                householdId = householdId,
                loadStocks = repository::list,
                changeUnitOf = catalogRepository::changeUnit,
                changeMinimumOf = catalogRepository::changeMinimum,
                archiveProduct = catalogRepository::archive,
                refresh = refresh, toast = toast, reauth = reauth,
            )
        }
        ProductSettingsSheet(
            open = true,
            stock = ov.stock,
            onClose = { catalogOverlay = null },
            onChangeUnit = { u -> scope.launch { settingsVm.changeUnit(ov.stock.product.id, u) } },
            onChangeMinimum = { m -> scope.launch { settingsVm.changeMinimum(ov.stock.product.id, m) } },
            onArchive = { scope.launch { settingsVm.archive(ov.stock.product.id) }; catalogOverlay = null },
        )
    }
}
```

`activeHouseholdName(sessionState)` ヘルパ(App.kt 内 private fn):

```kotlin
private fun activeHouseholdName(s: AppSession.State): String =
    s.households?.list?.firstOrNull { it.id == s.activeHouseholdId }?.profile?.name?.invoke() ?: ""
```

> `Profile.name` の VO アクセサは `household/Profile.kt` を確認(`HouseholdName` → `invoke()` か `value`)。`Stock` の import を App.kt に追加。

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(import/シグネチャ不整合があればここで潰す)

- [ ] **Step 6: Commit**

```bash
git add frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/catalog/ui/CatalogOverlay.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/profile/ProfileScreen.kt \
        frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/shell/AppShell.kt \
        frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt
git commit -m "feat(frontend): 商品追加/マスタ/設定/アーカイブを app 層に配線(最小 Profile タブ)"
```

---

## Task 13: 全体ビルド・テスト・検証

- [ ] **Step 1: frontend のテストとコンパイルを通す**

Run: `./gradlew :frontend:wasmJsTest :frontend:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL(全 ViewModel/Repository/Ownership テスト緑)

> フルビルド(`:frontend:build`)は WasmJs で OOM るため避ける(local-build-tips 準拠)。テストタスク + compile で代替。

- [ ] **Step 2: dev サーバで手動確認(任意・推奨)**

Run: `./gradlew :frontend:wasmJsBrowserDevelopmentRun`
確認: ①Stock「+」→ 検索 → 採用/カスタム追加 → 一覧に反映 ②商品詳細(owner)歯車 → 単位/最低在庫変更・在庫0でアーカイブ ③設定タブ → 商品マスタ / アーカイブ → 復元。

- [ ] **Step 3: 最終 Commit(あれば)**

```bash
git add -A && git commit -m "chore(frontend): P6-2 仕上げ(微修正)" || echo "nothing to commit"
```

---

## Self-Review(記入済み)

**Spec coverage:**
- 商品追加(採用/カスタム)→ Task 4, 8 ✓ / 単位・最低在庫設定 → Task 5, 9 ✓ / アーカイブ → Task 5, 9 ✓ / アーカイブ一覧・復元 → Task 6, 10 ✓ / 商品マスタ管理 → Task 5, 10 ✓ / owner ゲート → Task 3, 11, 12 ✓ / 入口(+/Profile/歯車) → Task 11, 12 ✓ / refresh → 各 VM の `refresh.request()` ✓ / 画像・カメラ見送り → 設計どおり未配線 ✓ / i18n → Task 1 ✓ / designsystem atom 経由 → Task 1,7 + 既存 atom ✓ / テスト → Task 2–6 ✓
- 型整合: `CatalogRepository.adopt/addCustom` は `householdId` 引数を取り、VM 注入時に部分適用(`{ id,u,m -> catalogRepository.adopt(householdId, id,u,m) }`)。`ProductMasterViewModel.loadStocks` は `InventoryRepository.list` を共有(DRY)。`Stepper(min=0)` で最低在庫 0 を許容。

**未確定で実装時に要確認(プレースホルダではなく実コードに対する検証点):**
- `TextInput` atom / `AppText` の引数シグネチャ(`textAlign` 有無)
- `Profile.name` VO アクセサ(`invoke()` / `value`)・`Resident`/`DisplayName` コンストラクタ(Task 3 テスト)
- `sampleJan` の EAN-13 チェックディジット(無効なら有効値へ差し替え)
- KMP test 実行ターゲット名(`wasmJsTest` / `jsTest` / `allTests`)は既存テストの流し方に合わせる
