# 統合テストの接続プール上限化 — `--max-workers=1` を不要にする

- 日付: 2026-05-30
- 状態: 設計合意済み(実装前)
- スコープ: **テスト/CI のみ。本番コード・本番設定は変更しない**

## 背景と問題

`./gradlew :backend:api:integrationTest` を default 並列度で(あるいは連続して)実行すると、
`com.zaxxer.hikari.pool.HikariPool$PoolInitializationException: ... FATAL: sorry, too many
clients already` で大量に fail する。これを避けるため、運用上 `--max-workers=1` を手で付けて
Gradle 全体を直列化していた。

`--max-workers=1` は Gradle 全体(コンパイル含む)を直列化する乱暴な対処で、本来の問題を
「隠している」だけ。これはローカル/CI のテスト実行時のみの概念で、本番には存在しない。

## 真因 — テスト固有のプール過大

`integrationTest` は単一 Test タスクで、`maxParallelForks` は default の 1。Kotest の
parallelism/isolation 設定も無い。よって**タスク内ではテストは 1 JVM で逐次実行**される。
「テストクラスごとに fork して並列で枯渇」という従来の理解は誤り。

真因は、テストが「fresh schema ごとに HikariPool を開いては捨てる」を何十回も繰り返す点。
HikariCP は `minimumIdle = maximumPoolSize` が default なので、プールは生成時に最大数を
**即座にすべて開く**。各テストのプールが過大で、逐次でも開閉サイクル + Postgres の接続 reap
遅延で瞬間的に `max_connections=100` を超える(連続実行・broad command での task 並列で悪化)。

### E2E 1 テストあたりの同時接続(現状)

| 発生源 | プール | 状態 |
|---|---|---|
| `TestDataSource.create()`(schema 作成/破棄) | 4 ×2 | 一時 |
| `testHikariDataSource()`(seed/Flyway 用) | **10**(未指定=default) | テスト中保持 |
| アプリ Exposed モジュール(`testApplication` が本番 DI を起動) | **10** | テスト中保持 |
| アプリ migration モジュール | 2 | 起動時一時 |

→ 1 テストで瞬間 **20〜24 接続**。Repository テストは `testHikariDataSource`(10)+ root(4+4)。

決定的な見落とし: E2E の `MapApplicationConfig` 上書き(`E2eTestSupport.kt`)は
jdbc-url/username/password だけ差し替え、**`maximum-pool-size` を上書きしていない**。
そのため `application.yaml` の本番値 `maximum-pool-size: 10` がテストでもそのまま効いていた。

## 本番非影響(明示)

本番はアプリのプール(`maximum-pool-size: 10`)を 1 プロセス・1 つだけ長時間保持する。
「テストごとに開閉を繰り返す」サイクルは本番に存在しない。よって本問題は完全にテスト固有。

`application.yaml` の `maximum-pool-size: 10` は**変更しない**。E2E でのプール縮小は
`testApplication` 起動時の `MapApplicationConfig` でのみ上書きする(本番起動時は 10 のまま)。

## 採用方針 — Approach A: テスト用プールを全部キャップ

真因(プール過大)を直接除去する。テストコード/テスト設定に閉じ、本番は無変更。
プールを絞ることで、`--max-workers=1` が不要になるだけでなく、**将来むしろ並列度を
上げても安全**になる(`--max-workers=1` とは真逆の性質)。

### 検討した他案(不採用)

- **B: スイート全体で共有プール 1 つ**: fresh-schema ごとの search_path 分離と
  プール共有が衝突しやすく、テスト基盤の書き換えが大きい。侵襲的。
- **C: `maxParallelForks=1` をタスクに焼き込むだけ**: コマンドからフラグは消えるが、
  連続実行時の reap 遅延による枯渇は残り得る。真因未解決。

## 変更内容

### 1. テスト用プールのキャップ

**`backend/core/src/testFixtures/kotlin/net/brightroom/mindstock/test/TestDataSource.kt`**

- `create()`: `maximumPoolSize = 4` → **`1`**(単発 DDL を流すだけ)。
- `testHikariDataSource()`: プール未指定(default 10)→ **`maximumPoolSize = 2`** と
  **`minimumIdle = 2`** を明示。**Flyway は PostgreSQL のセッションロック用に 1 接続を
  保持したままマイグレーション実行用にもう 1 接続を要求するため、最低 2 必要**
  (pool=1 だと self-deadlock し `Connection is not available, request timed out after
  30000ms` で全 e2e/repository 統合テストが fail する。実機検証で確認済み)。
  default の 10 は接続枯渇の主因なので必要最小限の 2 に絞る。

**`backend/api/src/test/kotlin/net/brightroom/mindstock/e2e/E2eTestSupport.kt`**

- `MapApplicationConfig` に **`"external.datasource.database.maximum-pool-size" to "2"`** を追加。
  これが最大の発生源(アプリ本体が本番設定 10 で起動していた)の除去。

#### トレードオフ: 絞りすぎは「枯渇」を「ハング」に化けさせる

アプリプールを過小にすると、接続枯渇ではなく**接続取得待ちのハング(`connectionTimeout`
default 30s 後に例外)**に変わり得る。E2E は RPC を概ね直列に叩くため 2 で足りる見込みだが、
**検証で取得タイムアウト/タイムアウト失敗が出た場合は 3〜4 へ引き上げる**前提とする。
この調整は受け入れ基準(下記)の経験的検証で確定させる。

#### 接続バジェット(変更後の見積り・実機計測)

- E2E 1 テスト: `testHikariDataSource`(2) + アプリプール(2) + 一時 create(1) ≈ **実測 peak 5**
  (1 クラス実行・連続 1s サンプリングで確認)。
- Repository 1 テスト: `testHikariDataSource`(2) + root 一時(1) ≈ **3**。
- 仮に将来 `maxParallelForks=4` にしても 4 × ~5 = ~20 で 100 に十分な余裕。

### 2. 連続実行で stale 結果を見ない

**`backend/api/build.gradle.kts`** の `integrationTest` タスクに
**`doNotTrackState("integration tests run against a live external DB")`** を追加。外部 DB に
当てる統合テストは毎回実行が正で、`cleanIntegrationTest` を都度付ける運用が不要になる。

- `doNotTrackState` は **UP-TO-DATE チェックとビルドキャッシュの両方** を無効化する。
  `outputs.upToDateWhen { false }` は UP-TO-DATE しか無効化せず、`org.gradle.caching=true`
  環境では `@CacheableTask` の Test タスクが FROM-CACHE で復元され実行をスキップしうるため不可。
- 留意: `tasks.check { dependsOn(integrationTest) }` のため、`check`/`build` 経由でも
  毎回 integrationTest が走る(意図どおり。外部 DB 依存テストはキャッシュさせない)。

### 3. CI / ドキュメントの後始末

- `.github/workflows/ci.yml`(現状 `Integration tests` step の
  「HikariPool 接続枯渇で flaky 化したら `--max-workers=1` を付与する」コメント)を、
  プール上限で対処済みである旨に更新。エスケープハッチの記述は撤去する。
- 実装後フォローアップ(別途・メモリ作業): `local-build-tips` メモリの
  `--max-workers=1` / `cleanIntegrationTest` 定型を更新。

## 受け入れ基準

flakiness 問題のため、接続数の計算は必要条件だが十分条件ではない。**経験的検証が必須**で、
推論だけで完了としない。

1. **default workers** で `./gradlew :backend:api:integrationTest` を **連続 3 回** 実行し、
   `too many clients already` が一度も出ない。
2. 全統合テストが pass(小さいアプリプールによる接続取得タイムアウトが発生しない)。
3. `--max-workers=1` / `cleanIntegrationTest` を一切付けない。

## 影響ファイル一覧

| ファイル | 区分 | 本番影響 |
|---|---|---|
| `backend/core/src/testFixtures/.../TestDataSource.kt` | testFixtures(テスト専用) | なし |
| `backend/api/src/test/.../e2e/E2eTestSupport.kt` | src/test | なし |
| `backend/api/build.gradle.kts`(`integrationTest` タスク) | テストタスク定義 | なし |
| `.github/workflows/ci.yml`(コメント) | CI | なし |
| `application.yaml`(`maximum-pool-size: 10`) | **変更しない** | — |

## 関連

- memory: `local-build-tips`(`--max-workers=1` / WasmJs OOM のローカル罠)
- memory: `ci-caching-facts`(GHA 4 job 構成)
- spec: `docs/superpowers/specs/2026-05-30-ci-workflow-redesign-design.md`
- spec: `docs/superpowers/specs/2026-05-25-integration-tests-design.md`
