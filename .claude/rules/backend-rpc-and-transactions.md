---
paths:
  - "backend/api/**/presentation/rpc/**/*.kt"
  - "backend/api/**/configuration/**/*.kt"
  - "rpc/**/*.kt"
---

# RPC and Transactions

kotlinx-rpc 0.10.x と Ktor WebSocket を組み合わせた RPC 層の規約。`@Rpc` annotation、Json 分離、トランザクション境界(DataSource 自前)、Request / Response 型の扱いを含む。

## Rule

### RPC service interface

- **`@kotlinx.rpc.annotations.Rpc` を必ず付ける**。`RemoteService` 継承は使わない(0.10.x で `@Deprecated(ERROR)`)
- service interface は `<Ctx>RpcService` suffix、実装(Controller)は `<Ctx>Controller`(`presentation.rpc.<ctx>` パッケージ)
- メソッド名は domain command 名そのまま(`Query` / `Command` suffix を付けない)
- 認証なし public service(`UserPublicRpcService` 等)は別 interface に分離

### Service 実装(Controller)の lifecycle

- **WebSocket 接続単位で 1 度だけ instantiate**(`registerService<T> { factory }` の factory は接続確立時に 1 回呼ばれる)
- factory は **非 suspend**。Ktor DI の `dependencies.resolve<T>()` は suspend なので、`RoutingConfiguration` で `val handler by dependencies` で **先に解決** し factory closure 内で参照する
- `MindstockSession` は `sessionOf(call)`(`configuration/auth/SessionAccess.kt`)で取得する。`call.attributes[MindstockSessionKey]` を読み出す薄いヘルパーで、`applicationCall` という extension は存在しない
- 認証 user は constructor で `MindstockSession` を受けて参照する

### Json 分離

- `Krpc` plugin は **`ClassDiscriminatorMode.POLYMORPHIC` の Json を要求**
  - `KrpcJson` を `shared/.../extensions/kotlinx/serialization/Json.kt` に定義
- HTTP の `ContentNegotiation` は **`CustomJson`(`NONE`)** を使う
- `RoutingConfiguration` の `install(Krpc) { serialization { json(KrpcJson) } }` と HTTP `install(ContentNegotiation) { json(CustomJson) }` を **使い分ける**

### トランザクション境界(DataSource 自前)

- トランザクションは DataSource 実装内で `transaction(database) { }` を張る(`backend/core` の各 DataSource メソッド)。`tx()` ヘルパーと `ExposedTransactionPlugin` は廃止した
- Controller / Service は transaction を意識しない(DataSource が境界を持つ)
- `Database` は起動配線(P5)で生成し DataSource にコンストラクタ注入する
- 旧 `tx()` / plugin 方式は P4 で撤廃(WS upgrade 時 1 回しか張れない plugin 制約と、RPC method ごとに張り直す煩雑さを DataSource 自前境界で解消)

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

- 全 `@Rpc` service は **単一エンドポイント `rpc("/api/rpc")`** に相乗りし、1 つの WS 接続で全サービスを提供する(per-service パスは使わない)
- JWT 認証は `MindstockAuthPlugin`(app-level install)が WS ハンドシェイク時に行う。有効な JWT であれば未登録 Resident でも接続できる
- 登録要件はルートスコープでなく **各 RPC メソッド内のガードヘルパー** で宣言する
  - `requireRegistered` — デフォルト(フェイルクローズ)。未登録の場合は `RpcError.Unauthorized`
  - `allowUnregistered` — `register` / `whoami` 等のみに付ける
  - ガードヘルパーの実装は `configuration/guard/SessionGuard.kt`

## Why

- `@Rpc` annotation を強制することで、`RemoteService` 継承 → 0.10.x で ERROR の罠を避ける
- Json の `ClassDiscriminatorMode` を間違えると Krpc envelope を decode できず **静かに失敗する**(ログにも出にくい)ため、Json を 2 つに物理分離する
- トランザクション境界を DataSource に持たせることで、Controller / Service は DB の意識を持たず純粋な orchestration に徹せる
- `ExposedTransactionPlugin` は WS upgrade 時 1 回しか張れないため RPC method ごとに再利用できず、`tx()` ヘルパーによる method 単位の張り直しも呼び出し側の煩雑さを増やした。DataSource 自前境界でこれらを解消する
- Request / Response 型は「腐敗防止層」が必要な時のみ作るべきで、内部表現と API 仕様が一致するなら中間 DTO は冗長な詰め替えコードを増やすだけ

## How to apply

### ✅ RPC method(トランザクションは DataSource に任せる)

```kotlin
class StockController(
    private val stockService: StockService,
    private val session: MindstockSession,
) : StockRpcService {
    override suspend fun replenish(
        stockId: StockId,
        quantity: Quantity,
        note: Note,
    ): RpcResult<Unit, RpcError> =
        // 登録ガードは requireRegistered で宣言(residentId は block 引数で受ける)。
        // tx() 不要 — transaction は StockDataSource 内で transaction(database){} として張られる
        requireRegistered(session) { residentId ->
            stockService.replenish(stockId, quantity, note, residentId)
            RpcResult.Ok(Unit)
        }
}
```

### ✅ DB を触らない RPC

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
    val sessionService by dependencies

    routing {
        // 全サービスを単一エンドポイントに相乗り。JWT 認証は MindstockAuthPlugin(app-level)が担う
        rpc("/api/rpc") {
            registerService<StockRpcService> {
                StockController(stockService, session = sessionOf(call))
            }
            registerService<SessionRpcService> {
                SessionController(sessionService, session = sessionOf(call))
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
- rule: [backend-software-architecture](backend-software-architecture.md) — Controller の責務
- rule: [error-handling](error-handling.md) — 例外 → RpcError 翻訳の方針
