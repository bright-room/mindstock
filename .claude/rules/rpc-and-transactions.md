---
paths:
  - "backend/api/**/presentation/rpc/**/*.kt"
  - "backend/api/**/configuration/**/*.kt"
  - "rpc/**/*.kt"
---

# RPC and Transactions

kotlinx-rpc 0.10.x と Ktor WebSocket を組み合わせた RPC 層の規約。`@Rpc` annotation、Json 分離、`tx()` ヘルパー、Request / Response 型の扱いを含む。

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

### `tx()` ヘルパー

- 場所: `backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/transaction/Transaction.kt`
- 役割: `supervisorScope { newSuspendedTransaction(db) { ... } }` を 1 行で書けるようにする transaction ヘルパー
- **DB を触る RPC method は `tx(database) { ... }` で包む**。`ExposedTransactionPlugin` は WS upgrade 時 1 回しか張らないため、RPC method ごとに transaction を張り直す必要がある
- `supervisorScope` で包むのは、`newSuspendedTransaction` の cancellation が Ktor scope を倒すのを防ぐため
- **DB を触らない RPC method**(S3 upload だけ、外部 HTTP を叩くだけ、in-memory 処理だけ、等)は `tx()` を使わなくてよい

### RPC 戻り値

- 全 RPC メソッドの戻り値は **`RpcResult<T, RpcError>`**
- `T` は **non-null**(`T?` は禁止)
- `RpcError` は `:rpc/RpcError.kt` の sealed interface:
  - `Unauthorized` / `NotFound` / `BadRequest` / `Conflict` / `Internal`
- 例外メッセージ・型は安定 contract(Controller がパターンマッチする)。リネーム時は両側を同時に更新する

### RPC 引数・戻り値の型(腐敗防止層の判断)

presentation 層は「ユーザ入力 ↔ application 層の橋渡し」+ 腐敗防止層。Request / Response 型を作る判断軸は **「API 仕様の型と application 内部の型がずれるか」**:

- **ずれない / マッピング不要の場合**: VO / ID / 集約 / ファーストクラスコレクションを **そのまま** RPC method の引数・戻り値の型として使う。中間 DTO を作らない
- **ずれる場合**(API 仕様として複合パラメータを 1 つにまとめたい / 内部表現を露出したくない / 既存 API の互換を保つ): `presentation/rpc/<ctx>/` 配下に Request / Response data class(`@Serializable`)を作りマッピングする

kotlinx-serialization の標準 deserialize は `data class` / `@JvmInline value class` のコンストラクタを呼ぶため、`init { require(...) }` の不変条件は wire 経由でも保たれる(IAE は Controller で `RpcError.BadRequest` に翻訳される)。よって「集約を丸ごと RPC 引数にしたら不変条件が破壊される」という心配は実質不要。

### Routing

- 認証レルムで rpc route を nest: `authenticate("user") { rpc("/api/v1/...") { /* ... */ } }`
- 中の `registerService<T> { ImplClass(...) }` で 1 service ずつ登録

## Why

- `@Rpc` annotation を強制することで、`RemoteService` 継承 → 0.10.x で ERROR の罠を避ける
- Json の `ClassDiscriminatorMode` を間違えると Krpc envelope を decode できず **静かに失敗する**(ログにも出にくい)ため、Json を 2 つに物理分離する
- `tx()` を「DB を触る RPC method のみ必須」とすることで、不必要な `newSuspendedTransaction` を張らずに済む
- `supervisorScope` なしの `newSuspendedTransaction` は失敗時に親 scope を巻き込んで Ktor server まで倒してしまう
- Request / Response 型は「腐敗防止層」が必要な時のみ作るべきで、内部表現と API 仕様が一致するなら中間 DTO は冗長な詰め替えコードを増やすだけ

## How to apply

### ✅ RPC method を tx() で包む(DB を触る場合)

```kotlin
class StockController(
    private val stockService: StockService,
    private val session: MindstockSession,
    private val database: Database,
) : StockRpcService {
    override suspend fun replenish(
        stockId: StockId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError> = tx(database) {
        stockService.replenish(stockId, quantity, note, requireNotNull(session.userId))
        RpcResult.Ok(Unit)
    }
}
```

### ✅ DB を触らない RPC(tx() 不要)

```kotlin
class AttachmentController(
    private val attachmentService: AttachmentService,
) : AttachmentRpcService {
    override suspend fun upload(file: AttachmentBytes): RpcResult<AttachmentUrl, RpcError> =
        RpcResult.Ok(attachmentService.upload(file))  // S3 のみ、DB を触らない
}
```

### ✅ Routing(factory は非 suspend)

```kotlin
fun Application.routingConfigure() {
    val stockService by dependencies   // ← suspend 解決を先取り
    val database by dependencies

    routing {
        authenticate("user") {
            rpc("/api/v1/stock") {
                registerService<StockRpcService> {
                    StockController(stockService, session = sessionOf(applicationCall), database)
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
