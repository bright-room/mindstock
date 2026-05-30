---
paths:
  - "backend/api/**/presentation/rpc/**/*.kt"
  - "backend/api/**/configuration/**/*.kt"
  - "rpc/**/*.kt"
---

# RPC and Transactions

kotlinx-rpc 0.10.x と Ktor WebSocket を組み合わせた RPC 層の規約。`@Rpc` annotation、Json 分離、`rpcBoundary`(presentation 境界)、Request / Response 型の扱いを含む。

## Rule

### RPC service interface

- **`@kotlinx.rpc.annotations.Rpc` を必ず付ける**。`RemoteService` 継承は使わない(0.10.x で `@Deprecated(ERROR)`)
- service interface は `<Ctx>RpcService` suffix、実装(Controller)は `<Ctx>Controller`(`presentation.rpc.<ctx>` パッケージ)
- メソッド名は domain command 名そのまま(`Query` / `Command` suffix を付けない)
- 認証なし public service(`UserPublicRpcService` 等)は別 interface に分離

### Service 実装(Controller)の lifecycle

- **WebSocket 接続単位で 1 度だけ instantiate**(`registerService<T> { factory }` の factory は接続確立時に 1 回呼ばれる)
- factory は **非 suspend**。Ktor DI の `dependencies.resolve<T>()` は suspend なので、`RoutingConfiguration` で `val handler by dependencies` で **先に解決** し factory closure 内で参照する
- `ApplicationCall` は `this.applicationCall`(`configuration.auth.applicationCall` extension)で取得
- 認証 user は constructor で `MindstockSession` を受けて参照する

### Json 分離

- `Krpc` plugin は **`ClassDiscriminatorMode.POLYMORPHIC` の Json を要求**
  - `KrpcJson` を `shared/.../extensions/kotlinx/serialization/Json.kt` に定義
- HTTP の `ContentNegotiation` は **`CustomJson`(`NONE`)** を使う
- `RoutingConfiguration` の `install(Krpc) { serialization { json(KrpcJson) } }` と HTTP `install(ContentNegotiation) { json(CustomJson) }` を **使い分ける**

### トランザクション境界と `rpcBoundary`

- **トランザクション境界は DataSource メソッド内**。各 DataSource は `Database` をコンストラクタで受け、メソッド本体を `newSuspendedTransaction(db = database) { ... }` で囲む。Repository interface / Service は `suspend`(詳細は [software-architecture](software-architecture.md))
- **presentation は `rpcBoundary(session) { ... }`** で包む。場所: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/rpc/RpcBoundary.kt`
  - `rpcBoundary` は **transaction を張らない**。担うのは ① `session.exp` guard ② `supervisorScope { block() }` ③ 例外 → `RpcError` 翻訳 ④ 1 行 JSON 呼び出しログ
  - `block` は **ドメイン値 `T` を返す**。成功時は `rpcBoundary` が `RpcResult.Ok(result)` で包む(Controller 側で `RpcResult.Ok(...)` を書かない)
  - 例外翻訳ラダー: `CancellationException` 再送出 → `ResourceNotFoundException` → `NotFound` → `IllegalArgumentException` → `BadRequest` → その他 `Throwable` → `Internal`
  - `supervisorScope` は kRPC server scope へ cancellation が leak するのを防ぐため維持
- **DB を触る / 触らないに関わらず RPC method は `rpcBoundary` で包む**(guard・ログ・例外翻訳を効かせるため)。DB を触る場合の transaction は呼び出し先の DataSource が自分で張る
- **例外**: 認証層(`MindstockAuthPlugin`)は RPC 境界の外なので `rpcBoundary` を通らない。`findProfileByAuthIdentity` 用に独立した tx を持つ(DataSource が自前 tx を張る今は技術的には外側ラップ不要だが、意図明示のため残置。ネストは `useNestedTransactions = true` で吸収)

### RPC 戻り値

- 全 RPC メソッドの戻り値は **`RpcResult<T, RpcError>`**
- `T` は **non-null**(`T?` は禁止)
- `RpcError` は `:rpc/RpcError.kt` の sealed interface:
  - `Unauthorized(reason)` / `NotFound(message)` / `BadRequest(reason)` / `Conflict(reason)` / `Internal(reason)`
- 例外 → `RpcError` の翻訳は `rpcBoundary` が一手に担う(ResourceNotFound→NotFound / IllegalArgumentException→BadRequest / その他→Internal)。Controller は例外を catch しない
- 例外メッセージ・型は安定 contract。リネーム時は両側を同時に更新する

### RPC 引数・戻り値の型(腐敗防止層の判断)

presentation 層は「ユーザ入力 ↔ application 層の橋渡し」+ 腐敗防止層。Request / Response 型を作る判断軸は **「API 仕様の型と application 内部の型がずれるか」**:

- **ずれない / マッピング不要の場合**: VO / ID / 集約 / ファーストクラスコレクションを **そのまま** RPC method の引数・戻り値の型として使う。中間 DTO を作らない
- **ずれる場合**(API 仕様として複合パラメータを 1 つにまとめたい / 内部表現を露出したくない / 既存 API の互換を保つ): `presentation/rpc/<ctx>/` 配下に Request / Response data class(`@Serializable`)を作りマッピングする

kotlinx-serialization の標準 deserialize は `data class` / `@JvmInline value class` のコンストラクタを呼ぶため、`init { require(...) }` の不変条件は wire 経由でも保たれる(IAE は `rpcBoundary` で `RpcError.BadRequest` に翻訳される)。よって「集約を丸ごと RPC 引数にしたら不変条件が破壊される」という心配は実質不要。

### Routing

- 認証レルムで rpc route を nest: `authenticate("user") { rpc("/api/v1/...") { /* ... */ } }`
- 中の `registerService<T> { ImplClass(...) }` で 1 service ずつ登録

## Why

- `@Rpc` annotation を強制することで、`RemoteService` 継承 → 0.10.x で ERROR の罠を避ける
- Json の `ClassDiscriminatorMode` を間違えると Krpc envelope を decode できず **静かに失敗する**(ログにも出にくい)ため、Json を 2 つに物理分離する
- トランザクション境界を DataSource に下ろすことで、presentation は transaction を意識せず、ユースケース全体ではなく「DB アクセス単位」が境界になる。`rpcBoundary` は transaction という関心から切り離され、guard・例外翻訳・log だけの薄い presentation 境界に保たれる
- append-only(実行時ロールが INSERT/SELECT のみ)+ 後勝ち read 前提のため、単一 INSERT は transaction 不要、複数 INSERT を伴う 3 メソッド(user/catalog/household の register・create)のみメソッド内 tx で原子性を確保する。横断トランザクションは現状存在しない
- `supervisorScope` なしの `newSuspendedTransaction` は失敗時に親 scope を巻き込んで Ktor server まで倒してしまうため、`rpcBoundary` 側で `supervisorScope` を維持する
- Request / Response 型は「腐敗防止層」が必要な時のみ作るべきで、内部表現と API 仕様が一致するなら中間 DTO は冗長な詰め替えコードを増やすだけ

## How to apply

### ✅ RPC method を rpcBoundary で包む(Controller は Database を持たない)

```kotlin
class StockController(
    private val stockService: StockService,
    private val stockRegisterService: StockRegisterService,
    private val productService: ProductService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun replenish(
        productId: ProductId,
        qty: Quantity,
        occurredAt: OccurredAt,
        note: Note,
    ): RpcResult<Unit, RpcError> = rpcBoundary(session) {
        val product = productService.findById(productId)
        // 末尾の Unit がそのまま RpcResult.Ok(Unit) に包まれる。RpcResult.Ok は書かない
        stockRegisterService.replenish(product, qty, occurredAt, requireNotNull(session.userId), note)
    }
}
```

### ✅ 値を返す RPC(rpcBoundary がドメイン値を Ok で包む)

```kotlin
override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
    rpcBoundary(session) {
        val product = productService.findById(productId)
        stockService.get(product)   // ← Stock をそのまま返す。boundary が RpcResult.Ok(stock) にする
    }
```

### ✅ Routing(factory は非 suspend、Controller は db を受けない)

```kotlin
fun Application.routingConfigure() {
    val stockFactory: StockControllerFactory by dependencies   // ← suspend 解決を先取り

    routing {
        route("/api/v1") {
            install(MindstockAuthPlugin) { /* ... */ }
            route("/") {
                install(RequireRegisteredUserPlugin)
                rpc("/stock") {
                    val session = call.attributes[MindstockSessionKey]
                    registerService<StockRpcService> { stockFactory.create(session) }
                }
            }
        }
    }
}
```

### ✅ API 仕様と application 型が一致 → VO / 集約を直接受ける

```kotlin
@Rpc
interface CatalogRpcService {
    suspend fun register(name: CatalogItemName, unit: CatalogItemUnit): RpcResult<CatalogItem, RpcError>
}
// CatalogItemName / CatalogItemUnit はそのまま VO、Request 型を作らない
```

### ✅ 型がずれる → Request 型でマッピング

```kotlin
@Serializable
data class ReplenishStockRequest(
    val stockId: StockId,
    val quantity: Quantity,
    val note: Note,
    val occurredAt: OccurredAt,
)

@Rpc
interface StockRpcService {
    suspend fun replenish(request: ReplenishStockRequest): RpcResult<Unit, RpcError>
}
// → Controller で request を分解して Service に渡す
```

### ❌ Json を取り違える

```kotlin
// アンチパターン: Krpc に CustomJson を渡してしまう
install(Krpc) { serialization { json(CustomJson) } }  // ← envelope decode 失敗で静かに死ぬ
```

## 関連

- spec: [docs/superpowers/specs/2026-05-30-coding-conventions-design.md](../../docs/superpowers/specs/2026-05-30-coding-conventions-design.md)
- spec(歴史): [docs/superpowers/specs/2026-05-25-rpc-layer-design.md](../../docs/superpowers/specs/2026-05-25-rpc-layer-design.md)
- rule: [software-architecture](software-architecture.md) — Controller の責務
- rule: [error-handling](error-handling.md) — 例外 → RpcError 翻訳の方針
