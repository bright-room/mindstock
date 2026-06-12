# フェーズ 2: ドメインロジック引き込み・層責務是正 実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(推奨)または superpowers:executing-plans でタスク単位に実行。ステップは `- [ ]` で追跡。TDD(失敗するテスト→最小実装→green→commit)を守る。検証コマンドの期待出力を確認してから次へ進む。

**Goal:** リッチドメイン原則の実質化。application/presentation に漏れたビジネスロジックを domain に引き込み、商品マスタ編集の認可漏れを塞ぐ。挙動変更は 2-4(マスタ編集がオーナー限定になる)の 1 件のみ。

**Architecture:** 既存アーキテクチャ(presentation → application ← infrastructure / domain は横断 / 腐敗防止層は presentation)を維持。読み取り射影型(`ActivityFeed`/`InvitationPreview`)は **wire DTO として `:rpc` に残す**(ルールなし・内部表現を隠す射影 = 腐敗防止層の Response 型)。domain に入れず、`backend/core` に `:rpc` 依存も足さない。

**Tech Stack:** Kotlin Multiplatform / Exposed / kotlinx-rpc / Kotest(FunSpec)+ mockk

---

## スコープ確定(2026-06-12 ユーザ確認済み)

マスタープラン `2026-06-12-refactoring-master-plan.md` のフェーズ 2 から、議論を経て以下に確定:

| # | 扱い | 備考 |
|---|------|------|
| 2-1 | **実施** | ただし `Stocks.buildShoppingList()` ではなく **`ShoppingList.from(stocks, wantedProductIds)`**。集約が自分の read-model に逆依存するのを避け、read-model→集約の自然な方向(既存 `ShoppingEntry→Stock` と同方向)にする |
| 2-2 | **見送り** | Controller の flatten+sort は domain→wire の腐敗防止マッピングで presentation の正当な責務。`StockControllerTest` で検証済み。元プランの「application 層へ格上げ」は腐敗防止層ルールの誤解として取り下げ |
| 2-3 | **見送り** | previewInvite は状態変更なし・ルールなしの純読み射影。2 サービス跨ぎだが Scenario 抽出は ceremony と判断し現状維持 |
| 2-4 | **実施** | 認可漏れ修正(挙動変更・PR に明記) |
| 2-5 | 欠番 | id ルール廃止により |
| 2-6 | **実施** | no-op hydration の削除 |
| 2-7 | **実施** | SessionGuard 構造化ログ |
| 2-8 | **実施** | AUTH_* 起動時 fail-fast |

**2-4 認可境界(spec `01-sudo-modeling.md` UC10/13/19/22/23・`03-domain-detail.md:323` 準拠):**
- **マスタ管理(世帯主のみ)を要求**: `changeUnit` / `changeMinimum` / `removeImage` / `uploadImage` / `archive` / `unarchive`(UC22 単位・最低在庫・画像・状態の編集 / UC23 アーカイブ・復元)
- **member 可のまま(`requireMember`)**: `adopt`(UC10 採用)/ `addCustom`(UC13 カスタム追加)/ `setWanted`(UC19 手動で買い物リスト操作)

`RolePermissions` テーブル: 世帯主=全 capability / メンバー=在庫編集のみ / 閲覧者=なし。よって「マスタ管理」要求でメンバーは `OwnerRequiredException`(→ `runGuarded` で `RpcError.Unauthorized` に翻訳)。

---

## File Structure

- `domain/.../inventory/shopping/ShoppingList.kt`(変更): `from(stocks, wantedProductIds)` companion 追加
- `domain/.../household/Household.kt`(変更): `requireCanManageMaster(by)` 追加
- `backend/core/.../application/service/product/ProductService.kt`(変更): `ShoppingList.from` へ委譲
- `backend/core/.../application/service/product/ProductRegisterService.kt`(変更): マスタ編集 6 メソッドの認可を マスタ管理 に
- `backend/core/.../infrastructure/datasource/household/HouseholdHydration.kt`(削除)
- `backend/core/.../infrastructure/datasource/household/HouseholdDataSource.kt`(変更): inline
- `backend/core/.../infrastructure/datasource/invitation/InvitationHydration.kt`(削除)
- `backend/core/.../infrastructure/datasource/invitation/InvitationDataSource.kt`(変更): inline
- `backend/api/.../configuration/guard/SessionGuard.kt`(変更): 構造化ログ
- `backend/api/.../configuration/routing/RoutingConfiguration.kt`(変更): AUTH_* fail-fast 抽出
- テスト: `ShoppingListTest`(既存・追記)/ `HouseholdTest`(新規)/ `ProductRegisterServiceTest`(既存・追記)/ `AuthSettingsTest`(新規)

---

## Task 1(2-1): 買い物リスト合成を domain read-model のファクトリへ

**狙い:** ProductService(application)の `.map { ShoppingEntry(...) }` 合成を domain に引き込む。`ShoppingList.from` に置くことで read-model→集約の依存方向を保つ。

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingList.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt:55-69`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/inventory/shopping/ShoppingListTest.kt`(既存に追記)

- [ ] **Step 1: 失敗するテストを書く**

**重要: domain の commonTest は `kotlin.test.@Test` + Kotest assertions スタイル(FunSpec 不可)。** 既存 `ShoppingListTest` は `class ShoppingListTest { ... @Test fun ... }` 形式で、`stock(name, minimum, quantity)` ヘルパー(Stock を作る)を持つ。これを再利用する。

既存 import に追加:
```kotlin
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
```
`class ShoppingListTest { ... }` の中に `@Test` を追加:
```kotlin
    @Test
    fun fromは希望商品集合に含まれる商品だけ手動希望フラグを立てる() {
        val wanted = stock("水", minimum = 1, quantity = 0)
        val other = stock("米", minimum = 1, quantity = 0)
        val stocks = Stocks(listOf(wanted, other))

        val list = ShoppingList.from(stocks, setOf(wanted.product.id))

        list.list.map { it.stock.product.id to it.manuallyWanted() } shouldBe
            listOf(wanted.product.id to true, other.product.id to false)
    }
```
（`manuallyWanted()` は `Wanted.invoke()` で Boolean を取り出す。`stock()` ヘルパーは既存。）

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "*ShoppingListTest*"`
Expected: FAIL（`from` 未定義で unresolved reference）

- [ ] **Step 3: `ShoppingList.from` を実装**

`ShoppingList.kt` を編集。import 追加:
```kotlin
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
```
data class の `}` 直前に companion を追加:
```kotlin
    companion object {
        /** Stock 集合と「手動希望の商品 ID 集合」から買い物リスト read-model を合成する。 */
        fun from(
            stocks: Stocks,
            wantedProductIds: Set<ProductId>,
        ): ShoppingList = ShoppingList(stocks.list.map { ShoppingEntry(it, Wanted(it.product.id in wantedProductIds)) })
    }
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "*ShoppingListTest*"`
Expected: PASS

- [ ] **Step 5: ProductService を委譲に変更**

`ProductService.kt:55-69` の `shoppingList` を:
```kotlin
    fun shoppingList(
        householdId: HouseholdId,
        actor: ResidentId,
    ): ShoppingList {
        householdRepository.findById(householdId).requireMember(actor)
        val stocks = stockRepository.listByHousehold(householdId)
        val wantedIds = productRepository.listWanted(householdId).list.map { it.id }.toSet()
        return ShoppingList.from(stocks, wantedIds)
    }
```
未使用になる import を削除: `ShoppingEntry`、`Wanted`（`ShoppingList` は残す）。

- [ ] **Step 6: ビルドとテスト**

Run: `./gradlew :domain:jvmTest :backend:core:test`
Expected: PASS（`ProductServiceTest` 既存も green のまま）

- [ ] **Step 7: コミット**

```bash
git add domain/src backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt
git commit -m "refactor(domain): 買い物リスト合成を ShoppingList.from に引き込み ProductService を委譲化"
```

---

## Task 2(2-4): 商品マスタ編集の認可を「マスタ管理」に(挙動変更)

**狙い:** マスタ編集系メソッドの認可を `requireMember`(誰でも)から `マスタ管理`(世帯主のみ)に是正。docs/spec の規定に一致させる。

### 2a. domain: `Household.requireCanManageMaster`

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt`
- Test: `domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdTest.kt`（新規）

- [ ] **Step 1: 失敗するテストを書く（新規ファイル）**

`HouseholdTest.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import net.brightroom.mindstock.domain.exception.MembershipRequiredException
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.household.member.HouseholdMember
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.household.member.Members
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.ResidentProfile
import kotlin.test.Test

class HouseholdTest {
    private val ownerId = ResidentId.create()
    private val memberId = ResidentId.create()
    private val viewerId = ResidentId.create()

    private fun resident(id: ResidentId, name: String) = Resident(id, ResidentProfile(DisplayName(name)))

    private fun household(): Household =
        Household(
            HouseholdId.create(),
            HouseholdProfile(HouseholdName("わが家")),
            Members(
                listOf(
                    HouseholdMember(resident(ownerId, "おや"), HouseholdMemberRole.世帯主),
                    HouseholdMember(resident(memberId, "こ"), HouseholdMemberRole.メンバー),
                    HouseholdMember(resident(viewerId, "みる"), HouseholdMemberRole.閲覧者),
                ),
            ),
        )

    @Test
    fun requireCanManageMaster_世帯主は通る() {
        shouldNotThrowAny { household().requireCanManageMaster(ownerId) }
    }

    @Test
    fun requireCanManageMaster_メンバーはオーナー権限不足で弾く() {
        shouldThrow<OwnerRequiredException> { household().requireCanManageMaster(memberId) }
    }

    @Test
    fun requireCanManageMaster_閲覧者はオーナー権限不足で弾く() {
        shouldThrow<OwnerRequiredException> { household().requireCanManageMaster(viewerId) }
    }

    @Test
    fun requireCanManageMaster_非メンバーはメンバー必須で弾く() {
        shouldThrow<MembershipRequiredException> { household().requireCanManageMaster(ResidentId.create()) }
    }
}
```
（domain commonTest は `kotlin.test.@Test` + Kotest assertions スタイル。FunSpec は使わない。）

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :domain:jvmTest --tests "*HouseholdTest*"`
Expected: FAIL（`requireCanManageMaster` 未定義）

- [ ] **Step 3: `requireCanManageMaster` を実装**

`Household.kt` の `requireCanManage`(:84-86)の直後に追加:
```kotlin
    /**
     * 商品マスタ(単位・最低在庫・画像・状態)編集の認可。
     * 非メンバーは MembershipRequiredException、メンバーだが マスタ管理 capability を持たなければ OwnerRequiredException。
     */
    fun requireCanManageMaster(by: ResidentId) {
        requireMember(by)
        requireCapability(by, HouseholdCapability.マスタ管理)
    }
```
（`requireMember` を先に呼ぶことで非メンバーを `MembershipRequiredException` で弾く。`requireCapability` 内の `members.roleOf(by)` は非メンバーに `ResourceNotFoundException` を投げるため、順序が重要。)

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :domain:jvmTest --tests "*HouseholdTest*"`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Household.kt domain/src/commonTest/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdTest.kt
git commit -m "feat(domain): Household.requireCanManageMaster を追加(マスタ管理 capability の認可)"
```

### 2b. application: ProductRegisterService のマスタ編集を マスタ管理 に

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt`
- Test: `backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceTest.kt`（既存に追記）

- [ ] **Step 1: 失敗するテストを書く**

`ProductRegisterServiceTest.kt` に、メンバーロールの世帯を返すヘルパーと test を追記。まず既存 import に追加:
```kotlin
import net.brightroom.mindstock.domain.exception.OwnerRequiredException
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.image.RawImageUpload
```
`FunSpec({ ... })` 内、`householdWithActor()` の隣にメンバー版ヘルパーを追加:
```kotlin
        fun householdWithMemberActor(): Household {
            val resident = Resident(actor, ResidentProfile(DisplayName("こ")))
            return Household(
                householdId,
                HouseholdProfile(HouseholdName("わが家")),
                Members(listOf(HouseholdMember(resident, HouseholdMemberRole.メンバー))),
            )
        }

        fun customProduct(): Product =
            Product.custom(ProductName("水"), Barcode.Unlinked, ProductUnit("本"), MinimumStock(1))
```
test 群を追加(マスタ編集 6 メソッド × メンバー拒否):
```kotlin
        test("changeUnit: メンバーは OwnerRequiredException でマスタ編集不可") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithMemberActor()
            shouldThrow<OwnerRequiredException> { service.changeUnit(product.id, ProductUnit("缶"), actor) }
            verify(exactly = 0) { productRegisterRepository.appendRevision(any()) }
        }

        test("changeMinimum: メンバーは OwnerRequiredException") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithMemberActor()
            shouldThrow<OwnerRequiredException> { service.changeMinimum(product.id, MinimumStock(5), actor) }
        }

        test("archive: メンバーは OwnerRequiredException") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithMemberActor()
            shouldThrow<OwnerRequiredException> { service.archive(product.id, actor) }
        }

        test("unarchive: メンバーは OwnerRequiredException") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithMemberActor()
            shouldThrow<OwnerRequiredException> { service.unarchive(product.id, actor) }
        }

        test("removeImage: メンバーは OwnerRequiredException") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithMemberActor()
            shouldThrow<OwnerRequiredException> { service.removeImage(product.id, actor) }
        }

        test("changeUnit: 世帯主はマスタ編集できる") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithActor()
            every { productRepository.findById(product.id) } returns product
            service.changeUnit(product.id, ProductUnit("缶"), actor)
            verify { productRegisterRepository.appendRevision(any()) }
        }

        test("setWanted: メンバーでも可(在庫編集 capability)") {
            val product = customProduct()
            every { productRepository.householdOf(product.id) } returns householdId
            every { householdRepository.findById(householdId) } returns householdWithMemberActor()
            service.setWanted(product.id, true, actor)
            verify { productRegisterRepository.setWanted(product.id, true) }
        }
```
（`uploadImage` は suspend かつ imageStorage を絡めるため、認可は他 5 メソッドと同一経路。代表として上記でカバーし、必要なら別 suspend test を追加可。`ProductRegisterServiceUploadImageTest` が既存にある点に留意。）

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterServiceTest*"`
Expected: FAIL（マスタ編集メソッドが現状 `requireMember` のため、メンバーで例外が出ず `shouldThrow` が失敗）

- [ ] **Step 3: ProductRegisterService の認可を是正**

`ProductRegisterService.kt` の `authorizeProduct`(:33-36)の隣に、マスタ管理用の認可ヘルパーを追加:
```kotlin
    private fun authorizeMaster(
        householdId: HouseholdId,
        actor: ResidentId,
    ) = householdRepository.findById(householdId).requireCanManageMaster(actor)

    private fun authorizeMasterByProduct(
        productId: ProductId,
        actor: ResidentId,
    ) = authorizeMaster(productRepository.householdOf(productId), actor)
```
次の 6 メソッドの先頭 `authorizeProduct(productId, actor)` を `authorizeMasterByProduct(productId, actor)` に置換:
`changeUnit`(:78) / `changeMinimum`(:88) / `removeImage`(:98) / `uploadImage`(:108) / `archive`(:119) / `unarchive`(:128)。

`adopt`(:45 `authorize`)/ `addCustom`(:62 `authorize`)/ `setWanted`(:138 `authorizeProduct`)は**変更しない**(member 可)。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:core:test --tests "*ProductRegisterService*"`
Expected: PASS（新規 + 既存テスト全 green。既存の「changeUnit は世帯メンバーでなければ MembershipRequiredException」test は、`requireCanManageMaster` が先頭で `requireMember` を呼ぶため引き続き `MembershipRequiredException` で green のまま=変更不要）

- [ ] **Step 5: コミット**

```bash
git add backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterService.kt backend/core/src/test/kotlin/net/brightroom/mindstock/application/service/product/ProductRegisterServiceTest.kt
git commit -m "fix(application): 商品マスタ編集の認可を マスタ管理(世帯主のみ)に是正

挙動変更: メンバー権限ではマスタ編集(単位/最低在庫/画像/アーカイブ)が不可に。
docs/spec の UC22/23 規定に一致させる。adopt/addCustom/setWanted は member 可のまま。"
```

---

## Task 3(2-6): no-op hydration ラッパーの削除とインライン化

**狙い:** コンストラクタを呼ぶだけの hydration 関数を消し、Hydration は実マッピングを持つものだけ残す。

**Files:**
- Modify: `backend/core/.../infrastructure/datasource/household/HouseholdDataSource.kt`
- Delete: `backend/core/.../infrastructure/datasource/household/HouseholdHydration.kt`
- Modify: `backend/core/.../infrastructure/datasource/invitation/InvitationDataSource.kt`
- Delete: `backend/core/.../infrastructure/datasource/invitation/InvitationHydration.kt`

- [ ] **Step 1: HouseholdDataSource をインライン化**

`HouseholdDataSource.kt` の import に追加:
```kotlin
import net.brightroom.mindstock.domain.model.household.HouseholdProfile
import net.brightroom.mindstock.domain.model.household.member.Members
```
`hydrate`(:76)の `return assembleHousehold(id, name, members)` を:
```kotlin
        return Household(id, HouseholdProfile(name), Members(members))
```
`currentMembers`(:154)の `member(resident, role)` を:
```kotlin
            HouseholdMember(resident, role)
```

- [ ] **Step 2: InvitationDataSource をインライン化**

`InvitationDataSource.kt` の `assembleInvitation(...)`(:53-58)を:
```kotlin
            Invitation(
                householdId = HouseholdId(base[InvitationsTable.householdId]),
                code = code,
                grantedRole = base[InvitationsTable.grantedRole],
                validity = validity,
            )
```
（`Invitation` は import 済み。`InvitationsTable` も import 済み。）

- [ ] **Step 3: Hydration ファイルを削除**

```bash
git rm backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdHydration.kt
git rm backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/invitation/InvitationHydration.kt
```

- [ ] **Step 4: 残参照ゼロを確認しビルド**

Run:
```bash
grep -rn "assembleHousehold\|assembleInvitation\|\bmember(resident" backend/core/src/main
./gradlew :backend:core:compileKotlin
```
Expected: grep ヒットなし / コンパイル成功

- [ ] **Step 5: コミット**

```bash
git add backend/core/src
git commit -m "refactor(infra): no-op な hydration ラッパーを削除し呼び出し元へインライン化"
```

---

## Task 4(2-7): SessionGuard の unhandled ログに構造化フィールド追加

**狙い:** 予期せぬ例外のトレース性向上。`auth_subject` / `resident_id` を付与。

**Files:**
- Modify: `backend/api/.../configuration/guard/SessionGuard.kt:80-82`

- [ ] **Step 1: ログ行に構造化フィールドを追加**

`runGuarded` の `catch (e: Throwable)`(:80-82)を:
```kotlin
    } catch (e: Throwable) {
        val residentId = (session as? MindstockSession.Registered)?.residentId
        logger.error(e) {
            "unhandled exception during RPC call_id=${session.callId} " +
                "auth_subject=${session.identity.subject} resident_id=$residentId"
        }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
```
（`AuthSubject.toString()` は生の subject を返す。`Unregistered` のとき `resident_id=null`。）

- [ ] **Step 2: ビルドと既存テスト**

Run: `./gradlew :backend:api:compileKotlin :backend:api:test --tests "*SessionGuardTest*"`
Expected: PASS（ログ文字列の変更のみ・分岐挙動は不変）

- [ ] **Step 3: コミット**

```bash
git add backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt
git commit -m "refactor(api): SessionGuard の unhandled ログに auth_subject/resident_id を付与"
```

---

## Task 5(2-8): AUTH_* の起動時 fail-fast ガード

**狙い:** `AUTH_ISSUER`/`AUTH_AUDIENCE`/`AUTH_JWKS_URL` 未設定時に、JWT 検証で深く死ぬのでなく起動時に明示エラーで即死させ、`.env.zitadel` 生成(`mise run up`)を案内する。

**Files:**
- Create: `backend/api/.../configuration/auth/AuthSettings.kt`
- Modify: `backend/api/.../configuration/routing/RoutingConfiguration.kt:62-74`
- Test: `backend/api/.../configuration/auth/AuthSettingsTest.kt`(新規)

- [ ] **Step 1: 失敗するテストを書く(新規)**

`AuthSettingsTest.kt`:
```kotlin
package net.brightroom.mindstock.configuration.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.MapApplicationConfig

class AuthSettingsTest :
    FunSpec({
        fun config(vararg pairs: Pair<String, String>) =
            MapApplicationConfig(*pairs.toList().toTypedArray())

        test("全て揃っていれば AuthSettings を返す") {
            val cfg =
                config(
                    "external.auth.issuer" to "https://idp.example",
                    "external.auth.audience" to "aud",
                    "external.auth.jwks-url" to "https://idp.example/jwks",
                )
            val settings = requireAuthSettings(cfg)
            settings.issuer shouldBe "https://idp.example"
            settings.audience shouldBe "aud"
            settings.jwksUrl shouldBe "https://idp.example/jwks"
        }

        test("issuer が未設定なら案内付きで即時エラー") {
            val cfg =
                config(
                    "external.auth.audience" to "aud",
                    "external.auth.jwks-url" to "https://idp.example/jwks",
                )
            val ex = shouldThrow<IllegalStateException> { requireAuthSettings(cfg) }
            ex.message!! shouldContain "AUTH_ISSUER"
            ex.message!! shouldContain "mise run up"
        }

        test("audience が空文字でも即時エラー") {
            val cfg =
                config(
                    "external.auth.issuer" to "https://idp.example",
                    "external.auth.audience" to "",
                    "external.auth.jwks-url" to "https://idp.example/jwks",
                )
            shouldThrow<IllegalStateException> { requireAuthSettings(cfg) }
        }
    })
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew :backend:api:test --tests "*AuthSettingsTest*"`
Expected: FAIL（`requireAuthSettings` / `AuthSettings` 未定義）

- [ ] **Step 3: AuthSettings と requireAuthSettings を実装(新規ファイル)**

`AuthSettings.kt`:
```kotlin
package net.brightroom.mindstock.configuration.auth

import io.ktor.server.config.ApplicationConfig

/** JWT 検証に必要な外部 IdP 設定。未設定なら起動時に fail-fast する。 */
data class AuthSettings(
    val issuer: String,
    val audience: String,
    val jwksUrl: String,
)

/**
 * `external.auth` 配下の必須設定を読み、未設定/空文字なら案内付きで即時エラー。
 * デフォルト値は与えない(誤った既定値で起動する方が危険)。
 */
fun requireAuthSettings(config: ApplicationConfig): AuthSettings {
    val auth = config.config("external.auth")
    fun required(key: String, env: String): String {
        val value = auth.propertyOrNull(key)?.getString()
        check(!value.isNullOrBlank()) {
            "external.auth.$key (env $env) が未設定です。`.env.zitadel` を生成しましたか?(`mise run up`)"
        }
        return value
    }
    return AuthSettings(
        issuer = required("issuer", "AUTH_ISSUER"),
        audience = required("audience", "AUTH_AUDIENCE"),
        jwksUrl = required("jwks-url", "AUTH_JWKS_URL"),
    )
}
```
注意: テストは `MapApplicationConfig` 直下に `external.auth.*` を置くので、`config.config("external.auth")` が空にならないよう、実装は **`config` をそのまま受け取り内部で `.config("external.auth")` する**。テストの key も `external.auth.issuer` 等にしてある(`MapApplicationConfig` はドット区切りで階層を表現)。

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :backend:api:test --tests "*AuthSettingsTest*"`
Expected: PASS
（万一 `MapApplicationConfig` の `.config("external.auth")` の挙動が想定と異なる場合は、実装側を `config.propertyOrNull("external.auth.$key")` 直読みに変える。テストを先に green にしてから RoutingConfiguration を繋ぐ。）

- [ ] **Step 5: RoutingConfiguration を fail-fast 経由に変更**

`RoutingConfiguration.kt` の import に追加:
```kotlin
import net.brightroom.mindstock.configuration.auth.requireAuthSettings
```
`val authConfig = environment.config.config("external.auth")`(:62)を削除し、代わりに:
```kotlin
    val authSettings = requireAuthSettings(environment.config)
```
`install(MindstockAuthPlugin)` ブロック(:66-74)を:
```kotlin
    install(MindstockAuthPlugin) {
        jwkProvider =
            JwkProviderBuilder(URI(authSettings.jwksUrl).toURL())
                .cached(10, 1, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
        issuer = authSettings.issuer
        audience = authSettings.audience
        this.residentRepository = residentRepository
    }
```

- [ ] **Step 6: ビルドと api テスト全体**

Run: `./gradlew :backend:api:compileKotlin :backend:api:test`
Expected: PASS（e2e の auth テストが `external.auth.*` を設定済みであることを確認。未設定なら e2e 側のテスト設定に `external.auth.*` を追加。`SingleEndpointRpcTest` 等が `MapApplicationConfig`/`application.yaml` 経由でどう auth を渡しているか確認し、`requireAuthSettings` が通る値を与える)

- [ ] **Step 7: コミット**

```bash
git add backend/api/src
git commit -m "feat(api): AUTH_* 未設定を起動時 fail-fast 化(.env.zitadel 生成を案内)"
```

---

## 仕上げ: フェーズ全体検証

- [ ] **Step 1: フルテスト**

Run: `./gradlew test`
Expected: 全 green

- [ ] **Step 2: 統合テスト(DB あり環境)**

Run: `mise run up`(未起動なら)→ `./gradlew integrationTest`
Expected: 既存統合テスト green(本フェーズは新規統合テストを追加しない。2-4 の認可は domain+Service テストで双方向検証済み)

- [ ] **Step 3: 残参照・挙動確認**

- `grep -rn "assembleHousehold\|assembleInvitation" backend/core/src` がゼロ
- 2-4 の挙動変更(メンバーはマスタ編集不可)を PR 説明に明記

- [ ] **Step 4: finishing-a-development-branch**

superpowers:finishing-a-development-branch で PR 作成。PR 説明に「**挙動変更: 商品マスタ編集(単位/最低在庫/画像/アーカイブ)が世帯主限定になる**」を必ず記載。

---

## Self-Review メモ

- **Spec coverage:** 2-1(ShoppingList.from)/2-4(マスタ管理 authz)/2-6(hydration 削除)/2-7(構造化ログ)/2-8(fail-fast)を各 Task で実装。2-2/2-3 は見送り確定・2-5 欠番。
- **挙動変更は 2-4 のみ。** PR に明記。
- **型移動なし・core→rpc 依存追加なし**(腐敗防止層の判断に従い ActivityFeed/InvitationPreview は :rpc のまま)。
- **解決済み:** `requireCanManageMaster` は `requireMember` を先に呼ぶため、非メンバーは `MembershipRequiredException`(既存 `ProductRegisterServiceTest` の該当 test は green のまま)。`Members.roleOf` の非メンバー時 `ResourceNotFoundException` を踏まない順序にしてある。
- **未確定の実挙動依存(実装時にテストを先に green にして確認):** (b) `MapApplicationConfig` の `.config("external.auth")` 挙動(必要なら実装を `propertyOrNull("external.auth.$key")` 直読みに切替)。(c) e2e auth テストへの `external.auth.*` 供給。
