# Controller → Repository 直接依存の排除 / 戻り値の non-null 化 Design

## 1. 背景とゴール

PR #66(Ktor 構成見直し)merge 後のレビューで、複数 Controller が `*Repository.findById(...)` を直接呼んでいることが指摘された。例:

```kotlin
// HouseholdController.invite — 違反
val household = householdRepository.findById(householdId)
    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
val inviteeProfile = userRepository.findProfileById(invitee)
    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$invitee"))
householdRegisterService.invite(household, inviteeProfile.userId, role)
```

問題点:

1. **層責務の漏れ**: presentation 層の Controller が application 層の Repository に直接アクセスしており、本来 Service が担うべき orchestration 責務を Controller が肩代わりしている
2. **nullable 戻り値の連鎖**: Repository が `T?` を返し、Controller が null チェック + `RpcError.NotFound` 変換、というパターンが Controller に滲み出している。Service が間に入っていれば「非存在は事象に沿った例外で通知する」契約に閉じ込められる
3. **一覧の raw List 露出**: `StockService.list(household): List<Stock>` だけが domain wrapper を介さず raw `List<Stock>` を返しており、他 wrapper (`Products` / `CatalogItems` / `StockMovements` / `HouseholdMembers`)との不整合

本 spec ではこれらを一括で整理する。

### 1.1 ゴール

- 全 Controller から `*Repository` の import / 注入を除去し、Service だけを依存する形に統一
- Repository / Service の戻り値を **non-null** に統一。単一値の不在は **`ResourceNotFoundException`** で通知
- 全 RPC 戻り値の nullable も撤廃(`RpcResult<T?, E>` を全廃、`Ok(T)` か `Err(RpcError.NotFound)` で表現)
- domain wrapper の慣習統一(`Stocks` 新規作成、既存 4 wrapper の規約変更)
- `tx()` ヘルパーに `ResourceNotFoundException` 捕捉を追加し、Controller 側の try/catch を不要に
- `RpcError.NotFound(resource, id)` を `(message)` に簡素化(クライアントは呼び出しコンテキストから意味を組み立てるため、構造化フィールドより message パススルーで十分)

### 1.2 非ゴール

- `IllegalArgumentException` → `BadRequest` の集約マッピング(別 spec)
- `Conflict` 発火元の整備(DB UNIQUE 違反等)(別 spec)
- gRPC 化、JWT 期限切れ実装変更 等の前 PR 範囲

### 1.3 適用する規約

本 spec で確立 or 適用するコーディングルールは `docs/coding-rules.md` に記載。本 spec はその規約を既存コードに適用するための作業:

- 層責務と依存方向
- Controller は Repository を直接呼ばない
- Service の戻り値は non-null、不在は例外で通知
- Repository も non-null
- nullable は事前承認制
- Domain collection wrapper の慣習(`val list` + `fun size(): Int`、`asList()` 不要)
- Service はビジネスロジックを持たない

## 2. 層責務とディレクトリ構成

```
domain/
├── exception/
│   └── ResourceNotFoundException.kt        NEW: 全層から依存可
└── model/stock/
    └── Stocks.kt                            NEW: List<Stock> の domain wrapper

:backend:core
├── application/
│   ├── repository/                          すべて non-null(throws on missing 契約)
│   │   ├── household/HouseholdRepository.kt        findOf/findById → non-null
│   │   ├── catalog/CatalogItemRepository.kt        findById → non-null
│   │   ├── product/ProductRepository.kt            findById/find → non-null、listOf 不変
│   │   ├── stock/StockRepository.kt                stockOf 不変、stocksOf → Stocks、movementHistory 不変
│   │   └── user/UserRepository.kt                  findProfileByAuthIdentity/findProfileById → non-null
│   └── service/
│       ├── catalog/CatalogItemService.kt   findById → non-null
│       ├── household/HouseholdService.kt   findOf → non-null、findById 追加(non-null)
│       ├── product/ProductService.kt       find → non-null、findById 追加、listOf 不変
│       ├── stock/StockService.kt           list の戻り値 → Stocks
│       └── user/
│           ├── UserService.kt              NEW: findById(userId): Profile (throws)
│           └── UserRegisterService.kt      不変
└── infrastructure/datasource/...           実装側で SQL 空結果 → throws ResourceNotFoundException

:rpc
├── RpcError.kt                              NotFound(message: String) に簡素化
└── StockRpcService.kt                       list の戻り値: RpcResult<Stocks, RpcError>

:backend:api
├── configuration/transaction/Transaction.kt tx() で ResourceNotFoundException 捕捉して Err(NotFound)
└── presentation/rpc/.../*Controller.kt      Repository import 削除、Service 経由のみ
                                             *ControllerFactory も UserRepository 注入除去
```

### 2.1 依存方向

- `ResourceNotFoundException` は `domain` モジュール → 全層が import 可
- `:backend:api/configuration/transaction/Transaction.kt`(configuration 層)は `:rpc` の `RpcError` + domain の `ResourceNotFoundException` 両方を知る。configuration の正当な責務
- Service と DataSource は presentation 概念(`RpcError` / `MindstockSession`)を import しない
- Repository も Service もすべて non-null

## 3. 具体メソッド設計

### 3.1 `domain/exception/ResourceNotFoundException.kt`

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

### 3.2 `domain/model/stock/Stocks.kt`

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

### 3.3 既存 wrapper の慣習統一

`Products` / `CatalogItems` / `StockMovements` / `HouseholdMembers` について:

- `val size: Int get() = list.size` → `fun size(): Int = list.size` に変更
- `fun asList(): List<T> = list.toList()` を **削除**(`.list` で直接アクセス)
- 呼び出し側を全プロジェクトスキャンして:
  - `.size` プロパティアクセス → `.size()` 呼び出しに置換
  - `.asList()` 呼び出し → `.list` に置換

### 3.4 Repository interface 変更

| ファイル | 変更内容 |
|---|---|
| `HouseholdRepository.kt` | `findOf(userId): Household?` → `Household`(throws)、`findById(id): Household?` → `Household`(throws) |
| `CatalogItemRepository.kt` | `findById(id): CatalogItem?` → `CatalogItem`(throws) |
| `ProductRepository.kt` | `findById(id): Product?` → `Product`(throws)、`find(household, catalogItem): Product?` → `Product`(throws) |
| `StockRepository.kt` | `stocksOf(household): List<Stock>` → `Stocks` |
| `UserRepository.kt` | `findProfileByAuthIdentity(identity): Profile?` → `Profile`(throws)、`findProfileById(id): Profile?` → `Profile`(throws) |

各 throws 契約は KDoc に明記し、メッセージ形式も例示する。

### 3.5 DataSource (infrastructure) 実装変更

各 `*DataSource` の該当メソッドを以下パターンに:

```kotlin
// Before
override fun findById(id: HouseholdId): Household? =
    HouseholdsTable.selectAll().where { ... }.firstOrNull()?.toHousehold()

// After
override fun findById(id: HouseholdId): Household =
    HouseholdsTable.selectAll().where { ... }.firstOrNull()?.toHousehold()
        ?: throw ResourceNotFoundException("household not found: $id")
```

メッセージ形式の規約:
- `"household not found: $id"`
- `"user not found: $id"`(`findProfileById` / `findProfileByAuthIdentity` 両方とも、識別子の形式は呼び出し側に合わせる)
- `"catalog item not found: $id"`
- `"product not found: $id"`(`findById`)
- `"product not found: household=$householdId, catalog_item=$catalogItemId"`(`find`)

### 3.6 Service クラス変更

```kotlin
// HouseholdService.kt
class HouseholdService(private val householdRepository: HouseholdRepository) {
    fun findOf(userId: UserId): Household = householdRepository.findOf(userId)
    fun findById(id: HouseholdId): Household = householdRepository.findById(id)
}

// CatalogItemService.kt
class CatalogItemService(private val catalogItemRepository: CatalogItemRepository) {
    fun findById(id: CatalogItemId): CatalogItem = catalogItemRepository.findById(id)
    fun search(query: String, limit: Int = 50): CatalogItems = catalogItemRepository.search(query, limit)
}

// ProductService.kt
class ProductService(private val productRepository: ProductRepository) {
    fun findById(id: ProductId): Product = productRepository.findById(id)
    fun find(household: Household, catalogItem: CatalogItem): Product =
        productRepository.find(household, catalogItem)
    fun listOf(household: Household): Products = productRepository.listOf(household)
}

// StockService.kt
class StockService(private val stockRepository: StockRepository) {
    fun get(product: Product): Stock = stockRepository.stockOf(product)
    fun list(household: Household): Stocks = stockRepository.stocksOf(household)
    fun getMovementHistory(product: Product, limit: Int = 50): StockMovements =
        stockRepository.movementHistory(product, limit)
}

// UserService.kt (NEW)
class UserService(private val userRepository: UserRepository) {
    fun findById(userId: UserId): Profile = userRepository.findProfileById(userId)
}
```

### 3.7 `RpcError` 変更

```kotlin
// :rpc/RpcError.kt
@Serializable
sealed interface RpcError {
    @Serializable data class Unauthorized(val reason: String) : RpcError
    @Serializable data class NotFound(val message: String) : RpcError   // (resource, id) から (message) へ
    @Serializable data class BadRequest(val field: String, val reason: String) : RpcError
    @Serializable data class Conflict(val reason: String) : RpcError
    @Serializable data class Internal(val reason: String) : RpcError
}
```

### 3.8 `tx()` 改修

`backend/api/src/main/kotlin/.../configuration/transaction/Transaction.kt`:

```kotlin
suspend fun <T> tx(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    val start = Clock.System.now()
    if (start > session.exp) {
        emitLog(session, start, "Err:Unauthorized(expired)")
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return try {
        val result = supervisorScope {
            newSuspendedTransaction(db = database) { block() }
        }
        emitLog(session, start, when (result) {
            is RpcResult.Ok -> "Ok"
            is RpcResult.Err -> "Err:${result.error::class.simpleName}"
        })
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResourceNotFoundException) {
        emitLog(session, start, "Err:NotFound")
        RpcResult.Err(RpcError.NotFound(message = e.message.orEmpty()))
    } catch (e: Throwable) {
        logger.error(e) { "unhandled exception during RPC call_id=${session.callId} user_id=${session.userId}" }
        emitLog(session, start, "Throwable:${e::class.simpleName}")
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
```

ポイント:
- `ResourceNotFoundException` 捕捉を `Throwable` より前に置く(順序が安全性)
- メッセージは Service / Repository が組み立てたものをそのまま wire に乗せる
- `IllegalArgumentException` → `BadRequest` の集約マッピングは本 spec のスコープ外(Future work)

### 3.9 Controller 書き換えパターン

全 Controller から `*Repository` の import / 注入を除去し、Service を呼ぶだけにする。例:

#### Before — `HouseholdController.invite`

```kotlin
val household = householdRepository.findById(householdId)
    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "household", id = "$householdId"))
val inviteeProfile = userRepository.findProfileById(invitee)
    ?: return@tx RpcResult.Err(RpcError.NotFound(resource = "user", id = "$invitee"))
householdRegisterService.invite(household, inviteeProfile.userId, role)
RpcResult.Ok(Unit)
```

#### After

```kotlin
val household = householdService.findById(householdId)
val inviteeProfile = userService.findById(invitee)
householdRegisterService.invite(household, inviteeProfile.userId, role)
RpcResult.Ok(Unit)
```

- try/catch 不要(`tx()` が `ResourceNotFoundException` を集約捕捉)
- `Controller` の constructor から `HouseholdRepository` / `UserRepository` 注入を除去
- 対応する `*ControllerFactory` interface(`HouseholdControllerFactory.create(session)` 等)の lambda body も同期して更新

### 3.10 Factory + DependenciesConfiguration の更新

各 `*ControllerFactory` provider lambda から不要になった Repository resolve を除去し、対応する Service だけを resolve する。

- `HouseholdControllerFactory`: `UserRepository` 注入を `UserService` に置換
- `CatalogControllerFactory`: `CatalogItemRepository` 注入を削除(Controller が `catalogItemService` 経由になるため)
- `ProductControllerFactory`: `HouseholdRepository` / `CatalogItemRepository` / `ProductRepository` 注入を削除、対応 Service だけを resolve
- `StockControllerFactory`: `HouseholdRepository` / `ProductRepository` 注入を削除、`HouseholdService` を追加注入

## 4. RPC API の戻り値型変更

`StockRpcService.list` の戻り値型を `RpcResult<List<Stock>, RpcError>` から `RpcResult<Stocks, RpcError>` に変更。

他の RPC interface(`HouseholdRpcService.findOf`, `CatalogRpcService.findById`, `ProductRpcService.find` 等)で現在 nullable な戻り値はすべて non-null に統一する:

| RPC method | Before | After |
|---|---|---|
| `HouseholdRpcService.findOf()` | `RpcResult<Household?, RpcError>` | `RpcResult<Household, RpcError>`(無ければ `Err(NotFound)`) |
| `CatalogRpcService.findById(id)` | `RpcResult<CatalogItem?, RpcError>` | `RpcResult<CatalogItem, RpcError>` |
| `ProductRpcService.find(...)` | `RpcResult<Product?, RpcError>` | `RpcResult<Product, RpcError>` |

クライアント側はこれらの呼び出し結果を `Ok(T)` / `Err(NotFound)` で分岐するパターンに統一する。

## 5. テスト戦略

### 5.1 Repository / DataSource (integration)

- 単一値 lookup の各メソッド(`findById` / `findOf` / `findProfileById` / `findProfileByAuthIdentity` / `find`)について、SQL 結果が空のケースで `ResourceNotFoundException` が throw されるテストを追加
- メッセージ内容も assertion で押さえる(`shouldBe "household not found: $id"`)
- 戻り値が wrapper の場合: 空 wrapper が返ることを確認(例外を throw しないことを担保)

### 5.2 Service (unit)

- ほぼ薄い pass-through なので、Repository を mockk で stub し、各 `findById` 等の委譲を 1 ケースずつ確認する程度で十分(Service にビジネスロジックが無いため網羅性は薄くてよい)
- 新規 `UserService` も同パターン

### 5.3 `tx()` (`TxWithGuardTest.kt`)

既存テストに以下を追加:

- `block` 内で `ResourceNotFoundException` が throw → `Err(RpcError.NotFound(message))` が返り、message が thrown exception の message と一致する
- `Throwable` (任意の他の `RuntimeException`)が throw → `Err(Internal)` のままで `NotFound` には流れない

### 5.4 Controller unit test

- 既存テストの `RpcError.NotFound(resource, id)` assertion を `RpcError.NotFound(message)` に書き換え
- Controller constructor から削除した `UserRepository` 等の mock を削除
- 「無い id を渡して NotFound」のケースは Controller 層では Service が throws する mock 設定で書く

### 5.5 E2E test

- `RpcError.NotFound` の assertion フィールド変更(`resource` / `id` → `message`)
- メッセージ文字列は §3.5 の規約に従って固定値 assertion で押さえる
- 既存 「無い household / product / user 等を返してた」テストが `Err(NotFound(message))` を期待する形に書き換え

## 6. 実装順序(後続の Plan で詳細化)

依存関係を考慮して以下の順で進める。各 step ごとに `:backend:api:check` が green であることを担保する。

1. **Domain 追加** ─ `ResourceNotFoundException` と `Stocks` を追加(他の変更には影響しない、独立 commit)
2. **既存 domain wrapper の慣習統一** ─ `Products`/`CatalogItems`/`StockMovements`/`HouseholdMembers` の `val size` → `fun size()`、`asList()` 削除 + 全呼び出し置換(機械的、独立 commit)
3. **Repository interface 改修 + DataSource 実装** ─ 5 つの Repository を non-null + throws へ。infrastructure DataSource も同時に更新(SQL 空結果 → throws)。DataSource integration test 更新
4. **Service 改修 + 新規 `UserService`** ─ 全 Service の戻り値を non-null へ。`UserService` 追加。Service unit test 最小限
5. **`RpcError.NotFound` 簡素化 + RPC interface non-null 化** ─ `:rpc/RpcError.kt`、`StockRpcService.list` の `Stocks` 化、他 RPC の nullable 撤廃
6. **`tx()` の `ResourceNotFoundException` 捕捉追加** ─ `TxWithGuardTest` も同時に
7. **Controller / Factory / DI 改修** ─ Repository 直接呼び出しの撲滅。Controller unit test 更新
8. **E2E test の `RpcError.NotFound` assertion 書き換え + 既存パスの動作確認** ─ 最終 green check

## 7. リスクと緩和策

- **`tx()` 内 `ResourceNotFoundException` 捕捉順序の取り違え**: `Throwable` catch-all より前に置かないと、すべて `Internal` に落ちる。実装時にコメント明記 + テストで guard
- **メッセージ文字列を contract として扱うことの脆さ**: 将来 i18n やリッチエラーが欲しくなった時に再構造化が必要。本 spec の射程内では許容(crash-free な情報量を維持)
- **wrapper の慣習変更の波及範囲**: `.size` / `.asList()` 呼び出し全箇所を spotless / `git grep` で漏れなく置換する必要。手作業で見逃すと runtime エラーではなく compile error が出るため検出は容易

## 8. Out of scope(将来別 spec)

- `IllegalArgumentException` → `RpcError.BadRequest` の集約マッピング
- DB UNIQUE 違反 → `RpcError.Conflict` のマッピング
- ban / revocation の即時反映(WS 接続中の権限変更検出)
- metrics / tracing の本格化
