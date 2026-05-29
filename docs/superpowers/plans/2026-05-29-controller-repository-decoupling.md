# Controller → Repository 直接依存の排除 / 戻り値 non-null 化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** spec 2026-05-29-controller-repository-decoupling-design に従い、Controller から Repository 直接依存を排除し、Repository/Service の戻り値を non-null に統一する。

**Architecture:** ① `domain` に `ResourceNotFoundException` を追加して全層から throws/捕捉できるようにする ② Repository/Service の戻り値を `T?` から `T`(throws on missing)に変更 ③ `tx()` で集約捕捉して `RpcError.NotFound(message)` に翻訳 ④ Controller は Service だけを呼ぶ薄い層に。

**Tech Stack:** Kotlin 2.x / Ktor 3 / kotlinx-rpc 0.10.2 / Exposed JDBC v1 / kotlinx-serialization / Kotest / mockk

**Spec:** `docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md`

**Branch:** `feat/controller-repository-decoupling`(spec commit `8bbe9fa` 済)

**Verification cadence:** 各 phase 末で `./gradlew :backend:api:check` を green に保つ。phase 内の小タスクはコンパイル green を目安(integration test は phase 末)。

---

## File Map(変更ファイル一覧)

### 作成

- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/ResourceNotFoundException.kt`
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt`
- `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/user/UserService.kt`

### 変更

- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/{catalog/CatalogItems,product/Products,stock/movement/StockMovements,household/HouseholdMembers}.kt` — wrapper 慣習統一
- `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stock.kt` 周辺 — `Stocks` 参照導入の波及
- `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/**/*Repository.kt`(5 ファイル: Household / CatalogItem / Product / Stock / User) — non-null + throws 契約
- `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/**/*DataSource.kt`(5 ファイル) — 該当メソッドで `null` 戻り → throws
- `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/**/*Service.kt`(4 ファイル: Household / CatalogItem / Product / Stock) — 戻り値型変更、findById 追加
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt` — `UserService` 登録、Factory provider の Repository 注入を Service に変更
- `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt` — `ResourceNotFoundException` 捕捉追加
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/{household,catalog,product,stock}/*Controller.kt`(4 ファイル) — Repository 注入除去、Service 経由のみ
- `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/{household,catalog,product,stock}/*ControllerFactory.kt`(該当ファイル) — 不要(Factory interface はそのまま、provider lambda の依存を変えるだけ)
- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcError.kt` — `NotFound(resource, id)` → `NotFound(message)`
- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/{Household,Catalog,Product,Stock}RpcService.kt` — nullable 戻り値撤廃、`StockRpcService.list` を `Stocks` に
- `backend/api/src/test/kotlin/.../infrastructure/datasource/repository/**/*IntegrationTest.kt` — throws ケース追加
- `backend/api/src/test/kotlin/.../presentation/rpc/**/*ControllerTest.kt`(4 ファイル) — Repository mock 削除、Service mock に
- `backend/api/src/test/kotlin/.../e2e/**/*E2eTest.kt`(複数) — `RpcError.NotFound` assertion を `(message)` ベースに、メッセージ文字列を固定値 assertion
- `backend/api/src/test/kotlin/.../configuration/transaction/TxWithGuardTest.kt` — `ResourceNotFoundException` ケース追加
- 全プロジェクト: `.size` プロパティ呼び出し → `.size()`、`.asList()` 呼び出し → `.list`

### 削除

- なし(置換のみ)

---

# Phase 1: Domain 追加(独立)

`ResourceNotFoundException` と `Stocks` を追加する。他のファイルには影響しない(参照されてないので追加だけ)。

### Task 1.1: `ResourceNotFoundException` を作成

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/ResourceNotFoundException.kt`

- [ ] **Step 1: ファイル作成**

```kotlin
package net.brightroom.mindstock.domain.exception

/**
 * 単一値の resource(集約 / Profile 等)が見つからなかったことを表す。
 *
 * Repository 実装(infrastructure)が起点として throw し、Service・Controller を素通りして
 * 最終的に `:backend:api` の tx() ヘルパーが捕捉して `RpcError.NotFound(message)` に変換する。
 *
 * メッセージは「リソース種別 + 識別子」の形式を推奨: e.g. "household not found: $id"
 */
class ResourceNotFoundException(
    reason: String,
) : RuntimeException(reason)
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :domain:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

### Task 1.2: `Stocks` ラッパーを作成

**Files:**
- Create: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt`

- [ ] **Step 1: ファイル作成**

```kotlin
package net.brightroom.mindstock.domain.model.stock

import kotlinx.serialization.Serializable

/**
 * 在庫の集合。世帯の在庫一覧等で使う。
 */
@Serializable
data class Stocks(
    val list: List<Stock>,
) {
    fun size(): Int = list.size
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :domain:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

### Task 1.3: Phase 1 commit

- [ ] **Step 1: 全テスト**

Run: `./gradlew :domain:check`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: commit**

```bash
git add domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/exception/ResourceNotFoundException.kt \
        domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/Stocks.kt
git commit -m "$(cat <<'EOF'
feat(domain): ResourceNotFoundException と Stocks wrapper を追加

- ResourceNotFoundException: 単一値の不在を表す共通例外。tx() で捕捉して
  RpcError.NotFound(message) に変換する起点として使う
- Stocks: List<Stock> の domain wrapper。新慣習(val list + fun size())で
  作成。後続 phase で StockService.list が raw List<Stock> から Stocks に置換される

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.1, §3.2

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 2: Wrapper 慣習統一(機械的)

既存 4 wrapper を新慣習(`val list` + `fun size(): Int`、`asList()` 削除)に揃え、全呼び出し箇所を一斉置換する。

### Task 2.1: `Products` を更新

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/product/Products.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.domain.model.product

import kotlinx.serialization.Serializable

/**
 * 商品の集合。世帯の商品リスト等で使う。
 */
@Serializable
data class Products(
    val list: List<Product>,
) {
    /** archived = false の商品のみのコレクションを返す。 */
    fun activeOnly(): Products = Products(list.filter { !it.archived })

    fun size(): Int = list.size
}
```

差分: `fun asList(): List<Product> = list.toList()` 削除、`val size: Int get() = list.size` → `fun size(): Int = list.size`

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :domain:compileKotlinJvm`
Expected: コンパイルエラーが他ファイルで発生(`.asList()` / `.size` プロパティ参照箇所)。次タスクで解消

### Task 2.2: `CatalogItems` を更新

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/catalog/CatalogItems.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.domain.model.catalog

import kotlinx.serialization.Serializable

/**
 * カタログ商品の集合。検索結果等で使う。
 */
@Serializable
data class CatalogItems(
    val list: List<CatalogItem>,
) {
    fun size(): Int = list.size
}
```

### Task 2.3: `StockMovements` を更新

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/stock/movement/StockMovements.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.domain.model.stock.movement

import kotlinx.serialization.Serializable

/**
 * StockMovement のコレクション。
 *
 * netQuantity は補充を正・消費を負として全 movement を線形集計した正味数量。
 * `Stock.currentQuantity()` はこれをそのまま使う。
 */
@Serializable
data class StockMovements(
    val list: List<StockMovement>,
) {
    fun size(): Int = list.size

    fun netQuantity(): Int =
        list.sumOf { m ->
            when (m) {
                is Replenishment -> +m.quantity()
                is Consumption -> -m.quantity()
            }
        }
}
```

### Task 2.4: `HouseholdMembers` を更新

**Files:**
- Modify: `domain/src/commonMain/kotlin/net/brightroom/mindstock/domain/model/household/HouseholdMembers.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.domain.model.household

import kotlinx.serialization.Serializable
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.Profile

/**
 * 世帯のメンバー一覧。
 *
 * アクティブなメンバーのみを保持(Repository が revoked を除外して読み込む)。
 */
@Serializable
data class HouseholdMembers(
    val list: List<HouseholdMember>,
) {
    /** OWNER ロールのメンバーを返す。存在しなければ null。 */
    fun owner(): Profile? = list.firstOrNull { it.role == HouseholdMemberRole.OWNER }?.profile

    /** すべてのアクティブメンバーの Profile を返す。 */
    fun activeMembers(): List<Profile> = list.map { it.profile }

    /** 指定したユーザーがアクティブメンバーに含まれるか。 */
    fun contains(userId: UserId): Boolean = list.any { it.profile.userId == userId }

    fun size(): Int = list.size
}
```

差分: `fun asList(): List<HouseholdMember> = list.toList()` 削除、`size` プロパティが元から無いので追加だけ

### Task 2.5: 全プロジェクトの `.size` / `.asList()` 呼び出しを一斉置換

**Files:**
- Modify: 多数(grep で検出)

- [ ] **Step 1: `.asList()` 呼び出しを `.list` に置換**

Run:
```bash
grep -rln "\.asList()" domain/src backend/ rpc/src 2>/dev/null | grep -v build | xargs -I {} echo "REVIEW: {}"
```

各ファイルを開いて `<wrapper>.asList()` → `<wrapper>.list` に置換。例えば `StockDataSource.kt:36` の `productRepository.listOf(household).asList()` → `productRepository.listOf(household).list`

Wrapper 以外の `.asList()`(Kotlin stdlib のもの)は触らない。判別は文脈で:
- `Products` / `CatalogItems` / `StockMovements` / `HouseholdMembers` のインスタンス上の `.asList()` のみ置換対象
- stdlib `IntArray.asList()` 等は触らない

- [ ] **Step 2: `.size` プロパティを `.size()` 呼び出しに置換**

該当する wrapper のインスタンス変数に対して `.size` を使っている箇所を `.size()` に置換。例: `products.size` → `products.size()`、`movements.size` → `movements.size()`

判別:
- 上記 5 wrapper の `.size` プロパティ参照のみ対象
- `List<T>.size` / `String.length` 等の stdlib プロパティは触らない

検出コマンド:
```bash
grep -rn "\.size\b" domain/src backend/ rpc/src 2>/dev/null | grep -v build | grep -v "list.size\b"
```

各 grep 結果を文脈確認して必要なら置換。

- [ ] **Step 3: コンパイル + 全テスト**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL(unit + integration)

### Task 2.6: Phase 2 commit

- [ ] **Step 1: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(domain): collection wrapper 慣習を統一

Products / CatalogItems / StockMovements / HouseholdMembers の以下を変更:
- `val size: Int get() = list.size` → `fun size(): Int = list.size`
- `fun asList(): List<T> = list.toList()` を削除(.list で直接アクセス)

呼び出し側を全プロジェクトで `.size` → `.size()`、`.asList()` → `.list`
に一斉置換。

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.3

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 3: Repository interface + DataSource の non-null 化

各 Repository の単一値 lookup メソッドを `T?` → `T` に変更し、infrastructure 側で空結果なら `ResourceNotFoundException` を throw する。`StockRepository.stocksOf` は戻り値型を `List<Stock>` → `Stocks` に変更。

### Task 3.1: `HouseholdRepository` + `HouseholdDataSource`

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/household/HouseholdRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/household/HouseholdDataSource.kt`

- [ ] **Step 1: interface を更新**

```kotlin
package net.brightroom.mindstock.application.repository.household

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.user.UserId

interface HouseholdRepository {
    /**
     * ユーザーが所属する世帯(MVP は 1 ユーザー 1 世帯前提)。
     * 未所属なら `ResourceNotFoundException` を throw する。
     */
    fun findOf(userId: UserId): Household

    /**
     * id 引き(主に RPC 経由)。
     * 該当 household が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findById(id: HouseholdId): Household
}
```

- [ ] **Step 2: DataSource を更新**

`HouseholdDataSource.kt` の `findOf` / `findById` のリターン文を次のパターンに変更:

`findOf` の末尾 `if (rows.isEmpty()) return null` を以下に置換:
```kotlin
if (rows.isEmpty()) throw ResourceNotFoundException("household not found for user: $userId")
```

import 追加: `import net.brightroom.mindstock.domain.exception.ResourceNotFoundException`

`findById` の `if (!householdExists) return null` を以下に置換:
```kotlin
if (!householdExists) throw ResourceNotFoundException("household not found: $id")
```

戻り値型の `?` を外す:
```kotlin
override fun findOf(userId: UserId): Household {  // was: Household?
    // ...
}

override fun findById(id: HouseholdId): Household {  // was: Household?
    // ...
}
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: 呼び出し側でコンパイルエラー(後続 phase で解消)。Repository / DataSource のみ独立コンパイルできれば OK

### Task 3.2: `CatalogItemRepository` + `CatalogItemDataSource`

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/catalog/CatalogItemRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/catalog/CatalogItemDataSource.kt`

- [ ] **Step 1: interface を更新**

```kotlin
package net.brightroom.mindstock.application.repository.catalog

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

interface CatalogItemRepository {
    /** 名前部分一致検索。 */
    fun search(
        query: String,
        limit: Int = 50,
    ): CatalogItems

    /**
     * id 引き(主に RPC 経由)。
     * 該当 CatalogItem が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findById(id: CatalogItemId): CatalogItem
}
```

- [ ] **Step 2: DataSource を更新**

`CatalogItemDataSource.kt` の `findById`:

```kotlin
override fun findById(id: CatalogItemId): CatalogItem {
    val latestRevs = buildLatestRevs()

    return CatalogItemsTable
        .join(latestRevs.alias, JoinType.INNER, onColumn = CatalogItemsTable.id, otherColumn = latestRevs.catalogItemId)
        .join(CatalogItemRevisionsTable, JoinType.INNER) {
            (CatalogItemRevisionsTable.catalog_item_id eq latestRevs.catalogItemId) and
                (CatalogItemRevisionsTable.id eq latestRevs.maxId)
        }.selectAll()
        .where { CatalogItemsTable.id eq id() }
        .singleOrNull()
        ?.let { row ->
            hydrateCatalogItem(
                id = row[CatalogItemsTable.id],
                name = row[CatalogItemRevisionsTable.name],
                unit = row[CatalogItemRevisionsTable.unit],
            )
        } ?: throw ResourceNotFoundException("catalog item not found: $id")
}
```

import 追加: `import net.brightroom.mindstock.domain.exception.ResourceNotFoundException`

### Task 3.3: `ProductRepository` + `ProductDataSource`

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/product/ProductDataSource.kt`

- [ ] **Step 1: interface を更新**

```kotlin
package net.brightroom.mindstock.application.repository.product

import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products

interface ProductRepository {
    /** 世帯の全商品(archived 含む)。 */
    fun listOf(household: Household): Products

    /**
     * 同一世帯で同一カタログ商品を採用済みか引く(UNIQUE 検出用)。
     * 該当 Product が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product

    /**
     * id 引き(主に RPC 経由)。
     * 該当 Product が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findById(id: ProductId): Product
}
```

- [ ] **Step 2: DataSource を更新**

`ProductDataSource.kt` の `find` と `findById`:

```kotlin
override fun find(
    household: Household,
    catalogItem: CatalogItem,
): Product =
    buildJoinedQuery()
        .selectAll()
        .where {
            (ProductsTable.household_id eq household.id()) and
                (ProductsTable.catalog_item_id eq catalogItem.id())
        }.singleOrNull()
        ?.toProduct()
        ?: throw ResourceNotFoundException(
            "product not found: household=${household.id()}, catalog_item=${catalogItem.id()}",
        )

override fun findById(id: ProductId): Product =
    buildJoinedQuery()
        .selectAll()
        .where { ProductsTable.id eq id() }
        .singleOrNull()
        ?.toProduct()
        ?: throw ResourceNotFoundException("product not found: $id")
```

import 追加: `import net.brightroom.mindstock.domain.exception.ResourceNotFoundException`

### Task 3.4: `StockRepository` + `StockDataSource`

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/stock/StockRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/stock/StockDataSource.kt`

- [ ] **Step 1: interface を更新**

```kotlin
package net.brightroom.mindstock.application.repository.stock

import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

interface StockRepository {
    /** 1 商品の在庫状態。 */
    fun stockOf(product: Product): Stock

    /** 世帯全商品の在庫状態(ShoppingList 用)。 */
    fun stocksOf(household: Household): Stocks

    /** 指定商品の movement 履歴(最新順を想定)。 */
    fun movementHistory(
        product: Product,
        limit: Int = 50,
    ): StockMovements
}
```

- [ ] **Step 2: DataSource を更新**

`StockDataSource.kt` の `stocksOf`:

```kotlin
override fun stocksOf(household: Household): Stocks {
    val products = productRepository.listOf(household).list
    if (products.isEmpty()) return Stocks(emptyList())
    val byProductId = loadMovementsFor(products)
    val stocks = products.map { p ->
        Stock(p, StockMovements(byProductId[p.id()] ?: emptyList()))
    }
    return Stocks(stocks)
}
```

import 追加: `import net.brightroom.mindstock.domain.model.stock.Stocks`

`.asList()` が `productRepository.listOf(household).list` に変わっている点に注意(Phase 2 で既に置換済のはずだが、戻り値型が `Stocks` になることで `Stock` の `List` を Stocks にラップする処理を最後に追加)。

### Task 3.5: `UserRepository` + `UserDataSource`

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/user/UserRepository.kt`
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/datasource/user/UserDataSource.kt`

- [ ] **Step 1: interface を更新**

```kotlin
package net.brightroom.mindstock.application.repository.user

import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.auth.AuthIdentity
import net.brightroom.mindstock.domain.model.user.profile.Profile

interface UserRepository {
    /**
     * AuthIdentity 引き。
     * 該当 Profile が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findProfileByAuthIdentity(identity: AuthIdentity): Profile

    /**
     * UserId 引き。
     * 該当 Profile が存在しなければ `ResourceNotFoundException` を throw する。
     */
    fun findProfileById(id: UserId): Profile
}
```

- [ ] **Step 2: DataSource を更新**

`UserDataSource.kt`:

```kotlin
override fun findProfileByAuthIdentity(identity: AuthIdentity): Profile =
    queryLatest { UsersTable.zitadel_sub eq identity.subject() }
        ?: throw ResourceNotFoundException("user not found: subject=${identity.subject()}")

override fun findProfileById(id: UserId): Profile =
    queryLatest { UsersTable.id eq id() }
        ?: throw ResourceNotFoundException("user not found: $id")

private fun queryLatest(where: () -> Op<Boolean>): Profile? {
    // 内部実装は不変(private なので nullable のまま)
    // ...
}
```

import 追加: `import net.brightroom.mindstock.domain.exception.ResourceNotFoundException`

### Task 3.6: `MindstockAuthPlugin` の `findByAuthIdentity` 呼び出しを更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/MindstockAuthPlugin.kt`

現在 `userRepository.findProfileByAuthIdentity(identity)?.userId` のような呼び出しが Auth plugin にある。throws になったので、try/catch で「ユーザ未登録時は userId = null」を表現する。

- [ ] **Step 1: Auth plugin の userId 取得を以下に変更**

該当箇所(`onCall { call ->` ブロック内、`val userId = newSuspendedTransaction(...) { ... }` のところ):

```kotlin
val userId =
    newSuspendedTransaction(db = database) {
        runCatching { userRepository.findProfileByAuthIdentity(identity).userId }.getOrNull()
    }
```

import が必要なら追加。

### Task 3.7: DataSource integration test に throws ケースを追加

**Files:**
- Modify: `backend/api/src/test/kotlin/.../infrastructure/datasource/repository/household/HouseholdDataSourceIntegrationTest.kt`
- Modify: `backend/api/src/test/kotlin/.../infrastructure/datasource/repository/catalog/CatalogItemDataSourceIntegrationTest.kt`
- Modify: `backend/api/src/test/kotlin/.../infrastructure/datasource/repository/product/ProductDataSourceIntegrationTest.kt`
- Modify: `backend/api/src/test/kotlin/.../infrastructure/datasource/repository/stock/StockDataSourceIntegrationTest.kt`
- Modify: `backend/api/src/test/kotlin/.../infrastructure/datasource/repository/user/UserDataSourceIntegrationTest.kt`

- [ ] **Step 1: 既存「null 戻り期待」テストを `shouldThrow<ResourceNotFoundException>` に書き換え**

例(`HouseholdDataSourceIntegrationTest.kt`):

```kotlin
test("findById returns null for unknown id") {
    // BEFORE: HouseholdDataSource().findById(HouseholdId(Uuid.random())) shouldBe null
    shouldThrow<ResourceNotFoundException> {
        HouseholdDataSource().findById(HouseholdId(Uuid.random()))
    }.message shouldContain "household not found"
}
```

import 追加: `import io.kotest.assertions.throwables.shouldThrow`, `import io.kotest.matchers.string.shouldContain`, `import net.brightroom.mindstock.domain.exception.ResourceNotFoundException`

同じパターンを 5 DataSource test に適用。各テストファイルの「null を期待」テストを throws に書き換える。

- [ ] **Step 2: 全 integration テスト実行**

Run: `./gradlew :backend:api:integrationTest`
Expected: BUILD SUCCESSFUL

### Task 3.8: Phase 3 commit

- [ ] **Step 1: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(repository): single-value lookup を non-null + throws に統一

- 5 Repository interface (Household/CatalogItem/Product/Stock/User) の単一値 lookup
  を T? から T に変更
- infrastructure DataSource: 空結果なら ResourceNotFoundException を throw
- StockRepository.stocksOf: List<Stock> → Stocks (新 wrapper)
- MindstockAuthPlugin: findProfileByAuthIdentity の throws を runCatching で受けて
  「未登録 user は userId = null」セマンティクスを維持
- DataSource integration test: 「null 戻り期待」→「shouldThrow<ResourceNotFoundException>」へ

呼び出し側 (Service / Controller) は次 phase で順次解消する。

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.4-§3.5

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 4: Service 改修 + 新規 `UserService`

各 Service を新 Repository signature に合わせて更新。`findById` メソッド追加、戻り値 non-null 化、`UserService` 新規作成、DI 登録。

### Task 4.1: `HouseholdService` を更新

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/household/HouseholdService.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.application.service.household

import net.brightroom.mindstock.application.repository.household.HouseholdRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.user.UserId

class HouseholdService(
    private val householdRepository: HouseholdRepository,
) {
    fun findOf(userId: UserId): Household = householdRepository.findOf(userId)

    fun findById(id: HouseholdId): Household = householdRepository.findById(id)
}
```

### Task 4.2: `CatalogItemService` を更新

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/catalog/CatalogItemService.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.application.service.catalog

import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItems

class CatalogItemService(
    private val catalogItemRepository: CatalogItemRepository,
) {
    fun findById(id: CatalogItemId): CatalogItem = catalogItemRepository.findById(id)

    fun search(
        query: String,
        limit: Int = 50,
    ): CatalogItems = catalogItemRepository.search(query, limit)
}
```

### Task 4.3: `ProductService` を更新

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/product/ProductService.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.application.service.product

import net.brightroom.mindstock.application.repository.product.ProductRepository
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products

class ProductService(
    private val productRepository: ProductRepository,
) {
    fun findById(id: ProductId): Product = productRepository.findById(id)

    fun find(
        household: Household,
        catalogItem: CatalogItem,
    ): Product = productRepository.find(household, catalogItem)

    fun listOf(household: Household): Products = productRepository.listOf(household)
}
```

### Task 4.4: `StockService` を更新

**Files:**
- Modify: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockService.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.application.service.stock

import net.brightroom.mindstock.application.repository.stock.StockRepository
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements

class StockService(
    private val stockRepository: StockRepository,
) {
    fun get(product: Product): Stock = stockRepository.stockOf(product)

    fun list(household: Household): Stocks = stockRepository.stocksOf(household)

    fun getMovementHistory(
        product: Product,
        limit: Int = 50,
    ): StockMovements = stockRepository.movementHistory(product, limit)
}
```

### Task 4.5: `UserService` を新規作成

**Files:**
- Create: `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/user/UserService.kt`

- [ ] **Step 1: ファイル作成**

```kotlin
package net.brightroom.mindstock.application.service.user

import net.brightroom.mindstock.application.repository.user.UserRepository
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.domain.model.user.profile.Profile

class UserService(
    private val userRepository: UserRepository,
) {
    fun findById(userId: UserId): Profile = userRepository.findProfileById(userId)
}
```

### Task 4.6: `DependenciesConfiguration` に `UserService` を登録

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`

- [ ] **Step 1: `UserService` provider を追加**

ファイル内の Service 登録ブロック(`provide<UserRegisterService>` の直前 or 直後)に追加:

```kotlin
provide<UserService> { UserService(resolve()) }
```

ファイル先頭の import 追加:
```kotlin
import net.brightroom.mindstock.application.service.user.UserService
```

(`UserRegisterService` の登録はそのまま残す)

### Task 4.7: コンパイル + Phase 4 commit

- [ ] **Step 1: コンパイル確認**

Run: `./gradlew :backend:core:compileKotlin :backend:api:compileKotlin`
Expected: 呼び出し側(Controller)でエラーが出る(後続 phase で解消)。Service 単独はコンパイル green

- [ ] **Step 2: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(service): Service 戻り値を non-null へ統一 + UserService 新規

- 4 Service (Household/CatalogItem/Product/Stock) の戻り値を Repository に追従して
  T へ統一。HouseholdService.findById / ProductService.findById を新規追加
- StockService.list の戻り値を Stocks (domain wrapper) へ
- UserService を新規作成: findById(userId): Profile
- DependenciesConfiguration に UserService を登録

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.6

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 5: `RpcError.NotFound` 簡素化 + RPC interface non-null 化

`:rpc` モジュールの `RpcError.NotFound` を `(message)` に簡素化、各 RPC interface の nullable な戻り値型を non-null に統一。

### Task 5.1: `RpcError.NotFound` を `(message)` に簡素化

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/RpcError.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
package net.brightroom.mindstock.rpc

import kotlinx.serialization.Serializable

/**
 * API 全体で共有する RPC エラー語彙。
 *
 * read/write を分けず、ありうるエラー集合の和集合を持つ。「この read メソッドは
 * BadRequest を返さない」という型レベル保証は失うが、クライアントは when の
 * 網羅性検証で新しい variant の追加に必ず気付ける。
 */
@Serializable
sealed interface RpcError {
    /** 認証失敗 / トークン期限切れ / Principal 未解決 等。 */
    @Serializable
    data class Unauthorized(
        val reason: String,
    ) : RpcError

    /**
     * 単一値の resource が見つからなかった。
     *
     * message は server 側で発生した `ResourceNotFoundException` の reason がそのまま
     * 乗る。例: "household not found: $id" / "product not found: $id"。
     * クライアントは呼び出しコンテキスト(どの RPC method を呼んだか)から意味を組み立てる。
     */
    @Serializable
    data class NotFound(
        val message: String,
    ) : RpcError

    /** 入力検証エラー。 */
    @Serializable
    data class BadRequest(
        val field: String,
        val reason: String,
    ) : RpcError

    /** 競合(重複登録 等)。 */
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

### Task 5.2: RPC interface の nullable 戻り値を撤廃

**Files:**
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/HouseholdRpcService.kt`
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/CatalogRpcService.kt`
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/ProductRpcService.kt`
- Modify: `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/StockRpcService.kt`

- [ ] **Step 1: `HouseholdRpcService.findOf()` を non-null に**

`HouseholdRpcService.kt` 内、`findOf` のシグネチャを以下に変更:

```kotlin
// Before:
// suspend fun findOf(): RpcResult<Household?, RpcError>
// After:
suspend fun findOf(): RpcResult<Household, RpcError>
```

- [ ] **Step 2: `CatalogRpcService.findById(id)` を non-null に**

```kotlin
// Before:
// suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem?, RpcError>
// After:
suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem, RpcError>
```

- [ ] **Step 3: `ProductRpcService.find(...)` を non-null に**

```kotlin
// Before:
// suspend fun find(householdId: HouseholdId, catalogItemId: CatalogItemId): RpcResult<Product?, RpcError>
// After:
suspend fun find(
    householdId: HouseholdId,
    catalogItemId: CatalogItemId,
): RpcResult<Product, RpcError>
```

- [ ] **Step 4: `StockRpcService.list(householdId)` を `Stocks` に**

```kotlin
// Before:
// suspend fun list(householdId: HouseholdId): RpcResult<List<Stock>, RpcError>
// After:
suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError>
```

import 追加: `import net.brightroom.mindstock.domain.model.stock.Stocks`

- [ ] **Step 5: コンパイル確認**

Run: `./gradlew :rpc:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

### Task 5.3: Phase 5 commit(コンパイルは Controller 側で失敗するが、interface 変更だけ単独 commit)

- [ ] **Step 1: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(rpc): RpcError.NotFound 簡素化 + RPC interface nullable 撤廃

- RpcError.NotFound(resource, id) → NotFound(message): クライアントは呼び出しコンテキスト
  から「何が見つからなかったか」を組み立てるため、メタデータは server 例外メッセージを
  パススルーするだけで十分
- 4 RPC interface (Household/Catalog/Product/Stock) の nullable 戻り値を non-null に
  統一。「存在しない」は Err(NotFound) で表現
- StockRpcService.list の戻り値型を List<Stock> から Stocks へ

呼び出し側 (Controller + Test) は次 phase で順次解消する。

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.7, §4

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 6: `tx()` で `ResourceNotFoundException` 捕捉

`Transaction.kt` に `ResourceNotFoundException` 専用 catch を追加し、`Err(RpcError.NotFound(message))` に翻訳する。

### Task 6.1: `Transaction.kt` を更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`

- [ ] **Step 1: ファイル全置換**

```kotlin
@file:Suppress("DEPRECATION")

package net.brightroom.mindstock.configuration.transaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.domain.exception.ResourceNotFoundException
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}
private val callLogJson = Json { encodeDefaults = true }

@Serializable
private data class TxLogEntry(
    val callId: String,
    val userId: String?,
    val outcome: String, // "Ok" | "Err:<variant>" | "Throwable:<class>" | "Err:Unauthorized(expired)" | "Err:NotFound"
    val elapsedMs: Long,
)

@OptIn(ExperimentalUuidApi::class)
suspend fun <T> tx(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    if (start > session.exp) {
        emitLog(session, start, outcome = "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result =
            supervisorScope {
                newSuspendedTransaction(db = database) { block() }
            }
        emitLog(
            session, start,
            outcome =
                when (result) {
                    is RpcResult.Ok -> "Ok"
                    is RpcResult.Err -> "Err:${result.error::class.simpleName}"
                },
        )
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResourceNotFoundException) {
        emitLog(session, start, outcome = "Err:NotFound")
        RpcResult.Err(RpcError.NotFound(message = e.message.orEmpty()))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId}" }
        emitLog(session, start, outcome = "Throwable:${e::class.simpleName}")
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun emitLog(
    session: MindstockSession,
    start: Instant,
    outcome: String,
) {
    val elapsedMs = (Clock.System.now() - start).inWholeMilliseconds
    val entry =
        TxLogEntry(
            callId = session.callId.toString(),
            userId = session.userId?.toString(),
            outcome = outcome,
            elapsedMs = elapsedMs,
        )
    logger.info { "rpc call ${callLogJson.encodeToString(entry)}" }
}
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileKotlin`
Expected: Controller 側でコンパイルエラー残るが、`Transaction.kt` 単独は green

### Task 6.2: `TxWithGuardTest` にケース追加

**Files:**
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/configuration/transaction/TxWithGuardTest.kt`

- [ ] **Step 1: 既存 spec を読み、`ResourceNotFoundException` ケースを追加**

ファイル末尾の `})` 直前に以下テストを追加:

```kotlin
test("block 内で ResourceNotFoundException → Err(NotFound) でメッセージがパススルーされる") {
    TestDataSource.withFreshSchema { jdbcUrl, _ ->
        val ds = testHikariDataSource(jdbcUrl, TestDataSource.user, TestDataSource.password)
        try {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            val database = Database.connect(ds)
            val result =
                runBlocking {
                    tx<Int>(database, sessionWith(Clock.System.now() + 1.hours)) {
                        throw ResourceNotFoundException("household not found: 00000000-0000-0000-0000-000000000001")
                    }
                }
            result.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
            val err = result.error
            err.shouldBeInstanceOf<RpcError.NotFound>()
            err.message shouldBe "household not found: 00000000-0000-0000-0000-000000000001"
        } finally {
            ds.close()
        }
    }
}
```

import 追加: `import net.brightroom.mindstock.domain.exception.ResourceNotFoundException`

- [ ] **Step 2: テスト実行**

Run: `./gradlew :backend:api:integrationTest --tests "*TxWithGuardTest"`
Expected: PASS

### Task 6.3: Phase 6 commit

- [ ] **Step 1: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(tx): ResourceNotFoundException を捕捉して Err(NotFound) に翻訳

tx() の catch チェインに ResourceNotFoundException 専用 case を追加。
順序は CancellationException → ResourceNotFoundException → Throwable (catch-all)。
exception の message をそのまま RpcError.NotFound(message) に乗せる。

TxWithGuardTest に 1 ケース追加。

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.8

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 7: Controller + Factory + DI の改修

各 Controller から Repository 注入を除去し、Service だけを呼ぶ薄い層に整える。Controller の null チェックを撤廃(`tx()` が NotFound を集約捕捉する)。Factory provider lambda の依存も更新。

### Task 7.1: `HouseholdController` を更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/household/HouseholdController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`(provider lambda)

- [ ] **Step 1: `HouseholdController.kt` を全置換**

```kotlin
package net.brightroom.mindstock.presentation.rpc.household

import net.brightroom.mindstock.application.service.household.HouseholdRegisterService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.user.UserService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.Household
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.household.HouseholdMemberRole
import net.brightroom.mindstock.domain.model.user.UserId
import net.brightroom.mindstock.rpc.HouseholdRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class HouseholdController(
    private val householdService: HouseholdService,
    private val householdRegisterService: HouseholdRegisterService,
    private val userService: UserService,
    private val session: MindstockSession,
    private val database: Database,
) : HouseholdRpcService {
    override suspend fun findOf(): RpcResult<Household, RpcError> =
        tx(database, session) { RpcResult.Ok(householdService.findOf(requireNotNull(session.userId))) }

    override suspend fun create(): RpcResult<Household, RpcError> =
        tx(database, session) { RpcResult.Ok(householdRegisterService.create(requireNotNull(session.userId))) }

    override suspend fun invite(
        householdId: HouseholdId,
        invitee: UserId,
        role: HouseholdMemberRole,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val inviteeProfile = userService.findById(invitee)
            householdRegisterService.invite(household, inviteeProfile.userId, role)
            RpcResult.Ok(Unit)
        }

    override suspend fun revoke(
        householdId: HouseholdId,
        target: UserId,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val targetProfile = userService.findById(target)
            householdRegisterService.revoke(household, targetProfile.userId)
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 2: `DependenciesConfiguration.kt` の `HouseholdControllerFactory` provider を更新**

該当 `provide<HouseholdControllerFactory> { ... }` ブロックを以下に置換(`UserRepository` → `UserService` に変更、`HouseholdRepository` 不要なので削除):

```kotlin
provide<HouseholdControllerFactory> {
    val hs = resolve<HouseholdService>()
    val hrs = resolve<HouseholdRegisterService>()
    val us = resolve<UserService>()
    val db = resolve<Database>()
    HouseholdControllerFactory { session -> HouseholdController(hs, hrs, us, session, db) }
}
```

import 追加: `import net.brightroom.mindstock.application.service.user.UserService`
import 削除(他で使われていない場合): `import net.brightroom.mindstock.application.repository.household.HouseholdRepository`、`import net.brightroom.mindstock.application.repository.user.UserRepository`

### Task 7.2: `CatalogController` を更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/catalog/CatalogController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`(provider lambda)

- [ ] **Step 1: `CatalogController.kt` を全置換**

```kotlin
package net.brightroom.mindstock.presentation.rpc.catalog

import net.brightroom.mindstock.application.service.catalog.CatalogItemRegisterService
import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItem
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.catalog.CatalogItemName
import net.brightroom.mindstock.domain.model.catalog.CatalogItemUnit
import net.brightroom.mindstock.domain.model.catalog.CatalogItems
import net.brightroom.mindstock.rpc.CatalogRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class CatalogController(
    private val catalogItemService: CatalogItemService,
    private val catalogItemRegisterService: CatalogItemRegisterService,
    private val session: MindstockSession,
    private val database: Database,
) : CatalogRpcService {
    override suspend fun search(
        query: String,
        limit: Int,
    ): RpcResult<CatalogItems, RpcError> = tx(database, session) { RpcResult.Ok(catalogItemService.search(query, limit)) }

    override suspend fun findById(id: CatalogItemId): RpcResult<CatalogItem, RpcError> =
        tx(database, session) { RpcResult.Ok(catalogItemService.findById(id)) }

    override suspend fun register(
        name: CatalogItemName,
        unit: CatalogItemUnit,
    ): RpcResult<CatalogItem, RpcError> =
        tx(database, session) {
            RpcResult.Ok(catalogItemRegisterService.register(name, unit, requireNotNull(session.userId)))
        }

    override suspend fun revise(
        id: CatalogItemId,
        newName: CatalogItemName,
        newUnit: CatalogItemUnit,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val catalogItem = catalogItemService.findById(id)
            catalogItemRegisterService.revise(catalogItem, newName, newUnit, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 2: `DependenciesConfiguration.kt` の `CatalogControllerFactory` provider を更新**

```kotlin
provide<CatalogControllerFactory> {
    val cs = resolve<CatalogItemService>()
    val crs = resolve<CatalogItemRegisterService>()
    val db = resolve<Database>()
    CatalogControllerFactory { session -> CatalogController(cs, crs, session, db) }
}
```

import 削除(他で使われていない場合): `import net.brightroom.mindstock.application.repository.catalog.CatalogItemRepository`

### Task 7.3: `ProductController` を更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/product/ProductController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`(provider lambda)

- [ ] **Step 1: `ProductController.kt` を全置換**

```kotlin
package net.brightroom.mindstock.presentation.rpc.product

import net.brightroom.mindstock.application.service.catalog.CatalogItemService
import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductRegisterService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.catalog.CatalogItemId
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.MinimumStock
import net.brightroom.mindstock.domain.model.product.Product
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.product.Products
import net.brightroom.mindstock.rpc.ProductRpcService
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import org.jetbrains.exposed.v1.jdbc.Database

class ProductController(
    private val productService: ProductService,
    private val productRegisterService: ProductRegisterService,
    private val householdService: HouseholdService,
    private val catalogItemService: CatalogItemService,
    private val session: MindstockSession,
    private val database: Database,
) : ProductRpcService {
    override suspend fun listOfHousehold(householdId: HouseholdId): RpcResult<Products, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            RpcResult.Ok(productService.listOf(household))
        }

    override suspend fun find(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val catalogItem = catalogItemService.findById(catalogItemId)
            RpcResult.Ok(productService.find(household, catalogItem))
        }

    override suspend fun adopt(
        householdId: HouseholdId,
        catalogItemId: CatalogItemId,
    ): RpcResult<Product, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            val catalogItem = catalogItemService.findById(catalogItemId)
            RpcResult.Ok(productRegisterService.adopt(household, catalogItem))
        }

    override suspend fun setMinimumStock(
        id: ProductId,
        minimumStock: MinimumStock,
    ): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product = productService.findById(id)
            productRegisterService.setMinimumStock(product, minimumStock, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }

    override suspend fun archive(id: ProductId): RpcResult<Unit, RpcError> =
        tx(database, session) {
            val product = productService.findById(id)
            productRegisterService.archive(product, requireNotNull(session.userId))
            RpcResult.Ok(Unit)
        }
}
```

- [ ] **Step 2: `DependenciesConfiguration.kt` の `ProductControllerFactory` provider を更新**

```kotlin
provide<ProductControllerFactory> {
    val ps = resolve<ProductService>()
    val prs = resolve<ProductRegisterService>()
    val hs = resolve<HouseholdService>()
    val cs = resolve<CatalogItemService>()
    val db = resolve<Database>()
    ProductControllerFactory { session -> ProductController(ps, prs, hs, cs, session, db) }
}
```

import 削除(他で使われていない場合): `HouseholdRepository`、`CatalogItemRepository`、`ProductRepository`
import 追加: `import net.brightroom.mindstock.application.service.household.HouseholdService`、`import net.brightroom.mindstock.application.service.catalog.CatalogItemService`

### Task 7.4: `StockController` を更新

**Files:**
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/stock/StockController.kt`
- Modify: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/di/DependenciesConfiguration.kt`(provider lambda)

- [ ] **Step 1: `StockController.kt` を全置換**

```kotlin
package net.brightroom.mindstock.presentation.rpc.stock

import net.brightroom.mindstock.application.service.household.HouseholdService
import net.brightroom.mindstock.application.service.product.ProductService
import net.brightroom.mindstock.application.service.stock.StockRegisterService
import net.brightroom.mindstock.application.service.stock.StockService
import net.brightroom.mindstock.configuration.auth.MindstockSession
import net.brightroom.mindstock.configuration.transaction.tx
import net.brightroom.mindstock.domain.model.household.HouseholdId
import net.brightroom.mindstock.domain.model.product.ProductId
import net.brightroom.mindstock.domain.model.stock.Note
import net.brightroom.mindstock.domain.model.stock.OccurredAt
import net.brightroom.mindstock.domain.model.stock.Quantity
import net.brightroom.mindstock.domain.model.stock.Stock
import net.brightroom.mindstock.domain.model.stock.Stocks
import net.brightroom.mindstock.domain.model.stock.movement.Consumption
import net.brightroom.mindstock.domain.model.stock.movement.Replenishment
import net.brightroom.mindstock.domain.model.stock.movement.StockMovements
import net.brightroom.mindstock.rpc.RpcError
import net.brightroom.mindstock.rpc.RpcResult
import net.brightroom.mindstock.rpc.StockRpcService
import org.jetbrains.exposed.v1.jdbc.Database

class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productService: ProductService,
    private val householdService: HouseholdService,
    private val session: MindstockSession,
    private val database: Database,
) : StockRpcService {
    override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
        tx(database, session) {
            val product = productService.findById(productId)
            RpcResult.Ok(stockService.get(product))
        }

    override suspend fun list(householdId: HouseholdId): RpcResult<Stocks, RpcError> =
        tx(database, session) {
            val household = householdService.findById(householdId)
            RpcResult.Ok(stockService.list(household))
        }

    override suspend fun movementHistory(
        productId: ProductId,
        limit: Int,
    ): RpcResult<StockMovements, RpcError> =
        tx(database, session) {
            val product = productService.findById(productId)
            RpcResult.Ok(stockService.getMovementHistory(product, limit))
        }

    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Replenishment, RpcError> =
        tx(database, session) {
            val product = productService.findById(productId)
            RpcResult.Ok(stockRegisterService.replenish(product, qty, occurredAt, requireNotNull(session.userId), note))
        }

    override suspend fun consume(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Consumption, RpcError> =
        tx(database, session) {
            val product = productService.findById(productId)
            RpcResult.Ok(stockRegisterService.consume(product, qty, occurredAt, requireNotNull(session.userId), note))
        }
}
```

- [ ] **Step 2: `DependenciesConfiguration.kt` の `StockControllerFactory` provider を更新**

```kotlin
provide<StockControllerFactory> {
    val ss = resolve<StockService>()
    val srs = resolve<StockRegisterService>()
    val ps = resolve<ProductService>()
    val hs = resolve<HouseholdService>()
    val db = resolve<Database>()
    StockControllerFactory { session -> StockController(ss, srs, ps, hs, session, db) }
}
```

import 削除(他で使われていない場合): `ProductRepository`、`HouseholdRepository`
import 追加: `import net.brightroom.mindstock.application.service.product.ProductService`

### Task 7.5: コンパイル確認 + Controller unit test の更新

**Files:**
- Modify: `backend/api/src/test/kotlin/.../presentation/rpc/household/HouseholdControllerTest.kt`
- Modify: `backend/api/src/test/kotlin/.../presentation/rpc/catalog/CatalogControllerTest.kt`
- Modify: `backend/api/src/test/kotlin/.../presentation/rpc/product/ProductControllerTest.kt`
- Modify: `backend/api/src/test/kotlin/.../presentation/rpc/stock/StockControllerTest.kt`

- [ ] **Step 1: コンパイル確認(本体)**

Run: `./gradlew :backend:api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Controller unit test を新シグネチャに更新**

各 ControllerTest で次のパターンの書き換えを行う:

1. `mockk<*Repository>()` を削除し、対応する `mockk<*Service>()` を導入
2. Controller インスタンス化引数を新コンストラクタに合わせる
3. assertion で `RpcError.NotFound(resource, id)` を期待していたものは `RpcError.NotFound(message)` に書き換え(該当があれば)
4. テストデータ生成で `userRepository.findProfileById(...)` を mock していた箇所を `userService.findById(...)` の mock に変更
5. `tx` mock の型パラメータは Service 戻り値の non-null 型に合わせる(例: `Household?` を期待していた assertion は `Household`)

例(`HouseholdControllerTest.kt` の `findOf` テストの再構築):

```kotlin
test("findOf delegates to HouseholdService and returns Ok") {
    val householdService = mockk<HouseholdService>()
    val householdRegisterService = mockk<HouseholdRegisterService>(relaxed = true)
    val userService = mockk<UserService>()
    val database = mockk<Database>()
    val userId = UserId(Uuid.parse("00000000-0000-0000-0000-000000000001"))
    val session = MindstockSession(
        identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject("sub-1")),
        userId = userId,
        exp = Instant.fromEpochMilliseconds(Long.MAX_VALUE),
        callId = Uuid.random(),
    )
    val household = Household(/* ... appropriate test data ... */)
    every { householdService.findOf(userId) } returns household

    mockkStatic("net.brightroom.mindstock.configuration.transaction.TransactionKt")
    coEvery {
        net.brightroom.mindstock.configuration.transaction
            .tx<Household>(any(), any(), any())
    } coAnswers {
        val block = arg<suspend () -> RpcResult<Household, RpcError>>(2)
        block()
    }

    val impl = HouseholdController(
        householdService, householdRegisterService, userService, session, database,
    )
    val r = runBlocking { impl.findOf() }
    r shouldBe RpcResult.Ok(household)
}
```

各 Controller test ファイルでこのパターンに整える。

- [ ] **Step 3: 全 unit test 実行**

Run: `./gradlew :backend:api:test`
Expected: BUILD SUCCESSFUL

### Task 7.6: Phase 7 commit

- [ ] **Step 1: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(controller): Repository 直接呼び出しを除去、Service 経由に統一

- 4 Controller (Household/Catalog/Product/Stock) から *Repository 注入を削除
- 集約取得・存在チェックは Service.findById 経由(throws → tx() が NotFound に翻訳)
- DependenciesConfiguration の各 Factory provider を Service ベースに変更
  - UserRepository → UserService
  - HouseholdRepository → HouseholdService
  - CatalogItemRepository → CatalogItemService
  - ProductRepository → ProductService
- Controller unit test の mock を Repository → Service に書き換え

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §3.9, §3.10

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# Phase 8: E2E test 更新 + 最終確認

E2E test の `RpcError.NotFound` assertion を `(resource, id)` から `(message)` ベースに書き換え、メッセージ固定値で安定 contract を担保する。

### Task 8.1: 6 個の E2E test を更新

**Files:**
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/household/HouseholdRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/catalog/CatalogRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/product/ProductRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/stock/StockRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserPublicRpcServiceE2eTest.kt`
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/user/UserRpcServiceE2eTest.kt`

- [ ] **Step 1: `RpcError.NotFound(resource, id)` 形式の assertion を `RpcError.NotFound(message)` に書き換え**

各 E2E test 内、以下のパターンを置換:

```kotlin
// BEFORE
err.shouldBeInstanceOf<RpcError.NotFound>()
err.resource shouldBe "household"
err.id shouldBe "$householdId"

// AFTER
err.shouldBeInstanceOf<RpcError.NotFound>()
err.message shouldBe "household not found: $householdId"
```

メッセージ文字列の対応表:
- `household not found: <id>`
- `household not found for user: <userId>`(`findOf` の場合)
- `user not found: <id>`
- `user not found: subject=<sub>`(`findProfileByAuthIdentity` 経由)
- `catalog item not found: <id>`
- `product not found: <id>`
- `product not found: household=<householdId>, catalog_item=<catalogItemId>`

各 E2E ファイルで該当 assertion を全部更新。

- [ ] **Step 2: nullable 撤廃済 RPC の assertion 調整**

`HouseholdRpcService.findOf` / `CatalogRpcService.findById` / `ProductRpcService.find` などが nullable から non-null に変わったので、関連テストで:

- 成功パスは `RpcResult.Ok<T>` (`<T?>` でなく `<T>`) を期待
- 「無い」パスは `RpcResult.Err<RpcError>` で `RpcError.NotFound` を期待

例(`HouseholdRpcServiceE2eTest`):
```kotlin
// BEFORE
test("findOf returns null when user has no household") {
    e2eTest {
        // ...
        val r = rpc.findOf()
        r.shouldBeInstanceOf<RpcResult.Ok<Household?>>()
        r.value shouldBe null
    }
}

// AFTER
test("findOf returns Err(NotFound) when user has no household") {
    e2eTest {
        // ...
        val r = rpc.findOf()
        r.shouldBeInstanceOf<RpcResult.Err<RpcError>>()
        val err = r.error
        err.shouldBeInstanceOf<RpcError.NotFound>()
        err.message shouldContain "household not found"
    }
}
```

- [ ] **Step 3: `StockRpcServiceE2eTest` の `list` 戻り値型を `Stocks` に**

```kotlin
// BEFORE
val r = rpc.list(householdId)
r.shouldBeInstanceOf<RpcResult.Ok<List<Stock>>>()
r.value shouldHaveSize 3

// AFTER
val r = rpc.list(householdId)
r.shouldBeInstanceOf<RpcResult.Ok<Stocks>>()
r.value.size() shouldBe 3
// 中身の確認は r.value.list[0] でアクセス
```

### Task 8.2: 全 check 実行

- [ ] **Step 1: 全テスト実行**

Run: `./gradlew :backend:api:check`
Expected: BUILD SUCCESSFUL (unit + integration)

### Task 8.3: Phase 8 commit + 最終確認

- [ ] **Step 1: 残作業のチェック**

```bash
git grep -E "RpcError\.NotFound\(resource" -- backend/
# Expected: no results
git grep -E "\.findById\(.*\) \?:" -- backend/api/src/main
# Expected: no results in Controller code
git grep -E "Repository" -- backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/
# Expected: no results (Controllers don't import Repository)
```

- [ ] **Step 2: commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
test(e2e): RpcError.NotFound assertion を (message) ベースへ + nullable 撤廃に追従

- 6 E2E test (Household/Catalog/Product/Stock/UserPublic/User) の
  RpcError.NotFound assertion を (resource, id) → (message) 形式に書き換え
- メッセージ文字列は spec §3.5 の規約に従って固定値 assertion で contract を担保
- nullable 撤廃された RPC method (findOf / findById / find) の
  「無い」パスを Err(NotFound) アサートに変更
- StockRpcServiceE2eTest.list の戻り値を Stocks にアクセス

ref: docs/superpowers/specs/2026-05-29-controller-repository-decoupling-design.md §5.5

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

# 自己レビューチェックリスト(実装者向け)

各 phase 終了時 + 完了時に確認:

- [ ] `./gradlew :backend:api:check` が green
- [ ] `git grep "RpcError\\.NotFound(resource"` で旧形式 assertion が残っていない
- [ ] `git grep "Repository" -- backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/` で Controller が Repository を import していない
- [ ] `git grep "asList()" -- domain backend/ rpc/` で wrapper の `asList()` 呼び出しが残っていない
- [ ] `git grep "\\.size\\b" -- domain backend/ rpc/` で wrapper の `.size` プロパティ参照が残っていない(stdlib `.size` は OK)
- [ ] `git grep "ResourceNotFoundException" -- backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/` で Controller が ResourceNotFoundException を直接 catch していない(tx() が集約捕捉する設計)

# 想定外の事象とリカバリ

- **wrapper の `.size` / `.asList()` 置換漏れ**: コンパイル時に検出されるため、エラーを順次修正。`.size` プロパティが消えても `List<T>.size`(stdlib)とは別物なので衝突なし
- **`MindstockAuthPlugin` の runCatching が想定外例外を握り込む**: `findProfileByAuthIdentity` 内の `ResourceNotFoundException` のみ catch すべきだが、`runCatching { ... }.getOrNull()` は全例外を吸収する。spec 上は許容(認証時に Internal を 401 にダウングレードする副作用は許容範囲)。気になる場合は `runCatching { ... }.recover { if (it is ResourceNotFoundException) null else throw it }.getOrThrow()` のような明示形に切り替え可能
- **kotlinx-rpc が `Stocks` をクライアント側で deserialize できない**: `@Serializable` が付いていれば自動で対応するはず。万一エラーが出たら、`:rpc` モジュールに `Stocks` が export されているか確認(`:domain` の `commonMain` にあるので問題ないはず)
