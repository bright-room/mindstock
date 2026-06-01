# P3: `:rpc` — `@Rpc` service interface + RpcResult / RpcError + DTO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:rpc` モジュールに、フロント/バックエンド双方が共有する RPC 契約(`@Rpc` service interface・`RpcResult`/`RpcError`・必要最小の DTO)をコンテキスト別 + 登録系/参照系に分けて宣言する。実装(Controller)は P5。

**Architecture:** 現行設計 [`02-iconix.md` B-5](../specs/2026-05-31-mindstock-full-replace-design/02-iconix.md) の 5 コンテキスト(Resident / Household / Catalog / Product / Stock)に則り、各コンテキストを **参照系 `<Ctx>RpcService`** と **登録系 `<Ctx>RegisterRpcService`** に分割する(Catalog は読み取りのみ → 登録系なし)。戻り値は全メソッド `RpcResult<T, RpcError>`(`T` は non-null)。引数・戻り値は原則ドメイン VO / 集約 / ファーストクラスコレクションを直接使い、ドメインで表せない射影だけ `:rpc` 内に DTO を作る。`actor`/`AuthIdentity` は引数で受けず P5 Controller が session から確定する(なりすまし防止)。

**Tech Stack:** Kotlin Multiplatform(commonMain / commonTest)、kotlinx-rpc 0.10.2(`@kotlinx.rpc.annotations.Rpc`)、kotlinx-serialization、Kotest assertions、`KrpcJson`(`:shared`、`ClassDiscriminatorMode.POLYMORPHIC`)。

---

## 前提・設計判断(実装前に必ず読む)

- **エラー語彙はルール準拠**: `.claude/rules/rpc-and-transactions.md` が確定済み。`RpcResult<out T, out E>`(`Ok`/`Err`)、`RpcError` sealed interface(`Unauthorized`/`NotFound`/`BadRequest`/`Conflict`/`Internal`)。teardown 前実装の形をそのまま復元する。
- **ドメイン FCC が未構築**: 現行 `:domain` には `StockMovements`/`Members`/`ShoppingList` しかファーストクラスコレクションが無い。一覧返却に raw `List<T>` は使えない(ルール)ため、本プラン Task 1 で `Stocks`/`Products`/`CatalogItems`/`Households` を `:domain` に最小追加する(`:rpc` ではなく `:domain` を触る点に注意)。domain 固有操作は YAGNI で足さず `val list` + `fun size()` のみ。
- **`KrpcJson` は `:shared` に既存**(`shared/.../extensions/kotlinx/serialization/Json.kt`)。再導入不要。round-trip テストはこれを使う。`:rpc` の commonTest に `:shared` 依存を足す。
- **テスト方針**(`.claude/rules/testing.md`): interface / DTO / FCC は「ロジックを持たない宣言」なので個別テストは書かない(コンパイル通過で足りる)。**意味のあるテストは `KrpcJson` 経由の serialization round-trip だけ**(sealed の polymorphic discriminator と value class が wire で壊れないことの確認。`krpc-ws-pipeline-gotchas` で踏んだ静かな失敗の予防)。対象は `RpcError`・`RpcResult` の envelope と、深いグラフを持つ代表ペイロード(`Product`)1 本。
- **パッケージ**: `net.brightroom.mindstock.rpc.result`(`RpcResult`/`RpcError`)、`net.brightroom.mindstock.rpc.<ctx>`(各サービス + DTO)。
- **メソッド名規約**(ルール): domain command 名そのまま。`Query`/`Command` suffix を付けない。

---

## 作成・変更するファイル

**`:domain`(Task 1):**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Households.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Products.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stocks.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItems.kt`

**`:rpc`(Task 2–10):**
- Modify: `rpc/build.gradle.kts`(commonTest 依存追加)
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcError.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcResult.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRegisterRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRegisterRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/InvitationPreview.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/catalog/CatalogRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRegisterRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/AddCustomProductRequest.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/ActivityFeed.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRegisterRpcService.kt`
- Test: `rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcErrorSerializationTest.kt`
- Test: `rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcResultSerializationTest.kt`

---

## サービス分割サマリ(承認済み)

| コンテキスト | 参照系 | 登録系 |
|---|---|---|
| Resident | `ResidentRpcService`: `me` | `ResidentRegisterRpcService`: `registerDisplayName`, `rename` |
| Household | `HouseholdRpcService`: `list`, `previewInvite` | `HouseholdRegisterRpcService`: `create`, `rename`, `leave`, `changeRole`, `removeMember`, `createInvite`, `revokeInvite`, `join` |
| Catalog | `CatalogRpcService`: `search`, `lookupByJan` | (なし) |
| Product | `ProductRpcService`: `list`, `listArchived`, `shoppingList` | `ProductRegisterRpcService`: `adopt`, `addCustom`, `changeUnit`, `changeImage`, `changeMinimum`, `archive`, `unarchive`, `setWanted` |
| Stock | `StockRpcService`: `history`, `activity` | `StockRegisterRpcService`: `replenish`, `consume`, `correct` |

> **改訂(承認済み)**: ① `replenish`/`consume`/`correct` から `occurredAt` 引数を外す(サーバ時刻。P5 で `OccurredAt.now()`)。② `activity`(UC24)は商品参照を持たない `StockMovement` を世帯横断で返すと「何の商品か」が落ちるため、`ActivityFeed`(`ActivityEntry(product, movement)` のリスト)view DTO を `:rpc` に新設して返す。③ `setWanted` の `householdId` は冗長(`ProductId` が global UUID で世帯一意)なので削除し `productId` のみにする。

---

## Task 1: `:domain` に未構築のファーストクラスコレクションを追加

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Households.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Products.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stocks.kt`
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItems.kt`

> ロジックを持たないため(`.claude/rules/testing.md`)テストは書かない。`val list` + `fun size()` のみ。domain 固有操作は P4/P5 で必要になった時に追加する(YAGNI)。

- [ ] **Step 1: `Households` を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/Households.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable

@Serializable
data class Households(
    val list: List<Household>,
) {
    fun size(): Int = list.size
}
```

- [ ] **Step 2: `Products` を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/product/Products.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.product

import kotlinx.serialization.Serializable

@Serializable
data class Products(
    val list: List<Product>,
) {
    fun size(): Int = list.size
}
```

- [ ] **Step 3: `Stocks` を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/inventory/stock/Stocks.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.inventory.stock

import kotlinx.serialization.Serializable

@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun size(): Int = list.size
}
```

- [ ] **Step 4: `CatalogItems` を作成**

`domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/item/CatalogItems.kt`:
```kotlin
package net.brightroom.mindstock.domain.model.catalog.item

import kotlinx.serialization.Serializable

@Serializable
data class CatalogItems(
    val list: List<CatalogItem>,
) {
    fun size(): Int = list.size
}
```

- [ ] **Step 5: `:domain` のコンパイルを確認**

Run: `./gradlew :domain:build`
Expected: BUILD SUCCESSFUL(既存テストも緑のまま)

- [ ] **Step 6: Commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model
git commit -m "feat(domain): 一覧返却用のファーストクラスコレクション(Households/Products/Stocks/CatalogItems)を追加"
```

---

## Task 2: `:rpc` の commonTest 依存を追加

**Files:**
- Modify: `rpc/build.gradle.kts`

- [ ] **Step 1: commonTest / jvmTest 依存を追加**

`rpc/build.gradle.kts` の `kotlin { sourceSets { ... } }` 内、`commonMain { ... }` ブロックの直後に以下を追加する(`commonMain` ブロックは現状維持):
```kotlin
        commonTest {
            dependencies {
                implementation(projects.shared)
                implementation(libs.kotest.assertions.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
```

> `kotlin("test")` と `useJUnitPlatform()` は規約プラグイン `net.brightroom.mindstock.kmp-shared` が付与済み。`projects.shared` は `KrpcJson` を使うため、`kotest.assertions.core` は `shouldBe` のため、`kotest.runner.junit5` は JVM でテストを走らせるため。

- [ ] **Step 2: 設定が壊れていないことを確認**

Run: `./gradlew :rpc:build`
Expected: BUILD SUCCESSFUL(まだソースは空なので即終了)

- [ ] **Step 3: Commit**

```bash
git add rpc/build.gradle.kts
git commit -m "build(rpc): round-trip テスト用に commonTest 依存(shared/kotest)を追加"
```

---

## Task 3: `RpcError`(sealed エラー語彙)

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcError.kt`
- Test: `rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcErrorSerializationTest.kt`

- [ ] **Step 1: round-trip テストを書く(失敗する)**

`rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcErrorSerializationTest.kt`:
```kotlin
package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class RpcErrorSerializationTest {
    @Test
    fun NotFound_を_KrpcJson_で往復できる() {
        val error: RpcError = RpcError.NotFound("household not found: 42")
        val json = KrpcJson.encodeToString(error)
        val back = KrpcJson.decodeFromString<RpcError>(json)
        back shouldBe error
    }

    @Test
    fun BadRequest_は_field_と_reason_を保持して往復できる() {
        val error: RpcError = RpcError.BadRequest(field = "displayName", reason = "must not be blank")
        val back = KrpcJson.decodeFromString<RpcError>(KrpcJson.encodeToString(error))
        back.shouldBeInstanceOf<RpcError.BadRequest>()
        back shouldBe error
    }
}
```

- [ ] **Step 2: テストがコンパイル失敗することを確認**

Run: `./gradlew :rpc:compileTestKotlinJvm`
Expected: FAIL（`RpcError` が未定義でコンパイルエラー）

- [ ] **Step 3: `RpcError` を実装**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcError.kt`:
```kotlin
package net.brightroom.mindstock.rpc.result

import kotlinx.serialization.Serializable

/**
 * API 全体で共有する RPC エラー語彙。
 *
 * read/write を分けず、ありうるエラー集合の和集合を持つ。クライアントは when の
 * 網羅性検証で新しい variant の追加に必ず気付ける。例外 → variant の翻訳は
 * P5 の Controller が担う(domain は RpcError を import しない)。
 */
@Serializable
sealed interface RpcError {
    /** 認証失敗 / トークン期限切れ / Principal 未解決 等。 */
    @Serializable
    data class Unauthorized(
        val reason: String,
    ) : RpcError

    /**
     * 単一値の resource が見つからなかった。message は server 側の
     * `ResourceNotFoundException` の reason がそのまま乗る(例: "household not found: $id")。
     */
    @Serializable
    data class NotFound(
        val message: String,
    ) : RpcError

    /** 入力検証エラー(IAE の翻訳先)。 */
    @Serializable
    data class BadRequest(
        val field: String,
        val reason: String,
    ) : RpcError

    /** 競合(重複登録・前提崩れ 等)。 */
    @Serializable
    data class Conflict(
        val reason: String,
    ) : RpcError

    /** 想定外のサーバエラー。クライアントにスタックトレースは漏らさない。 */
    @Serializable
    data class Internal(
        val reason: String,
    ) : RpcError
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :rpc:jvmTest --tests "net.brightroom.mindstock.rpc.result.RpcErrorSerializationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcError.kt rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcErrorSerializationTest.kt
git commit -m "feat(rpc): RpcError(sealed エラー語彙)を追加"
```

---

## Task 4: `RpcResult`(成功/失敗の sealed 二択)

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcResult.kt`
- Test: `rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcResultSerializationTest.kt`

- [ ] **Step 1: round-trip テストを書く(失敗する)**

`rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcResultSerializationTest.kt`:
```kotlin
package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.domain.model.resident.profile.Profile
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class RpcResultSerializationTest {
    @Test
    fun Ok_を_KrpcJson_で往復できる() {
        val resident = Resident(ResidentId.create(), Profile(DisplayName("たろう")))
        val ok: RpcResult<Resident, RpcError> = RpcResult.Ok(resident)
        val back = KrpcJson.decodeFromString<RpcResult<Resident, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }

    @Test
    fun Err_を_KrpcJson_で往復できる() {
        val err: RpcResult<Resident, RpcError> = RpcResult.Err(RpcError.NotFound("resident not found"))
        val back = KrpcJson.decodeFromString<RpcResult<Resident, RpcError>>(KrpcJson.encodeToString(err))
        back shouldBe err
    }
}
```

- [ ] **Step 2: テストがコンパイル失敗することを確認**

Run: `./gradlew :rpc:compileTestKotlinJvm`
Expected: FAIL（`RpcResult` が未定義でコンパイルエラー）

- [ ] **Step 3: `RpcResult` を実装**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcResult.kt`:
```kotlin
package net.brightroom.mindstock.rpc.result

import kotlinx.serialization.Serializable

/**
 * RPC メソッドの戻り値共通型。成功 [Ok] と失敗 [Err] の sealed 二択。
 *
 * クライアント側は `when (r) { is Ok -> ...; is Err -> ... }` で網羅性検証可能。
 * `T` は non-null(`T?` 禁止)。「不在」は [Err] の [RpcError.NotFound] で表す。
 */
@Serializable
sealed interface RpcResult<out T, out E> {
    @Serializable
    data class Ok<T>(
        val value: T,
    ) : RpcResult<T, Nothing>

    @Serializable
    data class Err<E>(
        val error: E,
    ) : RpcResult<Nothing, E>
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew :rpc:jvmTest --tests "net.brightroom.mindstock.rpc.result.RpcResultSerializationTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/result/RpcResult.kt rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/RpcResultSerializationTest.kt
git commit -m "feat(rpc): RpcResult<T, RpcError>(成功/失敗の sealed 二択)を追加"
```

---

## Task 5: Resident コンテキストのサービス(参照系 + 登録系)

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRegisterRpcService.kt`

> interface は宣言のみ。テストは書かず、コンパイル + 最終 Task の round-trip で担保する。`registerDisplayName` は「JWT は有効だが Resident 未登録でも通る」用途。public/authenticated の振り分け(認証レルム)は P5 の routing で扱う。

- [ ] **Step 1: 参照系 `ResidentRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.resident

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ResidentRpcService {
    /** ログイン中の Resident を取得(UC2 の `me`)。actor は session 由来。 */
    suspend fun me(): RpcResult<Resident, RpcError>
}
```

- [ ] **Step 2: 登録系 `ResidentRegisterRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident/ResidentRegisterRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.resident

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.resident.Resident
import net.brightroom.mindstock.domain.model.resident.profile.DisplayName
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ResidentRegisterRpcService {
    /** 初回:表示名を登録する(UC2)。AuthIdentity は session 由来(引数で受けない)。 */
    suspend fun registerDisplayName(displayName: DisplayName): RpcResult<Resident, RpcError>

    /** 表示名を変更する。 */
    suspend fun rename(displayName: DisplayName): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 3: コンパイルを確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/resident
git commit -m "feat(rpc): Resident コンテキストの RPC service interface(参照系/登録系)を追加"
```

---

## Task 6: Household コンテキストのサービス + `InvitationPreview` DTO

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/InvitationPreview.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRegisterRpcService.kt`

> `previewInvite`(UC4)は参加前にコードから世帯名と付与ロールを見せる射影。`Invitation` の `householdId` は internal で、joiner に世帯名を見せたいので、ドメインと形がずれる → `:rpc` に DTO を作る。`householdId` 引数は複数世帯切替(UC5)前提のため明示。

- [ ] **Step 1: `InvitationPreview` DTO を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/InvitationPreview.kt`:
```kotlin
package net.brightroom.mindstock.rpc.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole

/**
 * 招待コードのプレビュー(UC4)。参加前のユーザに見せる射影。
 * `Invitation` は内部に `householdId` を持つが、joiner には世帯名と付与ロールだけを見せる。
 */
@Serializable
data class InvitationPreview(
    val householdName: HouseholdName,
    val grantedRole: HouseholdMemberRole,
)
```

- [ ] **Step 2: 参照系 `HouseholdRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.household

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Households
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface HouseholdRpcService {
    /** ログイン中の Resident が所属する世帯一覧(UC5 の切替元)。 */
    suspend fun list(): RpcResult<Households, RpcError>

    /** 招待コードのプレビュー(UC4。参加前)。 */
    suspend fun previewInvite(code: InvitationCode): RpcResult<InvitationPreview, RpcError>
}
```

- [ ] **Step 3: 登録系 `HouseholdRegisterRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household/HouseholdRegisterRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.household

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdName
import net.brightroom.mindstock.domain.model.household.invitation.Invitation
import net.brightroom.mindstock.domain.model.household.invitation.InvitationCode
import net.brightroom.mindstock.domain.model.household.member.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.resident.identity.ResidentId
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface HouseholdRegisterRpcService {
    /** 世帯を作成する(UC3)。作成者が owner。actor は session 由来。 */
    suspend fun create(name: HouseholdName): RpcResult<Household, RpcError>

    /** 世帯名を変更する(UC6, owner のみ)。 */
    suspend fun rename(householdId: HouseholdId, name: HouseholdName): RpcResult<Unit, RpcError>

    /** 世帯から退出する(UC7)。 */
    suspend fun leave(householdId: HouseholdId): RpcResult<Unit, RpcError>

    /** メンバーの権限変更(UC9, owner のみ)。 */
    suspend fun changeRole(
        householdId: HouseholdId,
        target: ResidentId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError>

    /** メンバーを除外(UC9, owner のみ)。 */
    suspend fun removeMember(
        householdId: HouseholdId,
        target: ResidentId,
    ): RpcResult<Unit, RpcError>

    /** 招待を発行/再発行する(UC8, owner のみ。role 指定・期限なし)。 */
    suspend fun createInvite(
        householdId: HouseholdId,
        role: HouseholdMemberRole,
    ): RpcResult<Invitation, RpcError>

    /** 招待を失効する(UC8, owner のみ)。 */
    suspend fun revokeInvite(code: InvitationCode): RpcResult<Unit, RpcError>

    /** 招待コードで世帯に参加する(UC4)。 */
    suspend fun join(code: InvitationCode): RpcResult<Household, RpcError>
}
```

- [ ] **Step 4: コンパイルを確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/household
git commit -m "feat(rpc): Household コンテキストの RPC service interface + InvitationPreview DTO を追加"
```

---

## Task 7: Catalog コンテキストのサービス(参照系のみ)

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/catalog/CatalogRpcService.kt`

> Catalog は読み取りのみ(UC11 検索 / UC12 JAN 照会)。世帯独自 CatalogItem の作成は採用と一体なので Product 登録系の `addCustom` が担う。`lookupByJan` は「マスタ→外部 API→無ければ NotFound」の解決を P5 Scenario が行い、見つかれば `CatalogItem` を返す。`search` の `query: String` / `limit: Int` は presentation 境界の素のパラメータとして許容(検索クエリは VO 化しない)。

- [ ] **Step 1: `CatalogRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/catalog/CatalogRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.catalog

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItems
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface CatalogRpcService {
    /** 名前等でマスタを検索する(UC11)。 */
    suspend fun search(
        query: String,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError>

    /** JAN で照会(UC11,12。マスタ→外部 API。無ければ NotFound)。 */
    suspend fun lookupByJan(jan: Jan): RpcResult<CatalogItem, RpcError>
}
```

- [ ] **Step 2: コンパイルを確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/catalog
git commit -m "feat(rpc): Catalog コンテキストの RPC service interface(参照系)を追加"
```

---

## Task 8: Product コンテキストのサービス + `AddCustomProductRequest` DTO

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/AddCustomProductRequest.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRegisterRpcService.kt`

> `list`(UC16 在庫一覧)は数量+status を見せる画面なので `Stocks`(Stock = Product + movements)を返す。`listArchived`(UC23)はアーカイブ済 Product 一覧で在庫変動を伴わないので `Products`。`shoppingList`(UC18)は読みモデル `ShoppingList`。`setWanted`(UC19,20)の `manuallyWanted` は Stock/Product の外の独立フラグなので、Product 登録系に置く(buy-list 読みは Product 参照系の `shoppingList`)。`addCustom`(UC13)は name/unit/barcode/minimumStock の複合 → DTO にまとめる。JAN 任意は nullable 禁止のため `Barcode` sealed(`Unlinked`/`Linked`)で表す。`changeImage` の `ProductImage` は sealed(`None`/`Stored`)。

- [ ] **Step 1: `AddCustomProductRequest` DTO を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/AddCustomProductRequest.kt`:
```kotlin
package net.brightroom.mindstock.rpc.product

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock

/**
 * マスタに無い商品をその場で追加(UC13)。複合パラメータを 1 つにまとめた Request。
 * `barcode` で JAN 任意を表現(`Barcode.Unlinked` = JAN 無し / `Barcode.Linked(jan)` = JAN 有り)。
 * 採用時の `ProductUnit` は backend が `unit`(CatalogItemUnit)から構築する。
 */
@Serializable
data class AddCustomProductRequest(
    val name: CatalogItemName,
    val unit: CatalogItemUnit,
    val barcode: Barcode,
    val minimumStock: MinimumStock,
)
```

- [ ] **Step 2: 参照系 `ProductRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.product

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Products
import net.brightroom.mindstock.domain.model.inventory.shopping.ShoppingList
import net.brightroom.mindstock.domain.model.inventory.stock.Stocks
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ProductRpcService {
    /** 在庫一覧(UC16)。数量+status を見せるので Stock 集合を返す。 */
    suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError>

    /** アーカイブ済み商品一覧(UC23)。 */
    suspend fun listArchived(householdId: HouseholdId): RpcResult<Products, RpcError>

    /** 買い物リスト(UC18。自動=在庫不足 + 手動希望の 2 区分を含む読みモデル)。 */
    suspend fun shoppingList(householdId: HouseholdId): RpcResult<ShoppingList, RpcError>
}
```

- [ ] **Step 3: 登録系 `ProductRegisterRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product/ProductRegisterRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.product

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface ProductRegisterRpcService {
    /** マスタから商品を採用する(UC10。単位・最低在庫を指定)。 */
    suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
        unit: ProductUnit,
        minimumStock: MinimumStock,
    ): RpcResult<Product, RpcError>

    /** マスタに無い商品をその場で追加(UC13)。 */
    suspend fun addCustom(
        householdId: HouseholdId,
        request: AddCustomProductRequest,
    ): RpcResult<Product, RpcError>

    /** 単位を変更(UC22, owner のみ)。 */
    suspend fun changeUnit(
        productId: ProductId,
        unit: ProductUnit,
    ): RpcResult<Unit, RpcError>

    /** 画像を変更(UC22, owner のみ)。`ProductImage.None` で未設定に戻せる。 */
    suspend fun changeImage(
        productId: ProductId,
        image: ProductImage,
    ): RpcResult<Unit, RpcError>

    /** 最低在庫を変更(UC22, owner のみ)。 */
    suspend fun changeMinimum(
        productId: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError>

    /** 商品をアーカイブ(UC23, owner のみ。在庫 0 のときのみ)。 */
    suspend fun archive(productId: ProductId): RpcResult<Unit, RpcError>

    /** 商品を復元(UC23, owner のみ)。 */
    suspend fun unarchive(productId: ProductId): RpcResult<Unit, RpcError>

    /** 手動の買い物希望フラグを設定/解除(UC19,20)。ProductId は global UUID で世帯一意のため householdId は不要。 */
    suspend fun setWanted(
        productId: ProductId,
        wanted: Boolean,
    ): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 4: コンパイルを確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/product
git commit -m "feat(rpc): Product コンテキストの RPC service interface(参照系/登録系)+ AddCustomProductRequest DTO を追加"
```

---

## Task 9: Stock コンテキストのサービス(参照系 + 登録系)+ `ActivityFeed` DTO

**Files:**
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/ActivityFeed.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRpcService.kt`
- Create: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRegisterRpcService.kt`

> 在庫操作は Stock 集約のメソッド(`replenish`/`consume`/`correct`)。`actor` は session 由来(引数で受けない)。`occurredAt` も**引数で受けず P5 で `OccurredAt.now()`** を付与する(ブレスト合意=サーバ時刻)。`history`(UC17)は商品単位の変動履歴で、商品は productId で既知なので append-only な `StockMovements` を直接返す。`activity`(UC24)は**世帯横断**で、`StockMovement` は商品参照を持たないため bare `StockMovements` だと「何の商品か」が落ちる。よって `ActivityEntry(product, movement)` のリストを持つ `ActivityFeed` view DTO を `:rpc` に新設して返す(ドメインで表せない射影)。`correct`(UC21)は対象 movement・訂正後数量・理由を受ける。

- [ ] **Step 1: `ActivityFeed` / `ActivityEntry` DTO を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/ActivityFeed.kt`:
```kotlin
package net.brightroom.mindstock.rpc.stock

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovement

/**
 * 世帯全体の活動履歴(UC24)の射影。
 * `StockMovement` は商品参照を持たないため、世帯横断フィードでは各 movement に
 * 商品(`Product`)を添えて「誰が・何の商品を・いくつ」を描けるようにする。
 */
@Serializable
data class ActivityEntry(
    val product: Product,
    val movement: StockMovement,
)

@Serializable
data class ActivityFeed(
    val list: List<ActivityEntry>,
) {
    fun size(): Int = list.size
}
```

- [ ] **Step 2: 参照系 `StockRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.stock

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface StockRpcService {
    /** 商品単位の変動履歴(UC17)。商品は productId で既知なので StockMovements を直接返す。 */
    suspend fun history(productId: ProductId): RpcResult<StockMovements, RpcError>

    /** 世帯全体の活動履歴(UC24)。商品を添えた ActivityFeed を返す。 */
    suspend fun activity(householdId: HouseholdId): RpcResult<ActivityFeed, RpcError>
}
```

- [ ] **Step 3: 登録系 `StockRegisterRpcService` を作成**

`rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock/StockRegisterRpcService.kt`:
```kotlin
package net.brightroom.mindstock.rpc.stock

import kotlinx.rpc.annotations.Rpc
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.quantity.Quantity
import net.brightroom.mindstock.domain.model.inventory.stock.movement.MovementId
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Note
import net.brightroom.mindstock.domain.model.inventory.stock.movement.Reason
import net.brightroom.mindstock.rpc.result.RpcError
import net.brightroom.mindstock.rpc.result.RpcResult

@Rpc
interface StockRegisterRpcService {
    /** 在庫を補充する(UC14)。Stock は productId で特定。actor / occurredAt は session・サーバ時刻由来。 */
    suspend fun replenish(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError>

    /** 在庫を消費する(UC15)。 */
    suspend fun consume(
        productId: ProductId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError>

    /** 記録を訂正する(UC21。append-only。対象 movement を打ち消す訂正 movement を追記)。 */
    suspend fun correct(
        target: MovementId,
        correctedQuantity: Quantity,
        reason: Reason,
    ): RpcResult<Unit, RpcError>
}
```

- [ ] **Step 4: コンパイルを確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/stock
git commit -m "feat(rpc): Stock コンテキストの RPC service interface(参照系/登録系)+ ActivityFeed DTO を追加"
```

---

## Task 10: 深いグラフのペイロード round-trip + 全体ビルド検証

**Files:**
- Test: `rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/PayloadSerializationTest.kt`

> `Product` は `Barcode`(sealed)・`ProductImage`(sealed)・複数の value class を内包する代表的な深いグラフ。これを `RpcResult.Ok<Product>` として `KrpcJson` で往復し、polymorphic discriminator と value class が wire で壊れないことを確認する(`krpc-ws-pipeline-gotchas` の「Json 取り違えで静かに死ぬ」予防)。JAN は有効な EAN-13 チェックディジット `4901234567894` を使う。

- [ ] **Step 1: ペイロード round-trip テストを書く(失敗する)**

`rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/PayloadSerializationTest.kt`:
```kotlin
package net.brightroom.mindstock.rpc.result

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.brightroom.mindstock.domain.model.catalog.barcode.Barcode
import net.brightroom.mindstock.domain.model.catalog.barcode.Jan
import net.brightroom.mindstock.domain.model.catalog.content.CatalogContent
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.content.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.item.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.origin.CatalogOrigin
import net.brightroom.mindstock.domain.model.inventory.product.Product
import net.brightroom.mindstock.domain.model.inventory.product.ProductId
import net.brightroom.mindstock.domain.model.inventory.product.ProductStatus
import net.brightroom.mindstock.domain.model.inventory.product.image.ProductImage
import net.brightroom.mindstock.domain.model.inventory.product.setting.MinimumStock
import net.brightroom.mindstock.domain.model.inventory.product.setting.ProductUnit
import net.brightroom.mindstock.domain.model.inventory.product.setting.StockingPolicy
import net.brightroom.mindstock.extensions.kotlinx.serialization.KrpcJson
import kotlin.test.Test

class PayloadSerializationTest {
    @Test
    fun sealed_と_value_class_を含む_Product_を_RpcResult_Ok_として往復できる() {
        val product =
            Product(
                id = ProductId.create(),
                catalogItem =
                    CatalogItem(
                        id = CatalogItemId.create(),
                        content = CatalogContent(CatalogItemName("洗剤"), CatalogItemUnit("個")),
                        barcode = Barcode.Linked(Jan("4901234567894")),
                        origin = CatalogOrigin.世帯独自,
                    ),
                setting = StockingPolicy(ProductUnit("個"), MinimumStock(1)),
                image = ProductImage.None,
                status = ProductStatus.採用中,
            )
        val ok: RpcResult<Product, RpcError> = RpcResult.Ok(product)
        val back = KrpcJson.decodeFromString<RpcResult<Product, RpcError>>(KrpcJson.encodeToString(ok))
        back shouldBe ok
    }
}
```

- [ ] **Step 2: テストがコンパイル失敗しないこと・走って通ることを確認**

Run: `./gradlew :rpc:jvmTest --tests "net.brightroom.mindstock.rpc.result.PayloadSerializationTest"`
Expected: PASS

- [ ] **Step 3: `:rpc` 全体ビルド(全 round-trip + 全 interface コンパイル)を確認**

Run: `./gradlew :rpc:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: `:domain` も含めて緑を確認**

Run: `./gradlew :domain:build :rpc:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add rpc/src/commonTest/kotlin/net/brightroom/mindstock/rpc/result/PayloadSerializationTest.kt
git commit -m "test(rpc): sealed/value class を含む深いペイロードの KrpcJson round-trip を追加"
```

---

## 完了条件

- `./gradlew :domain:build :rpc:build` が緑。
- 5 コンテキスト × (参照系/登録系) のサービス interface(Catalog は参照系のみ)が `@Rpc` 付きで宣言されている。
- `RpcResult<T, RpcError>` / `RpcError`(sealed 5 variant)が `:rpc` にあり、`KrpcJson` で往復できる。
- DTO は `InvitationPreview` / `AddCustomProductRequest` / `ActivityFeed`(+ `ActivityEntry`)の 3 概念のみ(ドメインで表せない箇所だけ)。
- 全 UC(1–24)が、いずれかのサービスメソッド(または P5 で扱う認証/将来対応)に対応づく。

## UC → メソッド対応(網羅確認)

| UC | 対応 |
|---|---|
| 1 ログイン | P5 認証(Zitadel OIDC)。RPC メソッドなし |
| 2 表示名登録 / me | `ResidentRegisterRpcService.registerDisplayName` / `ResidentRpcService.me` |
| 3 世帯作成 | `HouseholdRegisterRpcService.create` |
| 4 招待で参加 | `HouseholdRpcService.previewInvite` + `HouseholdRegisterRpcService.join` |
| 5 世帯切替 | `HouseholdRpcService.list`(切替自体はクライアント状態) |
| 6 世帯名変更 | `HouseholdRegisterRpcService.rename` |
| 7 退出 | `HouseholdRegisterRpcService.leave` |
| 8 招待発行/失効 | `HouseholdRegisterRpcService.createInvite` / `revokeInvite` |
| 9 権限変更/除外 | `HouseholdRegisterRpcService.changeRole` / `removeMember` |
| 10 マスタ採用 | `ProductRegisterRpcService.adopt` |
| 11 JAN検索 | `CatalogRpcService.search` / `lookupByJan` |
| 12 バーコードスキャン | `CatalogRpcService.lookupByJan`(スキャンはクライアント) |
| 13 カスタム追加 | `ProductRegisterRpcService.addCustom` |
| 14 補充 | `StockRegisterRpcService.replenish` |
| 15 消費 | `StockRegisterRpcService.consume` |
| 16 在庫一覧/検索 | `ProductRpcService.list` |
| 17 商品詳細・履歴 | `StockRpcService.history` |
| 18 買い物リスト閲覧 | `ProductRpcService.shoppingList` |
| 19 手動で買い物リスト操作 | `ProductRegisterRpcService.setWanted` |
| 20 在庫から探して追加 | `ProductRegisterRpcService.setWanted`(探索はクライアント) |
| 21 訂正 | `StockRegisterRpcService.correct` |
| 22 マスタ編集 | `ProductRegisterRpcService.changeUnit` / `changeImage` / `changeMinimum` |
| 23 アーカイブ/復元 | `ProductRegisterRpcService.archive` / `unarchive` / `ProductRpcService.listArchived` |
| 24 活動履歴 | `StockRpcService.activity`(`ActivityFeed` を返す) |
