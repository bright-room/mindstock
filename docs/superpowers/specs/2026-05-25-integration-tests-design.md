# Plan 7: 結合テスト(RPC 越し e2e)— 設計

作成日: 2026-05-25
対象: kotlinx-rpc クライアント → testApplication → 実 Repository → 実 PostgreSQL の通し結合テスト

## 1. 背景と目的

Plan 5 で Repository 単位、Plan 6 で Service Impl 単位の unit/integration テストを整えたが、
**testApplication 越しの full-stack 結合テスト** は未整備。Plan 6 で導入した以下を実機検証する必要がある:

- `tx(database) { ... }` による per-RPC-message transaction 境界
- `Authentication` Plugin(Bearer = UserId UUID)+ `ActorResolver` の連携
- `StatusPages` による例外 → HTTP ステータスマッピング
- `Krpc` plugin + 6 service の routing + 認証境界(`authenticate("user")`)
- 集約 `@Serializable` 化による polymorphic 含むラウンドトリップ

これらが「クライアントから RPC method を呼ぶと正しく動く」ことを Plan 7 で確認する。

**スコープに含む**:
- testApplication + Testcontainers(PostgreSQL 18) を組み合わせる scaffold
- 6 RPC service それぞれに対する e2e テスト群(全 method × happy path + key edge case)
- 認証失敗・NotFound・transaction rollback・domain invariant 例外・polymorphic 戻り値の検証
- 共通の test fixture ヘルパー(`Fixtures.kt`)とテストエントリ(`e2eTest { }`)

**スコープ外**:
- 業務認可(actor が世帯メンバーか等)実装 — 別 Plan(Plan 6 の `TODO(authz):` を残す)
- 本格認証(JWT) — 別 Plan
- Plan 6 で残った Important / Minor follow-up(`setMinimumStock` nullable, spec 乖離, `asList` redundant copy 等)— 別 PR

## 2. 全体構成

```text
backend/application/api/src/test/kotlin/net/brightroom/mindstock/e2e/
  ├─ E2eTestSupport.kt        // テストエントリ scaffold
  ├─ Fixtures.kt              // シードヘルパー
  ├─ user/
  │   ├─ UserPublicRpcServiceE2eTest.kt
  │   └─ UserRpcServiceE2eTest.kt
  ├─ household/HouseholdRpcServiceE2eTest.kt
  ├─ catalog/CatalogRpcServiceE2eTest.kt
  ├─ product/ProductRpcServiceE2eTest.kt
  └─ stock/StockRpcServiceE2eTest.kt
```

`backend/application/api/src/testFixtures/` に置く案もあるが、e2e 用ヘルパーは現状この 1 モジュールから
しか使わない予定なので test source 配下に置く。将来複数モジュールから使う場面が出たら testFixtures に移す。

## 3. テストエントリ scaffold(`E2eTestSupport.kt`)

```kotlin
fun e2eTest(block: suspend E2eContext.() -> Unit) {
    TestContainersPostgres.withFreshSchema { jdbcUrl, _ ->
        val dataSource = testHikariDataSource(jdbcUrl, username, password)
        try {
            MigrationRunner.migrate(dataSource)
            val database = Database.connect(dataSource)
            testApplication {
                environment {
                    config = MapApplicationConfig(
                        "external.datasource.database.jdbc-url" to jdbcUrl,
                        "external.datasource.database.username" to username,
                        "external.datasource.database.password" to password,
                        // ... 他の必須項目
                    ).mergeWith(ApplicationConfig("application.yaml"))
                }
                application { module() }  // 本番と同じ全 Plugin
                val httpClient = createClient {
                    install(WebSockets)
                    install(Krpc) { serialization { json(CustomJson) } }
                }
                val ctx = E2eContext(httpClient, database, dataSource)
                ctx.block()
            }
        } finally {
            dataSource.close()
        }
    }
}

class E2eContext(
    val httpClient: HttpClient,
    val database: Database,
    val dataSource: DataSource,
) {
    /** Bearer = user.id を仕込んだ krpc service handle を返す。 */
    suspend inline fun <reified T : Any> rpcClient(asUser: User, path: String): T =
        httpClient.rpc {
            url("/api/v1/$path")
            header("Authorization", "Bearer ${asUser.id.value}")
        }.withService<T>()

    /** 認証なしの krpc service handle (UserPublicRpcService 用)。 */
    suspend inline fun <reified T : Any> publicRpcClient(path: String): T =
        httpClient.rpc { url("/api/v1/$path") }.withService<T>()
}
```

実装着手時に Ktor 3.5 / kotlinx-rpc 0.10.2 の `testApplication` API と krpc client builder の正確な
シグネチャを確認(Krpc plugin の client 側名や `withService<T>()` のジェネリック呼び出しなど)。

**スキーマライフサイクル**: 1 つの `e2eTest { ... }` ブロック = 1 fresh schema。
class 内テスト間で schema は共有しない方針(`withFreshSchema` のブロック単位で create/drop)。
テスト間独立性が完全に保たれ、append-only 制約があっても干渉ゼロ。

> **§1 で議論した「class 単位 fresh schema」から変更**: schema 共有による速度メリットより、
> testApplication のセットアップが test 単位で発生する以上、schema も test 単位で揃える方が一貫。
> 速度が問題になれば後で見直す。

## 4. シードヘルパー(`Fixtures.kt`)

`E2eContext` の receiver 関数として:

```kotlin
context(ctx: E2eContext)
fun seedUser(
    displayName: String = "Test User ${randomShort()}",
    provider: AuthProvider = AuthProvider.ZITADEL,
    subject: String = randomShort(),
): User { /* UserRegisterRepositoryImpl を直接呼ぶ or insert SQL */ }

context(ctx: E2eContext)
fun seedHousehold(owner: User, ...): Household

context(ctx: E2eContext)
fun seedCatalogItem(name: String = "Item ${randomShort()}", ...): CatalogItem

context(ctx: E2eContext)
fun seedProduct(household: Household, catalogItem: CatalogItem, ...): Product

context(ctx: E2eContext)
fun seedReplenishment(product: Product, by: User, qty: Int, ...): Replenishment
```

実装は Repository を呼ぶ(transaction 境界は seed 関数内で `transaction(ctx.database) { ... }`)。
ID は UUID v7 を `domain` の VO factory 経由で生成し、各テストが固有 UUID を持つ。

## 5. 典型テスト 1 本のフロー

```kotlin
class StockRpcServiceE2eTest : FunSpec({
    test("replenish persists a new Replenishment movement") {
        e2eTest {
            val owner = seedUser(displayName = "Alice")
            val household = seedHousehold(owner)
            val catalog = seedCatalogItem(name = "Milk", unit = "L")
            val product = seedProduct(household, catalog)

            val stockRpc = rpcClient<StockRpcService>(asUser = owner, path = "stock")
            val replenishment = stockRpc.replenish(
                productId = product.id,
                qty = Quantity(3),
                occurredAt = OccurredAt(Instant.parse("2026-05-25T12:00:00Z")),
                note = Note(""),
            )

            replenishment.quantity shouldBe Quantity(3)
            replenishment.actor.id shouldBe owner.id

            val stock = stockRpc.get(product.id)
            stock.currentQuantity() shouldBe 3
        }
    }
})
```

副作用検証は **同じ RPC client で read-side method を呼ぶ** ことを原則とし、
read-side method がないケース(`SetMinimumStock` の結果を確認したい等)のみ `database` 経由で SELECT する。

## 6. カバーする edge case の体系

| カテゴリ | 検証する不変条件 | 主な置き場所 |
|---|---|---|
| 認証失敗 | header なし → 401 / 不正 token → 401 | User, Household 各 1 |
| NotFound | 未知 ID → 404 | Household(invite/revoke), Product(setMin/archive), Stock(get 等)|
| Transaction rollback | Handler 内例外 → DB 状態が手前に戻る(Plan 6 fix 検証)| Stock or Product に 1 本 |
| Domain invariant 例外 | 不正値 → 400 (`IllegalArgumentException`) | Catalog register(空 name), Stock consume(qty 超過)|
| Polymorphic 戻り値 | `Replenishment` + `Consumption` 混在の `movementHistory` 取得 | Stock に 1 本 |

全 6 service の見積もり(合計 ~34 テスト):
- `UserPublicRpcServiceE2eTest`: 2(happy + invariant 1)
- `UserRpcServiceE2eTest`: 3(happy + 認証失敗 2)
- `HouseholdRpcServiceE2eTest`: 6(happy 4 + NotFound 2)
- `CatalogRpcServiceE2eTest`: 6(happy 4 + NotFound 1 + invariant 1)
- `ProductRpcServiceE2eTest`: 8(happy 5 + NotFound 2 + rollback 1)
- `StockRpcServiceE2eTest`: 9(happy 5 + NotFound 2 + invariant 1 + polymorphic 1)

## 7. ビルド構成

`backend/application/api/build.gradle.kts` の test deps に追加:

- `testImplementation(libs.kotlinx.rpc.client)`
- `testImplementation(libs.kotlinx.rpc.client.ktor)`
- `testImplementation(libs.kotlinx.rpc.serialization.json)`
- `testImplementation(ktorLib.client.contentNegotiation)`
- `testImplementation(ktorLib.client.cio)` — WebSocket 対応エンジン
- `testImplementation(ktorLib.client.websockets)`

既存(変更不要): `testImplementation(ktorLib.server.testHost)`, Testcontainers、mockk(不使用)

正確な artifact 名は `libs.versions.toml` で定義済みのエイリアスを使う。
未定義のものがあれば追加。

## 8. 実行コストと CI

- Testcontainers Postgres: object lazy 初期化 → **1 JVM = 1 コンテナ**(Plan 5 と同じパターン、現 CI で実証済み)
- testApplication 起動: ~300-500ms per test、~34 テスト ≈ 15-20 秒
- 初回コンテナ起動: ~3-5 秒(再利用)
- **合計**: 1 CI 実行あたり ~25 秒の純増

GitHub Actions:
- public リポジトリは standard runner で分単位無制限
- `ubuntu-latest` に Docker pre-installed、追加セットアップ不要
- 現 `.github/workflows/ci.yml` の `./gradlew check` がそのまま新テストも実行

## 9. 並列性・testcontainers reuse

- Plan 7 では順次実行(gradle 既定 `maxParallelForks=1`)で十分。並列化は将来必要になれば対応
- `testcontainers.reuse.enable=true`(ローカル開発の繰り返し起動を速くする)は **Plan 7 には含めない**。
  影響は開発者ローカルのみで、CI 効果ゼロ。導入したいタイミングで別途設定追加

## 10. 想定していない / 別 Plan

- 業務認可チェック(`TODO(authz):` で記録済み)→ 別 Plan で実装し、結合テストも追加
- frontend からの実呼び出しテスト → frontend 着手 Plan
- 認証 JWT 化 → 別 Plan
- 本 Plan で見つかった bug の修正は Plan 7 のスコープ内で対応する(typo / wiring の小ミス等)
