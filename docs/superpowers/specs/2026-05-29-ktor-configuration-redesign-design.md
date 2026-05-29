# `:backend:api` Ktor 構成見直し Design

## 1. 背景とゴール

`:backend:api` モジュールの Ktor 構成(`configuration/` 配下と `Main.kt`、`presentation/rpc/` 配下の Controller、build dependency)を見直す。プロジェクトはプレリリース段階で、利用者は居ない前提のため互換性破壊は許容する。

論点は以下の 4 つ:

1. Ktor plugin の棚卸し(無駄な plugin が入っていないか)
2. Routing から Controller を手 new している箇所の DI 化
3. Auth 周りの設計を「明らかに正しい」状態に再構築する
4. エラーハンドリング(`StatusPages` + 独自例外 throw)の妥当性

### 1.1 重要な事実(検証済み)

#### kotlinx-rpc 0.10.2 のエラー伝搬挙動

サーバ側で投げた例外は `SerializedException(className, message, stacktrace, cause)` に変換されてクライアントに届く。クライアント側での復元には以下の制約がある(`kotlinx.rpc.krpc.internal.ExceptionUtils.jvm.kt` の `deserializeUnsafe()` をソース読み + 実機検証で確認):

| 条件 | クライアント側で `catch(e: T)` できるか |
|---|---|
| stdlib 例外(`IllegalStateException` 等) | ✅ 元の型で復元 |
| 共有モジュール(`:rpc`/`:shared`)のカスタム例外 + 追加フィールド無し + `(String)` 等の素直なコンストラクタ | ✅ 元の型で復元 |
| 共有モジュールのカスタム例外 + 追加フィールド有り(`val code: Int` 等) | ❌ `DeserializedException` フォールバック。追加フィールド値は失われる |
| クライアント classpath に居ないクラス | ❌ `DeserializedException` フォールバック |

実機検証は本 spec を書く過程で `__ProbeRpcService.kt` + `RpcExceptionProbeTest.kt` で実施(spec 確定後に削除)。10 テストすべて通過。

#### `StatusPages` は RPC エラーに発火しない

`StatusPages` は HTTP/WS-upgrade フェーズで動く。kotlinx-rpc は WS 接続済みのメッセージとして RPC を扱い、各 RPC メソッド内の例外は kRPC 自前の `try/catch` で `SerializedException` 化されるため、`StatusPages` を経由しない。よって現在の `ErrorConfiguration.kt` で定義された `UnauthorizedException → 401` 等のマッピングは **死コード**。

#### `ExposedTransactionPlugin` も RPC に発火しない

`createApplicationPlugin` で `ApplicationCallPipeline.Call` をフックする plugin。WS upgrade 時に 1 回だけ発火する。WS 接続後の RPC メッセージ各個はこの pipeline を通らないため、本 plugin が張る transaction はビジネスロジックで使われていない。実体は `tx()` ヘルパー(`Transaction.kt`)のみ。

#### `validate {}` 内の blocking `transaction` バグ

`AuthConfiguration.kt` の `jwt("user") { validate { ... transaction(database) { ... } } }` は **blocking JDBC** を呼んでいる。`validate` は coroutine 上で動くため、ここで blocking transaction を使うと dispatcher のスレッドをブロックする。`newSuspendedTransaction` を使うのが正しい。

#### WS 接続中の JWT 期限切れ

ktor-auth-jwt および同等の自作 plugin も含め、JWT 検証は **WS upgrade 時に 1 回しか走らない**。WS 接続が開いた後はトークンの `exp` を超えて使用しても検出されない。これは ktor-auth 固有の制約ではなく WS over JWT の構造的制約。

### 1.2 ゴール

- 死コード化している Plugin / dep / file の削除
- Routing から「14 個の手動 `by dependencies`」を削減し、Controller factory パターンへ移行
- ktor-auth を自作 `MindstockAuthPlugin` へ置換。Identity → User 解決の二重 DB query を解消し、WS 中の JWT 期限切れ(L2)を扱えるようにする
- 例外 throw ベースから `RpcResult<T, RpcError>` 戻り値ベースへの移行
- Security Invariants を spec 上で明文化

### 1.3 非ゴール

- gRPC 移行(kotlinx-rpc の gRPC モードはフロントエンド未対応 + スキーマファースト前提のため別物。スコープ外)
- RPC service interface (`:rpc` モジュール) の業務 API 変更
- ドメインモデルの再設計
- Production 化に向けた observability(metrics / tracing)の本格設計

## 2. Plugin 棚卸し方針(論点 1)

`build.gradle.kts` / install 済 plugin / 自作 plugin を以下のように整理する:

| 現状 | 処遇 | 理由 |
|---|---|---|
| `libs.koin.ktor` (gradle) | **削除** | コード中で一切 import されていない死蔵 dep。Ktor 3 native DI を採用済 |
| `ExposedTransactionPlugin` (自作) | **削除** | WS RPC に発火しない。`tx()` ヘルパーのみが実体 |
| `StatusPages` + `ErrorConfiguration.kt` + `RpcExceptions.kt` | **削除** | RPC エラーに発火しない死コード。論点 4 の `RpcResult` 化と整合 |
| `DoubleReceive` | **削除** | `CallLogging` の body 取得用だったが、CallLogging も廃止するため不要 |
| `CallLogging` | **削除** | WS upgrade 時に 1 回しか発火しない。RPC メソッド粒度のログを取れない。論点 1 の構造的問題 |
| `CallId` | **削除** | `CallLogging` 撤去後は MDC 連携先が無く、単独では用途無し |
| `ContentNegotiation` (`jsonIo`) | **残す** | healthcheck エンドポイント追加予定があるため布石として維持 |
| `Authentication` (ktor-auth-jwt) | **削除** | `MindstockAuthPlugin`(論点 3)に置換 |
| `Krpc` | 維持 | 必須 |
| `WsSubprotocolEchoPlugin` (自作) | 維持 | ブラウザ WS 接続に必須(`mindstock.v1` echo / bearer 非 echo) |

### 2.1 ログ取得の代替

CallLogging 撤去後の代替として、**RPC メソッド粒度の構造化ログ**を `tx()` ヘルパーで一括出力する。Controller 各メソッドが `tx(database) { ... }` を必ず使う既存パターンを利用し、`tx()` 内で:

- メソッド入口・出口の log
- 呼び出し主体(`userId`)
- 結果が `Err` の場合の error variant
- 経過 ms

を 1 行 JSON で出力する(現 `CallLoggingModel` 相当の構造を踏襲)。`call-id` は `MindstockAuthPlugin` が session 構築時に UUID を生成して `MindstockSession` に保持し、`tx()` から CoroutineContext 経由で参照する。

## 3. Routing / DI 改善方針(論点 2)

### 3.1 現状の問題

`RoutingConfiguration.kt` で:
- 14 個の `by dependencies` 宣言
- 各 `authenticate("...") { rpc(path) { ... } }` 内で Controller を手 new
- Controller の依存が増えるたびに routing 側も変更が必要

### 3.2 採用案: Controller Factory パターン

各 Controller について `(ApplicationCall) -> Controller` を返す factory interface を `DependenciesConfiguration` で登録する。routing 側は factory を `by dependencies` で 1 つ受け取るだけ。

```kotlin
// :backend:api 配下に追加
fun interface StockControllerFactory {
    fun create(call: ApplicationCall): StockController
}

// DependenciesConfiguration.kt
provide<StockControllerFactory> {
    val s = resolve<StockService>()
    val rs = resolve<StockRegisterService>()
    val pr = resolve<ProductRepository>()
    val hr = resolve<HouseholdRepository>()
    val ur = resolve<UserRepository>()
    val db = resolve<Database>()
    StockControllerFactory { call -> StockController(s, rs, pr, hr, ur, call, db) }
}

// RoutingConfiguration.kt
val stockFactory: StockControllerFactory by dependencies
// ...
rpc("/api/v1/stock") {
    registerService<StockRpcService> { stockFactory.create(applicationCall) }
}
```

### 3.3 採用しなかった案

- **B**(`actor` を RPC 引数化): クライアントから渡せるとなりすまし可能となり JWT 検証の意味が薄れる。却下
- **C**(`RpcSession` 抽象を導入): 抽象 1 層増やしても Controller シグネチャは大して薄くならない。費用対効果薄
- **D**(ktor-server-di の child scope): Ktor 3 DI は単一 application scope のみで child scope 未サポート(2026 年 5 月時点)

### 3.4 影響範囲

- 6 個の `*ControllerFactory` interface を追加
- `RoutingConfiguration.kt` の `by dependencies` を 14 → 6 に縮小
- `DependenciesConfiguration.kt` に 6 個の factory provider 追加
- `Controller` クラス本体は無変更(ただし論点 3 によって `call` → `MindstockSession` に置き換わる)

## 4. Auth 再設計方針(論点 3)

### 4.1 現状の問題

- `validate {}` 内で blocking `transaction()` 呼び出し → coroutine dispatcher ブロック(バグ)
- 同じ `findByAuthIdentity` が validate と各 Controller メソッドで二重実行
- `"user"` / `"user-public"` realm 名が「未登録 OK」の意味を伝えない
- WS 中の JWT 期限切れ未対応
- ktor-auth-jwt の `Credential → Principal?` 抽象が「JWT → AuthIdentity → User 解決」のフローに微妙にフィットしていない
- `Principal` marker interface deprecation 等で abstraction を逆撫でしている箇所がある

### 4.2 採用案: 自作 `MindstockAuthPlugin` への置換

ktor-auth を撤去し、以下を自前で実装する 1 つのアプリ全体 plugin + 1 つの route-scoped plugin に置き換える:

```kotlin
// 1) アプリ全体: トークン検証 + MindstockSession の組み立て
val MindstockAuthPlugin: ApplicationPlugin<MindstockAuthConfig> =
    createApplicationPlugin("MindstockAuth", ::MindstockAuthConfig) {
        val verifier: JWTVerifier =
            JWT.require(Algorithm.RSA256(JwksKeyProvider(jwkProvider)))
                .withIssuer(settings.issuer)
                .withAudience(settings.audience)
                .acceptLeeway(30)
                .build()

        onCall { call ->
            val token = WsBearerTokenExtractor.extractRaw(call) ?: return@onCall
            val decoded = runCatching { verifier.verify(token) }.getOrNull() ?: return@onCall
            val sub = decoded.subject ?: return@onCall
            val identity = AuthIdentity(AuthProvider.ZITADEL, AuthSubject(sub))
            val userId = newSuspendedTransaction(db = database) {
                userRepository.findByAuthIdentity(identity)?.id
            }
            val exp = decoded.expiresAt?.toInstant()
                ?: error("JWT without exp accepted by verifier — should not happen")
            call.attributes.put(
                MindstockSessionKey,
                MindstockSession(identity, userId, exp, callId = Uuid.random()),
            )
        }
    }

// 2) route-scoped: 「登録済み user」を必須とする subtree に install
val RequireRegisteredUserPlugin: RouteScopedPlugin<Unit> =
    createRouteScopedPlugin("RequireRegisteredUser") {
        onCall { call ->
            val session = call.attributes.getOrNull(MindstockSessionKey)
            if (session == null || session.userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
            }
        }
    }
```

### 4.3 Session の shape

```kotlin
data class MindstockSession(
    val identity: AuthIdentity,
    val userId: UserId?,        // null = JWT 有効・User 未登録(register 専用ルートでのみ許容)
    val exp: Instant,
    val callId: Uuid,
)
```

W2 採用(Principal は UserId のみ。User aggregate は Controller で必要時に `findById` する)。理由:

- W1(User 全体を Principal に詰める): WS 接続が長く生きる間 User 状態が固定化される。ban / role 変更が反映されない
- W2: メソッドごとに最新の User を取得。コストは PK lookup 1 回で許容範囲

### 4.4 Routing 形

```kotlin
routing {
    install(MindstockAuthPlugin) { /* settings */ }

    route("/api/v1") {
        route("/user/public") {
            // JWT 有効ならよい(未登録 OK)
            rpc { registerService<UserPublicRpcService> { ... } }
        }
        route("/") {
            install(RequireRegisteredUserPlugin)
            rpc("/user")     { ... }
            rpc("/household"){ ... }
            rpc("/catalog")  { ... }
            rpc("/product")  { ... }
            rpc("/stock")    { ... }
        }
    }
}
```

ポイント:

- realm 名が消える(`"user"` / `"user-public"` という紛らわしい名前から解放)
- routing 層 enforcement を維持(R3 相当のセキュリティ強度): 登録済み user 必須の subtree は plugin install で確定
- 開発者が「Controller でユーザ登録チェックを書き忘れる」事故を構造的に防ぐ(R2 を採用していたら起きうるリスクの回避)

### 4.5 WS 中 JWT 期限切れ対策(L2)

`MindstockSession.exp` を持つので、各 RPC メソッド開始時に `if (Clock.System.now() > session.exp) return RpcResult.Err(RpcError.Unauthorized(...))` をチェックする。`tx()` ヘルパー内で集約する想定(論点 4 で導入する `RpcResult` 戻り値とセットで実装する。throw しない):

```kotlin
suspend fun <T> txWithGuard(
    database: Database,
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError> {
    if (Clock.System.now() > session.exp) {
        return RpcResult.Err(RpcError.Unauthorized(reason = "token expired"))
    }
    return supervisorScope { newSuspendedTransaction(db = database) { block() } }
}
```

### 4.6 Security Invariants(本 spec で義務化)

自作 plugin で auth を扱う以上、以下を **絶対の制約** として spec 上で明文化し、コードレビュー時のチェック項目とする:

1. **JWT 検証の crypto は自前で書かない**。`com.auth0:java-jwt` の `JWT.require(...)` と `com.auth0:jwks-rsa` (`JwkProviderBuilder`) を使う
2. **Algorithm は `RSA256` 固定**(`JwksKeyProvider` 経由)。`Algorithm.none()` 等を受け付ける余地を作らない
3. **JWKS は cache + rate-limit 必須**(現状の `cached(10, 1 HOUR)` + `rateLimited(10, 1 MIN)` を踏襲)
4. **`withIssuer` / `withAudience` を `JWT.require(...)` で必ず指定**。verify 結果に対する手書きの string compare を別途行わない
5. **`acceptLeeway(30)` を明示**
6. **`validate` 相当の DB アクセスは `newSuspendedTransaction` を使う**(blocking JDBC 禁止)
7. **token 値を含む `Sec-WebSocket-Protocol` を response header に echo しない**(現 `WsSubprotocolEchoPlugin` の制約を継続)

### 4.7 WsSubprotocolEcho / Bearer Extractor の維持

ブラウザ WS が `Authorization` ヘッダを設定できない制約は変わらないため、`WsSubprotocolEchoPlugin` と `WsBearerTokenExtractor` は現状ロジックのまま維持。ただし `MindstockAuthPlugin` 側に **`extractRaw(call): String?`** を新設し、現 `extract(call): HttpAuthHeader?` は ktor-auth との接続専用だったため撤去する。

## 5. エラーハンドリング方針(論点 4)

### 5.1 採用案: `RpcResult<T, RpcError>` 戻り値ベース

全 RPC service interface (`:rpc` モジュール) のメソッドシグネチャを以下の形に変更する:

```kotlin
@Serializable
sealed interface RpcResult<out T, out E> {
    @Serializable data class Ok<T>(val value: T) : RpcResult<T, Nothing>
    @Serializable data class Err<E>(val error: E) : RpcResult<Nothing, E>
}

@Serializable
sealed interface RpcError {
    @Serializable data class Unauthorized(val reason: String) : RpcError
    @Serializable data class NotFound(val resource: String, val id: String) : RpcError
    @Serializable data class BadRequest(val field: String, val reason: String) : RpcError
    @Serializable data class Conflict(val reason: String) : RpcError
    @Serializable data class Internal(val reason: String) : RpcError  // 想定外
}
```

- 単一 `RpcError` sealed で API 全体を表現(read/write 分割はしない)
- `:rpc` モジュールに置き、クライアント・サーバの双方が参照
- クライアントは `when (result) { is Ok -> ...; is Err -> when (result.error) { ... } }` で網羅性検証
- `@Serializable` + sealed 構造のため追加フィールド(`resource`, `id` 等)もシリアライズで完全保持

### 5.2 サーバ側の書き方

`throw` を原則使わない:

```kotlin
override suspend fun get(productId: ProductId): RpcResult<Stock, RpcError> =
    txWithGuard(database, session) {
        val product = productRepository.findById(productId)
            ?: return@txWithGuard RpcResult.Err(RpcError.NotFound("product", productId.toString()))
        RpcResult.Ok(stockService.get(product))
    }
```

`throw` を捨てることで以下が自動的に解決:

- `supervisorScope` の load-bearing なハック(`Transaction.kt` の解説コメント)が不要になる
- `StatusPages` が完全に不要になる
- 例外シリアライズの追加フィールド消失問題を回避

### 5.3 stdlib 例外(`require()` 等)の扱い

`require(x > 0)` のような contract violation は仕様上「呼ばないと約束したものを呼んだ」なのでサーバ Internal Error 相当。これらは `txWithGuard` 内で catch して `RpcResult.Err(RpcError.Internal(...))` に変換する(client にスタックトレースは漏らさない):

```kotlin
suspend fun <T> txWithGuard(database: Database, session: MindstockSession,
                            block: suspend () -> RpcResult<T, RpcError>): RpcResult<T, RpcError> {
    if (Clock.System.now() > session.exp) return RpcResult.Err(RpcError.Unauthorized("token expired"))
    return runCatching {
        supervisorScope { newSuspendedTransaction(db = database) { block() } }
    }.getOrElse { e ->
        logger.error(e) { "unhandled exception during RPC" }
        RpcResult.Err(RpcError.Internal(reason = "unexpected server error"))
    }
}
```

### 5.4 既存 `UnauthorizedException` / `NotFoundException` の削除

`RpcExceptions.kt` / `ErrorConfiguration.kt` を削除し、それらを throw していた Controller 各メソッドを `RpcResult.Err(...)` を返すように書き換える。

## 6. 実装順序

依存関係のあるリファクタなので、以下の順序で進める。各 step ごとに既存テストが green であることを確認する。

1. **死コード削除(論点 1 の半分)**
   - `Koin` gradle dep 削除
   - `ExposedTransactionPlugin` 削除
   - `DoubleReceive` / `CallLogging` / `CallId` 削除(`LoggingConfiguration.kt` 解体)
   - **`StatusPages` / `ErrorConfiguration.kt` はこの step では削除しない**(step 2 で Controller の throw を解消した後に消す)
2. **`RpcResult` / `RpcError` 導入(論点 4)**
   - `:rpc` モジュールに `RpcResult` / `RpcError` 追加
   - 各 RPC service interface のシグネチャを `RpcResult<T, RpcError>` に変更
   - Controller を `throw` → `RpcResult.Err(...)` 返却に書き換え
   - `tx()` を「`runCatching` で stdlib 例外を `Err(Internal)` 化する」拡張版に置換(session 統合は step 4)
   - `RpcExceptions.kt` / `ErrorConfiguration.kt` / `StatusPages` 削除
3. **Routing / DI Factory 化(論点 2)**
   - 6 個の `*ControllerFactory` interface 追加
   - `DependenciesConfiguration.kt` に factory provider 追加
   - `RoutingConfiguration.kt` の手 new を factory 呼び出しに置換
   - 本 step 時点では Controller の `call: ApplicationCall` 引数は維持(置換は step 4 で行う)
4. **Auth 再設計(論点 3)**
   - `MindstockAuthPlugin` + `RequireRegisteredUserPlugin` 実装
   - `MindstockSession` 導入(`callId` 含む)
   - Controller factory の signature を `(ApplicationCall) -> Controller` → `(MindstockSession) -> Controller` に変更
   - Controller の `call: ApplicationCall` 引数 → `session: MindstockSession` に置換
   - `tx()` に `session.exp` チェックを追加(本格的に `txWithGuard(database, session) {...}` の形へ)
   - `AuthConfiguration.kt` / `JwtAuthConfiguration.kt` / `ActorResolver.kt` 削除
5. **代替ロギング(論点 1 の残り)**
   - `txWithGuard` 内で構造化 JSON ログを 1 行出力(`callId` / `userId` / 経過 ms / `Ok`/`Err` variant)

## 7. テスト戦略

- 既存 E2E (`backend/api/src/test/kotlin/.../e2e/`) は `shouldThrowAny` から **`when` ベースの `RpcResult.Err` アサーション** に書き換える。これで初めて「想定したエラー variant が返っている」という検証が型レベルで可能になる
- `MindstockAuthPlugin` 単体テスト:
  - 有効トークン → session 設定される
  - 無効署名 / 期限切れ / iss 不一致 / aud 不一致 → session 設定されない
  - WS bearer subprotocol 経由でも extract できる
- `RequireRegisteredUserPlugin` 単体テスト:
  - session 無し → 401
  - session 有り `userId == null` → 401
  - session 有り `userId != null` → proceed
- `txWithGuard` 単体テスト:
  - `session.exp < now` → `Err(Unauthorized("token expired"))`
  - block 内で例外 → `Err(Internal)`、ログ出力
  - 正常系 → block の結果がそのまま返る

## 8. 既知の制約 / Future work

### 8.1 ban / revocation の即時反映

JWT 期限内のユーザを即座に拒否する仕組みは本 spec には含めない。`MindstockSession.userId` 経由で User 状態が削除されていれば次回メソッドで `Err(Unauthorized)` 相当を返せるが、「ban フラグが立った瞬間に既存 WS を切断する」要件は server-push の仕組みが必要。Future work。

### 8.2 JWKS rotation 中の挙動

`JwkProviderBuilder.cached(10, 1 HOUR)` のため、JWKS rotation 直後に古い kid の token を 1 時間以内に再受領する余地がある。短命 token + rotation 周期短縮で運用回避する想定。

### 8.3 metrics / tracing

CallLogging を撤去するため、機能としての「全 HTTP 呼び出しメトリクス」は消える。Future work で micrometer 等を導入する想定。

### 8.4 gRPC 移行

非ゴール扱い(理由: kotlinx-rpc gRPC モードはスキーマファースト + ブラウザ未対応で本プロジェクトと噛み合わない)。

## 9. 削除予定の検証用ファイル

本 spec のための検証で以下のファイルを作成した。spec 確定後は **削除** する(コミットしない):

- `rpc/src/commonMain/kotlin/net/brightroom/mindstock/rpc/__ProbeRpcService.kt`
- `backend/api/src/test/kotlin/net/brightroom/mindstock/probe/RpcExceptionProbeTest.kt`
