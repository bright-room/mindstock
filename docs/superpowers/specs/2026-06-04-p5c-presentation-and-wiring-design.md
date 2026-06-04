# P5c presentation Controller・認可・起動配線・失効ガード 設計

家庭の在庫管理 SaaS「mindstock」フルリプレイス(`2026-05-31-mindstock-full-replace-design`)のフェーズ P5c。P5a(application service)・P5b(backend 認証 plugin 群)の成果物を土台に、**presentation 層の Controller を実装し、認可・失効ガード・起動配線を完成させて `:backend:api` を起動可能にする**。

- 出典(上位設計): `docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/`(特に `02-iconix.md`)
- 直接の前提: P5a `docs/superpowers/specs/2026-06-03-p5a-backend-application-service-design.md` / P5b `docs/superpowers/specs/2026-06-03-p5b-backend-auth-design.md`
- P5b 申し送り: `docs/superpowers/plans/2026-06-04-p5b-backend-auth.md` 末尾「P5c への申し送り」

## ゴール

P5c 完了時、`:backend:api` は実 PostgreSQL + Zitadel JWKS に対して **起動し、認証・認可付きで RPC を提供できる**。具体的には:

1. 全 9 RPC service interface に対応する Controller が `presentation/rpc/<ctx>/` に存在する
2. 横方向認可(世帯メンバーシップ)が application/domain 層で強制され、IDOR(任意世帯の読み書き)が塞がれている
3. WebSocket 長時間接続中の JWT 失効(期限切れ)が RPC メッセージ単位で弾かれる
4. ドメイン例外が `RpcError` に翻訳されてクライアントに返る
5. DB 接続・マイグレーション・DI・ルーティングが配線され、アプリが起動する

## 背景と問題

### 現状(P5b 完了時点)

- `:backend:api` は `Main.kt`(`EngineMain` のみ)/ `RoutingConfiguration`(`/health` のみ)で、**起動できない**(DB・DI・routing が空)。
- P5b で `MindstockAuthPlugin` / `RequireRegisteredUserPlugin` / `WsSubprotocolEchoPlugin` / `MindstockSession` / `WsBearerTokenExtractor` は実装済みだが **未配線**。
- P5a で application service / scenario / repository interface / DataSource(`Database` コンストラクタ注入・transaction 自前境界)は実装済み。**Controller は未実装**。

### 発見した認可ギャップ(IDOR / BOLA)

P5a の application service は、一部の経路で **世帯メンバーシップを検証していない**:

| 経路 | 例 | 現状 |
|---|---|---|
| 在庫読み取り | `ProductService.list(householdId)` / `StockService.activity(householdId)` / `history(productId)` | ❌ `actor` 無し・メンバー検証無し |
| 在庫/商品書き込み | `StockRegisterService.replenish(productId,…,actor)` / `ProductRegisterService.changeUnit(productId,…)` | ❌ actor は記録するが世帯メンバー検証無し |
| 世帯書き込み | `HouseholdRegisterService.rename(…,actor)` | ✅ domain `requireCapability(actor)` |
| 世帯一覧 | `HouseholdService.list(actor)` | ✅ `listByResident(actor)` でスコープ |
| 招待 | `CreateInvitationScenario` / `RevokeInvitationScenario` | ✅ `requireCanManage(actor)` |
| カタログ | `CatalogService.search/lookupByJan` | ✅ グローバルマスタ(世帯非依存) |
| 住人 | `ResidentService.me(actor)` / `ResidentRegisterService` | ✅ 自分スコープ |

→ ログイン済み住人なら他世帯の `householdId`/`productId` を推測して読み書きできてしまう。**P5c で application/domain 層に閉じて塞ぐ**(決定: 認可は domain に集約。Controller は薄く保つ)。

`Product`/`Stock` 集約は `householdId` を保持しない(意図的に世帯非依存)。よって `productId`/`movementId` から世帯を解決する **専用 repository クエリ**を新設する。

## 設計

### 全体方針

- **層責務厳守**(`.claude/rules/software-architecture.md`): 認可ロジックは domain(`Household.requireMember`)、orchestration は application(service が household を fetch して検証)、Controller は presentation の腐敗防止層に徹し薄く保つ。
- **nullable 戻り値禁止**(`error-handling.md`): 不在は例外。`session` の状態は sealed `MindstockSession`。
- **transaction は DataSource 自前**(`rpc-and-transactions.md`): `guarded{}` は DB transaction を張らない。

### A. 認可ハードニング(domain + application + infrastructure)

#### A-1. domain: `Household.requireMember`

`Household` に追加:

```kotlin
/** 世帯メンバーであることの認可。非メンバーなら MembershipRequiredException。 */
fun requireMember(by: ResidentId) {
    if (!members.contains(by)) {
        throw MembershipRequiredException("not a member of household ${id}: $by")
    }
}
```

新例外 `domain/exception/MembershipRequiredException.kt`(`RuntimeException` 直系。既存の `OwnerRequiredException` と同形)。

#### A-2. repository: 世帯解決クエリ

- `ProductRepository.householdOf(productId: ProductId): HouseholdId` を追加(不在は `ResourceNotFoundException`)。`ProductsTable.householdId` を引く。
- `movementId` 解決は既存の `StockRepository.findByMovement(movementId): Stock` → `stock.product.id` → `ProductRepository.householdOf(...)` で合成(新メソッド不要)。

infrastructure 実装: `ProductDataSource.householdOf` を `transaction(database){}` 境界で追加。

#### A-3. service: `actor` 追加 + メンバー検証

各 service に `householdRepository: HouseholdRepository`(+ 必要に応じ `productRepository: ProductRepository`)を注入し、メソッドに `actor: ResidentId` を追加して先頭で `householdRepository.findById(<resolved>).requireMember(actor)` を呼ぶ。

| service | メソッド | 世帯解決 |
|---|---|---|
| `ProductService` | `list` / `listArchived` / `shoppingList` | 引数 `householdId` 直 |
| `ProductRegisterService` | `adopt` / `addCustom` | 引数 `householdId` 直 |
| `ProductRegisterService` | `changeUnit` / `changeMinimum` / `changeImage` / `archive` / `unarchive` / `setWanted` | `productRepository.householdOf(productId)` |
| `StockService` | `activity` | 引数 `householdId` 直 |
| `StockService` | `history` | `productRepository.householdOf(productId)` |
| `StockRegisterService` | `replenish` / `consume` | `productRepository.householdOf(productId)` |
| `StockRegisterService` | `correct` | `findByMovement(target).product.id` → `householdOf` |

- `AdoptProductScenario.run(...)` に `actor: ResidentId` を追加(内部の `productRegisterService.adopt` へ伝播)。
- 注入が増える service のコンストラクタ変更に合わせ DI 配線・既存 P5a テストを更新する。
- メンバー検証は「household を 1 回 fetch する追加クエリ」を伴う。読み取り経路でも許容する(正確性優先)。

> 設計判断: 認可を「Controller 層で household を引いて `members.contains`」ではなく **application/domain 層**に置く。理由: domain にロジックを集約しリッチドメイン原則と整合させ、Controller 以外の呼び出し元(将来のバッチ等)からも守るため。コストは P5a service シグネチャの変更。

### B. presentation Controller(9 個)

`backend/api/src/main/kotlin/net/brightroom/mindstock/presentation/rpc/<ctx>/<Ctx>Controller.kt`。各 RPC service interface を実装。

#### 共通形

```kotlin
class CatalogController(
    private val catalogService: CatalogService,
    private val session: MindstockSession,
) : CatalogRpcService {
    override suspend fun search(name: CatalogItemName, limit: Int): RpcResult<CatalogItems, RpcError> =
        guarded(session) { RpcResult.Ok(catalogService.search(name, limit)) }
}
```

- コンストラクタは **service/scenario + `session: MindstockSession` のみ**。**Repository は注入しない**(認可は service に集約済)。
- 各メソッドは `guarded(session) { ... }` で包み、内部で service/scenario を呼んで `RpcResult.Ok(...)` を返す。例外翻訳・失効判定は `guarded` が担う(Controller に try/catch を書かない)。
- `actor` は `session.requireResidentId()`(下記ヘルパー)で取り出す。

#### session ヘルパー

`MindstockSession` に拡張を追加(`configuration/auth/` 内):

```kotlin
/** Registered のときだけ residentId を返す。Unregistered で呼ぶのはバグ(RequireRegistered 配下で保証)。 */
fun MindstockSession.requireResidentId(): ResidentId =
    (this as? MindstockSession.Registered)?.residentId
        ?: error("registered session required")
```

`error(...)`(`IllegalStateException`)は `guarded` で `Internal` に落ちる。`RequireRegisteredUserPlugin` 配下では到達しない不変条件。

#### Controller 一覧と特記

| Controller | 実装 interface | 認可ルート | 特記 |
|---|---|---|---|
| `ResidentRegisterController` | `ResidentRegisterRpcService` | **public**(RequireRegistered 非適用) | `when(session)`: `registerDisplayName` は `Unregistered` 必須(`Registered` なら `Conflict("already registered")`)、`rename` は `Registered` 必須(`Unregistered` なら `Unauthorized`) |
| `ResidentController` | `ResidentRpcService` | Registered | `me()` → `residentService.me(session.requireResidentId())` |
| `CatalogController` | `CatalogRpcService` | Registered | 世帯非依存。actor 不要 |
| `HouseholdController` | `HouseholdRpcService` | Registered | `list()` → `householdService.list(actor)`、`previewInvite(code)` → `invitationService.findByCode` + `householdService.findById` から `InvitationPreview` を組み立て |
| `HouseholdRegisterController` | `HouseholdRegisterRpcService` | Registered | 8 メソッド。`join`/`createInvite`/`revokeInvite` は Scenario 経由。actor を全メソッドへ |
| `ProductController` | `ProductRpcService` | Registered | `list/listArchived/shoppingList(householdId)` に actor を渡す |
| `ProductRegisterController` | `ProductRegisterRpcService` | Registered | `adopt` は `AdoptProductScenario.run(householdId, catalogItemId, unit, minimumStock, actor)` |
| `StockController` | `StockRpcService` | Registered | `activity` は `stockService.activity(householdId, actor): Stocks` を **`ActivityFeed` に flatten**(各 Stock の movement を ActivityEntry 化) |
| `StockRegisterController` | `StockRegisterRpcService` | Registered | replenish/consume/correct に actor |

戻り値の型ズレ(`StockService.activity: Stocks` → `ActivityFeed`)は Controller でマッピングする(腐敗防止層)。それ以外は VO/集約/ファーストクラスコレクションを直接返す。

> 旧コミット(`11c9b31`)の `<Ctx>ControllerFactory` interface は **採用しない**。service を `RoutingConfiguration` で `by dependencies` 解決し、`registerService<T> { Controller(service, sessionOf(applicationCall)) }` のインライン構築にする(per-WS-connection に session を束ねる。`rpc-and-transactions.md` の例と一致)。

### C. 失効ガード `guarded{}`

`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt`:

```kotlin
suspend fun <T : Any> guarded(
    session: MindstockSession,
    block: suspend () -> RpcResult<T, RpcError>,
): RpcResult<T, RpcError>
```

処理:

1. `Clock.System.now() > session.exp` なら `RpcResult.Err(RpcError.Unauthorized("token expired"))`(`kotlin.time.Clock` を使用。`session.exp` は `kotlin.time.Instant`)
2. `supervisorScope { block() }` で実行(kRPC サーバスコープへの例外漏れ防止)
3. 例外を `RpcError` に翻訳:

| 例外 | RpcError |
|---|---|
| `kotlinx.coroutines.CancellationException` | 再 throw(キャンセル伝播) |
| `IllegalArgumentException` | `BadRequest(field = "request", reason = message)` |
| `ResourceNotFoundException` | `NotFound(message)` |
| `OwnerRequiredException` / `MembershipRequiredException` | `Unauthorized(reason)` |
| `LastOwnerException` / `DuplicateJanException` / `CannotArchiveWithStockException` / `InsufficientStockException` / `InvitationInvalidException` | `Conflict(reason)` |
| その他 `Throwable` | `Internal("unexpected server error")`(`session.callId` を含めて `logger.error`) |

**DB transaction は張らない**(P4 で `tx()`/`ExposedTransactionPlugin` 廃止済。transaction は DataSource 自前)。旧 `tx()` とは別概念のため名前を `guarded` にする。`when` 句は具体例外を先に、`IllegalArgumentException` は `is` で、最後に `else -> Internal`。

> このガードが守るのは **JWT の有効期限切れ**のみ。IdP(Zitadel)側で exp 前に明示的に revoke されたトークンは検知しない(リアルタイム失効リストは将来課題)。

### D. 起動配線

旧コミット `11c9b31` の構成を新アーキ(DataSource が `Database` 注入・transaction 自前)に合わせて移植する。

#### D-1. `ExposedConfiguration`(`configuration/external/exposed/`)

- `ExposedDataSourceProperties`(`@Serializable`、`@Property("external.datasource.database")` で注入)。
- Hikari `HikariDataSource` → Exposed `Database.connect(datasource, DatabaseConfig{ useNestedTransactions = true })` を生成し DI 登録。`ApplicationStopped` で `dataSource.close()`。

#### D-2. `MigrationConfiguration`(`configuration/migration/`)

- 起動時に Flyway で `classpath:db/migration`(`backend/core` の `V1__init.sql`)を `migrate()`。専用 Hikari(`maximumPoolSize=2`)を `use{}` で開閉。

#### D-3. `DependenciesConfiguration`(`configuration/di/`)

```kotlin
dependencies {
    // Repository(DataSource は Database 注入)
    provide<ResidentRepository> { ResidentDataSource(resolve()) }
    provide<ResidentRegisterRepository> { ResidentRegisterDataSource(resolve()) }
    // … catalog / household / invitation / product / stock の Repository / RegisterRepository
    provide<ExternalProductGateway> { UnconfiguredProductGateway() }  // 現状の既定 gateway

    // Service
    provide<CatalogService> { CatalogService(resolve(), resolve(), resolve()) }
    // … 各 Service(認可で増えた householdRepository / productRepository も resolve)

    // Scenario
    provide<AdoptProductScenario> { AdoptProductScenario(resolve(), resolve()) }
    provide<JoinHouseholdScenario> { JoinHouseholdScenario(resolve(), resolve(), resolve()) }
    provide<CreateInvitationScenario> { CreateInvitationScenario(resolve(), resolve()) }
    provide<RevokeInvitationScenario> { RevokeInvitationScenario(resolve(), resolve(), resolve()) }
}
```

Controller Factory は登録しない(B 節の通り routing でインライン構築)。

#### D-4. `RoutingConfiguration`(差し替え)

```kotlin
fun Application.routingConfigure() {
    install(ContentNegotiation) { jsonIo(CustomJson) }
    install(Krpc) { serialization { json(KrpcJson) } }
    install(WsSubprotocolEchoPlugin)

    val cfg = environment.config.config("external.auth")
    val residentRepository: ResidentRepository by dependencies
    install(MindstockAuthPlugin) {
        jwkProvider = JwkProviderBuilder(URI(cfg.property("jwks-url").getString()).toURL())
            .cached(10, 1, TimeUnit.HOURS).rateLimited(10, 1, TimeUnit.MINUTES).build()
        issuer = cfg.property("issuer").getString()
        audience = cfg.property("audience").getString()
        this.residentRepository = residentRepository
    }

    // service を先取り解決(factory は非 suspend のため)
    val residentService: ResidentService by dependencies
    val residentRegisterService: ResidentRegisterService by dependencies
    // … 他 service / scenario

    routing {
        route("/api/v1") {
            // JWT 有効なら未登録 OK(初回登録経路)
            rpc("/resident/register") {
                registerService<ResidentRegisterRpcService> {
                    ResidentRegisterController(residentRegisterService, sessionOf(applicationCall))
                }
            }
            // 登録済み Resident 必須
            route("") {
                install(RequireRegisteredUserPlugin)
                rpc("/resident") { registerService<ResidentRpcService> { ResidentController(residentService, sessionOf(applicationCall)) } }
                rpc("/catalog") { /* … */ }
                rpc("/household") { /* … */ }
                rpc("/household/register") { /* HouseholdRegisterRpcService */ }
                rpc("/product") { /* … */ }
                rpc("/product/register") { /* ProductRegisterRpcService */ }
                rpc("/stock") { /* … */ }
                rpc("/stock/register") { /* StockRegisterRpcService */ }
            }
        }
    }
}
```

- `sessionOf(call)` ヘルパー(`configuration/auth/`): `call.attributes[MindstockSessionKey]`。
- パス: register 系は `/<ctx>/register` の独立 rpc route で分離(query 系と別 service なので path も分ける)。

#### D-5. `application.yaml`

```yaml
ktor:
  environment: "$KTOR_ENV:LOCAL"
  deployment:
    port: "$PORT:8080"
  application:
    modules:
      - "...migration.MigrationConfigurationKt.migrationConfigure"
      - "...external.exposed.ExposedConfigurationKt.exposedConfigure"
      - "...di.DependenciesConfigurationKt.dependenciesConfigure"
      - "...routing.RoutingConfigurationKt.routingConfigure"

external:
  auth:
    issuer: "$AUTH_ISSUER"
    audience: "$AUTH_AUDIENCE"
    jwks-url: "$AUTH_JWKS_URL"
  datasource:
    database:
      driver-class-name: "org.postgresql.Driver"
      jdbc-url: "$DB_JDBC_URL:jdbc:postgresql://localhost:5432/mindstock"
      username: "$DB_USERNAME:mindstock"
      password: "$DB_PASSWORD:mindstock"
```

module 実行順: migration → exposed → dependencies → routing(routing は `Database`/repository を resolve するため後)。

## テスト戦略

`testApplication` は WebSocket Upgrade 不可(`ktor-testapplication-auth-gotchas`)・kotlinx-rpc は WS 上で動くため、**RPC over WS の e2e テストは書かない**。各層を直接単体テストする。

| 対象 | 場所 | スタイル | 観点 |
|---|---|---|---|
| `Household.requireMember` | `domain` commonTest | `kotlin.test.@Test` + Kotest assertions(`frontend-kmp-test-style`) | メンバー→通過 / 非メンバー→`MembershipRequiredException` |
| 各 service の認可 | `backend/core` test | Kotest FunSpec + mockk | 非メンバーで例外、`householdOf` 経由解決の結線、actor 伝播 |
| `guarded{}` | `backend/api` test | Kotest FunSpec | 期限切れ→`Unauthorized` / 各例外→対応する `RpcError` / `CancellationException` 再throw / 正常→Ok |
| 各 Controller | `backend/api` test | Kotest FunSpec + mockk | service が session 由来 actor で呼ばれる / `RpcResult` / `ResidentRegister` の状態分岐 / `activity` の `ActivityFeed` 変換 |

配線(D)は `./gradlew :backend:api:build`(コンパイル + DI グラフのコンパイル時健全性)で担保。**実 PostgreSQL + JWKS に対する実起動はローカル手動確認**とする(自動 e2e は本フェーズ対象外)。

## 完了の定義

- `./gradlew :domain:build :backend:core:build :backend:api:build`(integrationTest 除く)が green
- 上表の単体テストが green
- `./gradlew spotlessApply` 差分なし
- 認可ギャップ(IDOR)が application/domain で塞がれている(service 認可テストで担保)
- `application.yaml` に必要な環境変数が定義され、modules が登録されている

## 非ゴール(本フェーズ対象外)

- IdP 即時失効(revocation list)
- RPC over WS の自動 e2e テスト
- frontend の接続(P6)
- 消費予測 / 通知 / オフライン(上位設計で実装後回し)

## 参照

- 上位: `docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/`
- P5a: `docs/superpowers/specs/2026-06-03-p5a-backend-application-service-design.md`
- P5b: `docs/superpowers/specs/2026-06-03-p5b-backend-auth-design.md` / plan `docs/superpowers/plans/2026-06-04-p5b-backend-auth.md`
- rules: `.claude/rules/software-architecture.md` / `error-handling.md` / `rpc-and-transactions.md`
- 旧実装(移植元): commit `11c9b31`
