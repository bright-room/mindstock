# 統合テスト接続プール上限化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** テスト用 HikariCP プールを全部キャップして `--max-workers=1` 無しで統合テストが安定して通るようにする。

**Architecture:** 真因は「テストごとに過大なプール(各10接続)を開いては捨てる」サイクルが Postgres `max_connections=100` を突破すること。テスト側 3 箇所(`TestDataSource.create()`=1、`testHikariDataSource()`=2、E2E アプリプール=2)のプールを絞る。本番 `application.yaml` は無変更。検証は経験的(default workers で実行し `too many clients` が出ず、全テストが pass すること)。

**実装時の重要な修正(実機検証で発覚):** `testHikariDataSource()` を pool=1 にすると Flyway が self-deadlock する(セッションロック用 1 接続を保持したままマイグレーション用にもう 1 接続を要求 → `Connection is not available, request timed out after 30000ms`)。**Flyway は最低 2 接続必要**なので pool=2 とする。pool=1 だと全 e2e/repository 統合テストが 30s タイムアウトで fail(かつ 1 回の実行が ~35 分に膨張)。`create()` は raw 単文 DDL のみなので pool=1 で可。

**Tech Stack:** Kotlin / Gradle / HikariCP / Exposed / Kotest / Ktor testApplication / PostgreSQL

**設計 spec:** `docs/superpowers/specs/2026-05-30-integration-test-pool-capping-design.md`

---

## File Structure

- `backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/test/TestDataSource.kt` — テスト用 DataSource ファクトリ。`create()` と `testHikariDataSource()` のプールをキャップ。
- `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt` — E2E の `testApplication` 起動。`MapApplicationConfig` でアプリプールを上書き。
- `backend/api/build.gradle.kts` — `integrationTest` タスク定義。毎回 rerun 化。
- `.github/workflows/ci.yml` — エスケープハッチ コメントの更新。

**前提:** Postgres が起動済みであること(`docker compose up -d postgres`)。検証タスクで必須。

---

## Task 1: `TestDataSource` のプールをキャップ

**Files:**
- Modify: `backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/test/TestDataSource.kt`

このファイルは 2 つのプール生成元を持つ。`create()`(schema 作成/破棄の root DS、現状 pool=4)と `testHikariDataSource()`(seed/Flyway 用、現状プール未指定=HikariCP default 10)。両方を 1 に絞る。HikariCP は `minimumIdle = maximumPoolSize` が default なので、`maximumPoolSize = 1` にすると生成時に開く接続も 1 になる。

- [ ] **Step 1: `create()` のプールを 4 → 1 に変更**

`TestDataSource.create()` 内の `HikariConfig().apply { ... }`(現状 `maximumPoolSize = 4`)を次のように変更する。

変更前:
```kotlin
        val config =
            HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = dbUrl
                username = dbUser
                password = dbPassword
                maximumPoolSize = 4
                isAutoCommit = false
            }
```

変更後:
```kotlin
        val config =
            HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = dbUrl
                username = dbUser
                password = dbPassword
                // 単発 DDL(CREATE/DROP SCHEMA)を流すだけなので 1 接続で足りる。
                // テスト並列・連続実行時の Postgres 接続枯渇を避けるためキャップする。
                maximumPoolSize = 1
                isAutoCommit = false
            }
```

- [ ] **Step 2: `testHikariDataSource()` にプール上限を追加**

`testHikariDataSource()` 内の `HikariConfig().apply { ... }`(現状プール指定なし)を次のように変更する。

変更前:
```kotlin
    val config =
        HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
        }
```

変更後:
```kotlin
    val config =
        HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            // Flyway は PostgreSQL のセッションロック用に 1 接続を保持したまま
            // マイグレーション実行用にもう 1 接続を要求するため、最低 2 必要
            // (pool=1 だと self-deadlock で 30s タイムアウトする)。
            // default の 10 は接続枯渇の主因なので、必要最小限の 2 に絞る。
            maximumPoolSize = 2
            minimumIdle = 2
        }
```

- [ ] **Step 3: コンパイル確認**

Run: `./gradlew :backend:core:compileTestFixturesKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/test/TestDataSource.kt
git commit -m "test: TestDataSource のプールを 1 にキャップ"
```

---

## Task 2: E2E アプリプールを `MapApplicationConfig` で上書き

**Files:**
- Modify: `backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt`

`e2eTest()` は `testApplication` で本番 DI モジュール(`exposedConfigure`)を起動する。`MapApplicationConfig` は jdbc-url/username/password だけ上書きし `maximum-pool-size` を差し替えていなかったため、`application.yaml` の本番値 `10` がテストでも効いていた。これが E2E 1 テストあたり最大の接続消費源。`"2"` で上書きする。

- [ ] **Step 1: `MapApplicationConfig` に `maximum-pool-size` を追加**

`e2eTest()` 内の `MapApplicationConfig(...)`(`E2eTestSupport.kt` の `environment { config = ... }`)を次のように変更する。

変更前:
```kotlin
                            MapApplicationConfig(
                                "external.datasource.database.jdbc-url" to jdbcUrl,
                                "external.datasource.database.username" to TestDataSource.user,
                                "external.datasource.database.password" to TestDataSource.password,
                                "external.auth.issuer" to TestJwtIssuer.DEFAULT_ISSUER,
                                "external.auth.audience" to TestJwtIssuer.DEFAULT_AUDIENCE,
                                "external.auth.jwks-url" to SharedJwksServer.jwksUrl,
                            ),
```

変更後:
```kotlin
                            MapApplicationConfig(
                                "external.datasource.database.jdbc-url" to jdbcUrl,
                                "external.datasource.database.username" to TestDataSource.user,
                                "external.datasource.database.password" to TestDataSource.password,
                                // 本番 application.yaml は maximum-pool-size: 10。テストでは
                                // RPC を概ね直列に叩くため 2 で足り、接続枯渇を防ぐ。
                                // 接続取得タイムアウトが出たら 3〜4 へ上げる(Task 5 で確認)。
                                "external.datasource.database.maximum-pool-size" to "2",
                                "external.auth.issuer" to TestJwtIssuer.DEFAULT_ISSUER,
                                "external.auth.audience" to TestJwtIssuer.DEFAULT_AUDIENCE,
                                "external.auth.jwks-url" to SharedJwksServer.jwksUrl,
                            ),
```

- [ ] **Step 2: コンパイル確認**

Run: `./gradlew :backend:api:compileTestKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt
git commit -m "test: E2E アプリプールを 2 にキャップ"
```

---

## Task 3: `integrationTest` を毎回 rerun させる

**Files:**
- Modify: `backend/api/build.gradle.kts`

外部 DB に当てる統合テストはキャッシュさせず毎回実行が正。`outputs.upToDateWhen { false }` で UP-TO-DATE スキップを無効化し、`cleanIntegrationTest` を都度付ける運用を不要にする。

- [ ] **Step 1: `integrationTest` タスクに `outputs.upToDateWhen { false }` を追加**

`val integrationTest by tasks.registering(Test::class) { ... }` ブロック内の先頭付近(`group`/`description` の直後)に 1 行追加する。

変更前:
```kotlin
val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against TEST_DB_URL."
    useJUnitPlatform()
```

変更後:
```kotlin
val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs @Tags(\"integration\") specs against TEST_DB_URL."
    // 外部 DB に当てる統合テストはキャッシュさせず毎回実行する(stale 結果防止)。
    outputs.upToDateWhen { false }
    useJUnitPlatform()
```

- [ ] **Step 2: タスク設定が解決できることを確認**

Run: `./gradlew :backend:api:help --task integrationTest`
Expected: `BUILD SUCCESSFUL` かつ `integrationTest` の説明が表示される(設定エラーが無いこと)。

- [ ] **Step 3: Commit**

```bash
git add backend/api/build.gradle.kts
git commit -m "test: integrationTest を毎回 rerun させ stale 結果を防ぐ"
```

---

## Task 4: CI のエスケープハッチ コメントを更新

**Files:**
- Modify: `.github/workflows/ci.yml`

`integration-test` ジョブの `Integration tests` step にある「枯渇したら `--max-workers=1` を付与」コメントは、プール上限化で対処済みのため撤去し、現状を反映した文言にする。

- [ ] **Step 1: コメント行を更新**

`.github/workflows/ci.yml` の `Integration tests` step を次のように変更する。

変更前:
```yaml
      - name: Integration tests
        # HikariPool 接続枯渇で flaky 化したら `--max-workers=1` を付与する。
        env:
          TEST_DB_URL: jdbc:postgresql://localhost:5432/mindstock_test
```

変更後:
```yaml
      - name: Integration tests
        # テスト用 HikariCP プールはキャップ済み(TestDataSource / E2E アプリプール)。
        # default 並列度で接続枯渇しない。詳細は
        # docs/superpowers/specs/2026-05-30-integration-test-pool-capping-design.md
        env:
          TEST_DB_URL: jdbc:postgresql://localhost:5432/mindstock_test
```

- [ ] **Step 2: YAML が壊れていないことを確認**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('yaml ok')"`
Expected: `yaml ok`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: 接続プール上限化に伴い --max-workers=1 コメントを撤去"
```

---

## Task 5: 経験的検証(受け入れ基準ゲート)

**Files:** なし(検証のみ)

この問題は flakiness。接続数の計算は必要条件であって十分条件ではない。**default workers で連続 3 回**実行し、`too many clients already` が一度も出ないことを実機で確認する。これが完了ゲート。推論だけで done にしない。

- [ ] **Step 1: Postgres が起動していることを確認**

Run: `docker compose ps postgres`
Expected: postgres が `running`(`Up`/healthy)。起動していなければ `docker compose up -d postgres` で起動する。

- [ ] **Step 2: default workers で連続 3 回 integrationTest を実行**

Run(`--max-workers=1` を付けない・`cleanIntegrationTest` を付けない):
```bash
for i in 1 2 3; do \
  echo "=== run $i ==="; \
  ./gradlew :backend:api:integrationTest || { echo "RUN $i FAILED"; break; }; \
done
```
Expected: 3 回すべて `BUILD SUCCESSFUL`。

- [ ] **Step 3: 接続枯渇エラーが出ていないことを確認**

Run:
```bash
grep -rl "too many clients\|PoolInitializationException\|Connection is not available" \
  backend/api/build/reports backend/api/build/test-results 2>/dev/null || echo "no connection errors"
```
Expected: `no connection errors`。

もし `Connection is not available`(接続取得タイムアウト=プールが小さすぎてハング)が出た場合は、`E2eTestSupport.kt` の `maximum-pool-size` を `"2"` → `"3"` または `"4"` に上げて Task 5 を再実行する(spec のトレードオフ記載どおり)。

- [ ] **Step 4: 検証結果を記録してコミット(変更があった場合のみ)**

プール値を調整した場合のみ:
```bash
git add backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt
git commit -m "test: E2E アプリプールを N に調整(接続取得タイムアウト回避)"
```
調整不要なら Step 4 はスキップ。

---

## 実装後フォローアップ(プラン外・任意)

- `local-build-tips` メモリの `--max-workers=1` / `cleanIntegrationTest` 定型を、不要になった旨に更新する(メモリ作業のため本プランのタスクには含めない)。

---

## Self-Review

- **Spec coverage:** spec の変更内容 1(TestDataSource キャップ=Task 1、E2E アプリプール=Task 2)、変更内容 2(毎回 rerun=Task 3)、変更内容 3(CI コメント=Task 4、メモリ更新=フォローアップ)、受け入れ基準(連続 3 回=Task 5)を全てカバー。本番 `application.yaml` 無変更も維持(どのタスクも触らない)。
- **Placeholder scan:** TBD/TODO 無し。各 code step に変更前後の実コードを記載。
- **Type consistency:** 設定キー `external.datasource.database.maximum-pool-size` は `ExposedDataSourceProperties` の `@SerialName("maximum-pool-size")` と一致。`maximumPoolSize` / `minimumIdle` は HikariCP API。
