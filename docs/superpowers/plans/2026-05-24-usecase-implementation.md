# Plan 4: UseCase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:backend:application:api` 配下に 20 個の Command Handler 風 UseCase クラスを実装し、トランザクション Ktor plugin を整備する。Repository ポートと 1:1 対応の薄いアプリケーション層。

**Architecture:** リッチドメインモデルを前提に、Handler は thin pass-through(1 操作 1 クラス、`handle()` 1 メソッド)。トランザクションは Ktor plugin で境界に張り、Handler 内には `transaction {}` を書かない。DomainException は Handler を透過させ Plan 6 で境界マッピング。Plan 4 のテストは「ロジックがある Handler だけ MockK で選択的」だが現状の 20 Handler はすべて pass-through なので **単体テストは書かない**(spec §6.1)。結合テストは Plan 5。

**Tech Stack:** Kotlin / Ktor server / Ktor built-in DI (`io.ktor.server.plugins.di`) / Exposed v1.0.0-beta-4 / spotless / Kotest(将来テスト追加時)

---

## 前提

- `:domain` の集約・Value Object・Repository ポートは Plan 3 / domain-richness / stock-movements-unification で実装済み
- `:backend:application:api` モジュールは存在し、`Main.kt` / Ktor configuration がスケルトンとして整っている
- Repository 実装は **Plan 5** で実装(Plan 4 時点では未実装)。よって DI 配線・実行時動作確認は Plan 4 のスコープ外
- 本仕様: `docs/superpowers/specs/2026-05-24-usecase-design.md`

## ブランチ運用

`feat/usecase-layer` ブランチを main から派生して全 Task を乗せる。Task 0(spec patch)も同じブランチに含める(ユーザー指示: まとめて 1 PR)。

```bash
git checkout main
git pull --ff-only
git checkout -b feat/usecase-layer
```

## ファイル構成サマリ

新規作成:
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/RegisterUserHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/RenameUserHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/CreateHouseholdHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/InviteMemberHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/RevokeMembershipHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/FindHouseholdOfUserHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/RegisterCatalogItemHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/ReviseCatalogItemHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/SearchCatalogItemsHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/FindCatalogItemByIdHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/AdoptProductHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/SetMinimumStockHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/ArchiveProductHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/ListProductsOfHouseholdHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/FindProductHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/ReplenishStockHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/ConsumeStockHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/GetStockHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/ListStocksHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/GetMovementHistoryHandler.kt`
- `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`

修正:
- `docs/superpowers/specs/2026-05-24-domain-richness-design.md`(Task 0)
- `docs/superpowers/specs/2026-05-23-domain-layer-design.md`(Task 0)
- `docs/superpowers/specs/2026-05-24-usecase-design.md`(Task 0: Koin → Ktor DI 記述修正)

---

## Task 0: 親仕様パッチと usecase-design 仕様の修正

本仕様の決定に合わせて古い「Plan 4 で再考」記述を削除し、`:backend:application:api` の DI 機構が Koin ではなく Ktor 標準 DI である事実に合わせて usecase-design 仕様を修正する。

**Files:**
- Modify: `docs/superpowers/specs/2026-05-24-domain-richness-design.md`
- Modify: `docs/superpowers/specs/2026-05-23-domain-layer-design.md`
- Modify: `docs/superpowers/specs/2026-05-24-usecase-design.md`

### Step 1: `domain-richness-design.md` の Request クラス記述を更新

L33 付近、「Plan 4 で `:backend:application:api` 配下に置くかどうかを再判断する。」という記述を以下に置き換え:

> Request クラスは domain には置かない。Request は外部入力(RPC DTO)と domain の間に位置する**腐敗防止層(ACL)**の概念で、`shared:rpc` 側に DTO として定義する(Plan 4 で確定)。UseCase は Request クラスを受け取らず、named arguments で個別 Value Object を受ける。

### Step 2: `domain-richness-design.md` の Stock id 対応付け記述を削除

L59 / L266 / L367 / L458 付近にある「Repository 実装が domain object と DB 行を対応付ける手段は Plan 4-5 で設計」「同値性問題」「未解決(Plan 4-5 で設計)」関連の記述を削除する。stock-movements-unification で訂正概念ごと廃止により自動解消したため。

具体的な対象文:
- L59: 「Repository 実装が domain object と DB 行を対応付ける手段は Plan 4-5 で設計(候補: 全フィールドハッシュ、UUID 化、隠し handle 等)」全文削除
- L266: 「id を持たない。順序は `occurredAt` で取れる。Repository 実装が domain object を DB 行に対応付ける手段は Plan 4-5 で設計する。」を「id を持たない。順序は `occurredAt` で取れる。stock-movements-unification で訂正概念を廃止したため、Repository 実装は append-only insert のみで domain object と DB 行を逆引きする必要がない。」に置換
- L367: 「Plan 4-5 でこの「同値性問題」を解決する設計が必要(候補: 隠し handle、UUID 化、占位的に一意性を保証するフィールド追加)。」を削除
- L458: 「**未解決(Plan 4-5 で設計)**: domain object に id がないため、…決定は Plan 4-5 で行う。」セクション全文削除(stock-movements-unification で解消済み)

### Step 3: `domain-richness-design.md` の Repository ポート位置記述を更新

L123 付近: 「現状の位置を維持(Repository 移動は Plan 4 検討)」を「現状の位置を維持(`domain/repository/` 据え置きで確定)」に変更。

L544 付近: 「Repository インターフェースの位置(domain vs application): 現状 domain に置いたまま。リファレンスでは application 側だが、Plan 4 で UseCase を書く際に再考」を「Repository インターフェースの位置: `domain/repository/` 据え置きで確定(Plan 4)。理由はテスタビリティと domain の自己完結性。」に変更。

### Step 4: `domain-richness-design.md` の findById 記述と Application 層記述を更新

L408 付近: 「`fun findById(id: CatalogItemId): CatalogItem?  // RPC 経由の id 引き(Plan 4 で取り扱い再考)`」を「`fun findById(id: CatalogItemId): CatalogItem?  // RPC 経由の id 引き(取り扱いは Plan 6 で RPC 設計と合わせて再考)`」に変更。

L546 付近: 「Application 層(Scenario / Service クラス): Plan 4」を「Application 層(Command Handler 風): `2026-05-24-usecase-design.md` で確定」に変更。

### Step 5: `domain-layer-design.md` の例外翻訳記述を更新

L288 付近: 「UseCase 層(Plan 4)で catch して、必要なら親仕様 §6.3 の `InventoryException` サブクラスに翻訳する。`DomainException` は domain の純粋な語彙で、ワイヤー越境はしない。」を以下に置換:

> Plan 4 では Handler は `DomainException` を catch せず透過させ、Ktor の境界 plugin(`StatusPages` or kotlinx-rpc error interceptor)で一括して RPC error に翻訳する。詳細は `2026-05-24-usecase-design.md` §5 を参照。`DomainException` は domain の純粋な語彙で、ワイヤー越境はしない(境界翻訳の責務は plugin)。

### Step 6: `domain-layer-design.md` のトランザクション境界記述を更新

L381 付近: 「Repository インターフェースはトランザクション境界を表現しない。**UseCase 層がトランザクションを開閉する**(Plan 4 で `transaction { ... }` ブロックを定義)。」を以下に置換:

> Repository インターフェースはトランザクション境界を表現しない。トランザクションは **Ktor plugin で境界に張る**(1 RPC 呼び出し = 1 transaction)。UseCase Handler は `transaction {}` を書かない。詳細は `2026-05-24-usecase-design.md` §4 を参照。

### Step 7: `usecase-design.md` の DI 機構記述を修正

`:backend:application:api` は Koin ではなく Ktor 標準 DI(`io.ktor.server.plugins.di`)を使用している。spec §7 を全面差し替え:

該当ブロック(`// configuration/di/UseCaseModule.kt` から始まる Koin の例)全体を以下に置換:

```markdown
## 7. DI 配線

`:backend:application:api` は Ktor 標準 DI(`io.ktor.server.plugins.di`)を使用する。Handler 群は Plan 5 で Repository 実装が揃った時点で Application 拡張関数として `dependencies { provide<...> { ... } }` ブロックに登録する。

Plan 4 段階では Handler クラスファイルだけを作る。DI 登録(`configuration/di/` 配下の Application 拡張関数追加と `application.yaml` の `ktor.application.modules` への登録)は **Plan 5 で実施**。理由: Repository 実装が存在しないため、Plan 4 段階で Koin / Ktor DI のいずれに登録しても、Application 起動時に依存解決で失敗する。

Plan 4 ではコンパイルが通り、将来 DI に流し込めるコンストラクタが揃っていることを保証する。
```

### Step 8: 動作確認

```bash
./gradlew spotlessCheck
```

Expected: BUILD SUCCESSFUL(spec markdown のみの変更で spotless は影響しない)。

### Step 9: コミット

```bash
git add docs/superpowers/specs/2026-05-24-domain-richness-design.md \
        docs/superpowers/specs/2026-05-23-domain-layer-design.md \
        docs/superpowers/specs/2026-05-24-usecase-design.md
git commit -m "docs(spec): align parent specs with Plan 4 (UseCase layer) decisions

- domain-richness: Request 位置・Stock id 対応付け・Repository 位置・findById・Application 層構造を確定値に更新
- domain-layer: 例外翻訳とトランザクション境界の責務を境界 plugin に変更
- usecase-design: DI 機構を Koin から Ktor 標準 DI に修正、Plan 4 では DI 登録しない方針を明記"
```

---

## Task 1: User 集約の Handler 実装

User の Repository ポート 2 メソッド(`register` / `rename`)に対応する Handler を作る。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/RegisterUserHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/RenameUserHandler.kt`

### Step 1: `RegisterUserHandler` を作成

`backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/RegisterUserHandler.kt`:

```kotlin
package net.brightroom.mindstock.application.usecase.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository

class RegisterUserHandler(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun handle(
        identity: AuthIdentity,
        defaultName: DisplayName,
    ): User = userRegisterRepository.register(identity, defaultName)
}
```

### Step 2: `RenameUserHandler` を作成

`backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/RenameUserHandler.kt`:

```kotlin
package net.brightroom.mindstock.application.usecase.user

import net.brightroom.mindstock.domain.model.user.DisplayName
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.user.UserRegisterRepository

class RenameUserHandler(
    private val userRegisterRepository: UserRegisterRepository,
) {
    fun handle(
        user: User,
        newName: DisplayName,
    ) {
        userRegisterRepository.rename(user, newName)
    }
}
```

### Step 3: ビルド確認

Run: `./gradlew :backend:application:api:compileKotlin`
Expected: BUILD SUCCESSFUL

### Step 4: spotless 適用

Run: `./gradlew :backend:application:api:spotlessApply`
Expected: BUILD SUCCESSFUL

### Step 5: コミット

```bash
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/user/
git commit -m "feat(usecase): add User aggregate handlers (Register/Rename)"
```

---

## Task 2: Household 集約の Handler 実装

`HouseholdRegisterRepository`(create / invite / revoke)と `HouseholdRepository`(findOf)に対応する 4 Handler。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/CreateHouseholdHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/InviteMemberHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/RevokeMembershipHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/FindHouseholdOfUserHandler.kt`

### Step 1: `CreateHouseholdHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository

class CreateHouseholdHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(owner: User): Household = householdRegisterRepository.create(owner)
}
```

### Step 2: `InviteMemberHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository

class InviteMemberHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(
        household: Household,
        user: User,
        role: HouseholdMemberRole,
    ) {
        householdRegisterRepository.invite(household, user, role)
    }
}
```

### Step 3: `RevokeMembershipHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRegisterRepository

class RevokeMembershipHandler(
    private val householdRegisterRepository: HouseholdRegisterRepository,
) {
    fun handle(
        household: Household,
        user: User,
    ) {
        householdRegisterRepository.revoke(household, user)
    }
}
```

### Step 4: `FindHouseholdOfUserHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.household.HouseholdRepository

class FindHouseholdOfUserHandler(
    private val householdRepository: HouseholdRepository,
) {
    fun handle(user: User): Household? = householdRepository.findOf(user)
}
```

### Step 5: ビルド + spotless + コミット

```bash
./gradlew :backend:application:api:compileKotlin
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/household/
git commit -m "feat(usecase): add Household aggregate handlers (Create/Invite/Revoke/FindOf)"
```

---

## Task 3: CatalogItem 集約の Handler 実装

`CatalogItemRegisterRepository`(register / revise)と `CatalogItemRepository`(search / findById)に対応する 4 Handler。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/RegisterCatalogItemHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/ReviseCatalogItemHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/SearchCatalogItemsHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/FindCatalogItemByIdHandler.kt`

### Step 1: `RegisterCatalogItemHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository

class RegisterCatalogItemHandler(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun handle(
        name: CatalogItemName,
        unit: CatalogItemUnit,
        createdBy: User,
    ): CatalogItem = catalogItemRegisterRepository.register(name, unit, createdBy)
}
```

### Step 2: `ReviseCatalogItemHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRegisterRepository

class ReviseCatalogItemHandler(
    private val catalogItemRegisterRepository: CatalogItemRegisterRepository,
) {
    fun handle(
        catalogItem: CatalogItem,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
        editedBy: User,
    ) {
        catalogItemRegisterRepository.revise(catalogItem, newName, newUnit, editedBy)
    }
}
```

### Step 3: `SearchCatalogItemsHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository

class SearchCatalogItemsHandler(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun handle(
        query: String,
        limit: Int = 50,
    ): CatalogItems = catalogItemRepository.search(query, limit)
}
```

### Step 4: `FindCatalogItemByIdHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.repository.catalog.CatalogItemRepository

class FindCatalogItemByIdHandler(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun handle(id: CatalogItemId): CatalogItem? = catalogItemRepository.findById(id)
}
```

### Step 5: ビルド + spotless + コミット

```bash
./gradlew :backend:application:api:compileKotlin
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/catalog/
git commit -m "feat(usecase): add CatalogItem aggregate handlers (Register/Revise/Search/FindById)"
```

---

## Task 4: Product 集約の Handler 実装

`ProductRegisterRepository`(adopt / setMinimumStock / archive)と `ProductRepository`(listOf / find)に対応する 5 Handler。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/AdoptProductHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/SetMinimumStockHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/ArchiveProductHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/ListProductsOfHouseholdHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/FindProductHandler.kt`

### Step 1: `AdoptProductHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository

class AdoptProductHandler(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun handle(
        household: Household,
        catalogItem: CatalogItem,
    ): Product = productRegisterRepository.adopt(household, catalogItem)
}
```

### Step 2: `SetMinimumStockHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository

class SetMinimumStockHandler(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun handle(
        product: Product,
        value: MinimumStock,
        editedBy: User,
    ) {
        productRegisterRepository.setMinimumStock(product, value, editedBy)
    }
}
```

### Step 3: `ArchiveProductHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.product.ProductRegisterRepository

class ArchiveProductHandler(
    private val productRegisterRepository: ProductRegisterRepository,
) {
    fun handle(
        product: Product,
        by: User,
    ) {
        productRegisterRepository.archive(product, by)
    }
}
```

### Step 4: `ListProductsOfHouseholdHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.domain.repository.product.ProductRepository

class ListProductsOfHouseholdHandler(
    private val productRepository: ProductRepository,
) {
    fun handle(household: Household): Products = productRepository.listOf(household)
}
```

### Step 5: `FindProductHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.repository.product.ProductRepository

class FindProductHandler(
    private val productRepository: ProductRepository,
) {
    fun handle(
        household: Household,
        catalogItem: CatalogItem,
    ): Product? = productRepository.find(household, catalogItem)
}
```

### Step 6: ビルド + spotless + コミット

```bash
./gradlew :backend:application:api:compileKotlin
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/product/
git commit -m "feat(usecase): add Product aggregate handlers (Adopt/SetMinimumStock/Archive/ListOf/Find)"
```

---

## Task 5: Stock 集約の Handler 実装

`StockRegisterRepository`(replenish / consume)と `StockRepository`(stockOf / stocksOf / movementHistory)に対応する 5 Handler。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/ReplenishStockHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/ConsumeStockHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/GetStockHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/ListStocksHandler.kt`
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/GetMovementHistoryHandler.kt`

### Step 1: `ReplenishStockHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.stock.StockRegisterRepository

class ReplenishStockHandler(
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun handle(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Replenishment = stockRegisterRepository.replenish(product, quantity, occurredAt, by, note)
}
```

### Step 2: `ConsumeStockHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.user.User
import net.brightroom.mindstock.domain.repository.stock.StockRegisterRepository

class ConsumeStockHandler(
    private val stockRegisterRepository: StockRegisterRepository,
) {
    fun handle(
        product: Product,
        quantity: Quantity,
        occurredAt: OccurredAt,
        by: User,
        note: Note,
    ): Consumption = stockRegisterRepository.consume(product, quantity, occurredAt, by, note)
}
```

### Step 3: `GetStockHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.repository.stock.StockRepository

class GetStockHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(product: Product): Stock = stockRepository.stockOf(product)
}
```

### Step 4: `ListStocksHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.repository.stock.StockRepository

class ListStocksHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(household: Household): List<Stock> = stockRepository.stocksOf(household)
}
```

### Step 5: `GetMovementHistoryHandler` を作成

```kotlin
package net.brightroom.mindstock.application.usecase.stock

import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.domain.repository.stock.StockRepository

class GetMovementHistoryHandler(
    private val stockRepository: StockRepository,
) {
    fun handle(
        product: Product,
        limit: Int = 50,
    ): StockMovements = stockRepository.movementHistory(product, limit)
}
```

### Step 6: ビルド + spotless + コミット

```bash
./gradlew :backend:application:api:compileKotlin
./gradlew :backend:application:api:spotlessApply
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase/stock/
git commit -m "feat(usecase): add Stock aggregate handlers (Replenish/Consume/Get/List/MovementHistory)"
```

---

## Task 6: Transaction Ktor plugin の整備(ファイルのみ、未登録)

Ktor の Application plugin として `transaction(db) { ... }` で各呼び出しを囲むためのスケルトンを置く。RPC 配線は Plan 6 で行うため、本 plugin は **install しない**(ファイルだけ用意して Plan 6 で `application.yaml` の modules に登録する)。

**Files:**
- Create: `backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`

### Step 1: Plugin ファイルを作成

`backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/ExposedTransactionPlugin.kt`:

```kotlin
package net.brightroom.mindstock.configuration.transaction

import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager

/**
 * 1 RPC 呼び出し = 1 Exposed transaction を境界で開閉する Ktor plugin。
 *
 * Handler / Repository 実装は `transaction {}` を一切書かず、本 plugin が張った
 * transaction を `TransactionManager.currentOrNull()` 経由で拾う前提。
 *
 * 注: 本 plugin は Plan 4 時点ではファイルのみ用意し、`install(...)` は Plan 6 の
 * kotlinx-rpc サービス配線と同時に行う。Plan 4 段階で install すると、Repository
 * 実装が無いため Handler を呼ぶエンドポイント自体が存在せず動作確認できない。
 *
 * 詳細設計: docs/superpowers/specs/2026-05-24-usecase-design.md §4
 */
val ExposedTransactionPlugin =
    createApplicationPlugin(
        name = "ExposedTransaction",
        createConfiguration = ::ExposedTransactionConfig,
    ) {
        val database = pluginConfig.database
            ?: error("ExposedTransactionPlugin requires `database` to be set in configuration")

        onCall { call ->
            call.attributes.put(DatabaseAttributeKey, database)
        }

        // Plan 6 で kotlinx-rpc 配線時、ここで RPC 呼び出しを transaction { ... } で囲む
        // 実装フック点(call interceptor / rpc service decorator のいずれか)は Plan 6 で確定
    }

class ExposedTransactionConfig {
    var database: Database? = null
}

internal val DatabaseAttributeKey = AttributeKey<Database>("ExposedTransactionDatabase")
```

### Step 2: ビルド確認

Run: `./gradlew :backend:application:api:compileKotlin`
Expected: BUILD SUCCESSFUL

注: `transactionManager` import は将来 plugin 内で transaction 操作を行うときに使う想定。現状未使用で warning が出る場合は import を削除する。

### Step 3: 未使用 import の削除

`./gradlew :backend:application:api:compileKotlin` で `unused import` warning が出たら `ExposedTransactionPlugin.kt` から `import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager` を削除して再ビルド。

### Step 4: spotless 適用

Run: `./gradlew :backend:application:api:spotlessApply`
Expected: BUILD SUCCESSFUL

### Step 5: コミット

```bash
git add backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/
git commit -m "feat(infra): add ExposedTransactionPlugin skeleton (install deferred to Plan 6)"
```

---

## Task 7: 最終検証と PR 作成

全 Handler とプラグインがビルド・spotless ともに通ること、20 ファイル + 1 plugin がリポジトリ上に存在することを確認し、PR を作成する。

### Step 1: 全モジュールのビルド

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL(既存テストはすべて pass、新規テストは無し)

### Step 2: spotless 最終確認

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL

失敗したら `./gradlew spotlessApply` を実行して再度確認 → コミット追加。

### Step 3: ファイル数確認

Run:
```bash
find backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase -name "*.kt" | wc -l
```

Expected: `20`

Run:
```bash
ls backend/application/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/
```

Expected: `ExposedTransactionPlugin.kt`

### Step 4: コミット履歴確認

Run: `git log --oneline main..HEAD`

Expected(順序):

```text
<sha> feat(usecase): add ExposedTransactionPlugin skeleton (install deferred to Plan 6)
<sha> feat(usecase): add Stock aggregate handlers (Replenish/Consume/Get/List/MovementHistory)
<sha> feat(usecase): add Product aggregate handlers (Adopt/SetMinimumStock/Archive/ListOf/Find)
<sha> feat(usecase): add CatalogItem aggregate handlers (Register/Revise/Search/FindById)
<sha> feat(usecase): add Household aggregate handlers (Create/Invite/Revoke/FindOf)
<sha> feat(usecase): add User aggregate handlers (Register/Rename)
<sha> docs(spec): align parent specs with Plan 4 (UseCase layer) decisions
```

### Step 5: push と PR 作成

```bash
git push -u origin feat/usecase-layer
gh pr create --title "feat: Plan 4 — UseCase layer (20 Command Handlers + transaction plugin skeleton)" --body "$(cat <<'EOF'
## Summary
Plan 4 の UseCase 層を実装。Repository ポートと 1:1 対応の 20 Handler を Command Handler 風(1 操作 1 クラス)で `:backend:application:api` 配下に追加。

- User: Register / Rename
- Household: Create / Invite / Revoke / FindOf
- CatalogItem: Register / Revise / Search / FindById
- Product: Adopt / SetMinimumStock / Archive / ListOf / Find
- Stock: Replenish / Consume / Get / List / MovementHistory

加えて `ExposedTransactionPlugin` のスケルトンを `configuration/transaction/` に追加(install は Plan 6 で実施)。

親仕様(`domain-richness-design.md` / `domain-layer-design.md` / `usecase-design.md`)を本実装に合わせて更新。

仕様書: `docs/superpowers/specs/2026-05-24-usecase-design.md`
実装プラン: `docs/superpowers/plans/2026-05-24-usecase-implementation.md`

## Scope notes
- DI 配線(Ktor `dependencies { provide<...> }`)は **Plan 5** で実施(Repository 実装が揃ってから)
- Plan 4 では Handler ファイルのみで、`application.yaml` の `ktor.application.modules` への追加は無し
- 単体テストは spec §6.1 の方針に従い、ロジックがある Handler のみ書く方針。現状の 20 Handler はすべて thin pass-through なので Plan 4 では **テストファイル無し**(結合テストは Plan 5)
- Transaction plugin の install と RPC 配線は Plan 6

## Test plan
- [ ] `./gradlew build` が通る
- [ ] `./gradlew spotlessCheck` が通る
- [ ] `find backend/application/api/src/main/kotlin/net/brightroom/mindstock/application/usecase -name "*.kt" | wc -l` が 20
- [ ] 仕様書(usecase-design.md §3)の Handler 一覧と実装ファイルが 1:1 で揃っている

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### Step 6: 終了確認

PR URL を出力。マージ判断はユーザー。

---

## 対象外(明示)

本プランで **やらないこと**(Plan 5 / 6 / 別途で扱う):

- Repository の Exposed 実装(Plan 5)
- Handler の DI 登録 / `application.yaml` への module 追加(Plan 5)
- ExposedTransactionPlugin の install / kotlinx-rpc 配線(Plan 6)
- DomainException → RPC error マッピングの具体実装(Plan 6)
- Handler + Repository + 実 PostgreSQL の結合テスト(Plan 5、Testcontainers)
- RPC エンドポイント単位の E2E テスト(Plan 6)
- 認証(JWT 検証 / current user 取得)実装

memory: [[integration-tests-deferred-not-skipped]] のとおり「Plan 4 で結合テストを書かない」≠「結合テストを永久にやらない」。Plan 5 で必ず Testcontainers を使った結合テストを追加する。
