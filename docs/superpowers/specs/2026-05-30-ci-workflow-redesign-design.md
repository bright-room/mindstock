# CI ワークフロー再設計

## 背景と目的

現状の `.github/workflows/ci.yml` は単一 job で `./gradlew check` を実行し、lint
(spotlessCheck) / 単体テスト / 結合テストをすべて直列にまとめている。これを以下の
ように改善する。

1. lint を先行ゲートにし、その後に単体テストと結合テストを並列実行する。
2. Gradle 公式の `gradle/actions/setup-gradle` によるキャッシュ戦略を実際に機能
   させる。
3. frontend / backend のテストを別 job に分割する。

## 現状の調査結果（設計の前提）

- `./gradlew check` は `spotlessCheck` + `test` + `integrationTest` に展開される。
  `integrationTest` は `backend/api/build.gradle.kts` の
  `tasks.check { dependsOn(integrationTest) }` で `check` に紐づいている。
- **DB が必要なのは `integrationTest` のみ**。`backend:core` / `backend:schedules`
  には単体テストのソースが無く(testFixtures のみ)、`backend:api` の `test` は
  `kotest.tags.exclude = "integration | manual"` で integration タグを除外する。
  DB を触るテストはすべて `backend:api` の `@Tags("integration")` 付きで、
  `integrationTest` タスク(外部 `TEST_DB_*` / Postgres service)で走る。
- `domain` のテストは KMP commonTest。jvm / js / wasmJs の各ターゲットで実行される
  ため、JVM 側は test-backend、js/wasm 側は test-frontend に自然に分かれる。
- backend モジュールから `:frontend` への build 依存・タスクエッジは無い
  (`projects.frontend` 参照なし、`backend:api` に `processResources`/wasm bundling
  なし)。よって frontend コンパイルが要求する `AUTH_*` env とヒープ懸念は
  test-frontend job だけに閉じる。`compose-web` 規約は `jvm()` ターゲットを定義
  しないため、`./gradlew jvmTest test` が frontend を巻き込むこともない。
- `generateAuthConfig`(frontend)は `providers.environmentVariable(...)` の遅延
  プロバイダで、frontend コンパイル実行時にのみ `AUTH_*` を読む。
- `gradle.properties`: `org.gradle.configuration-cache=true`,
  `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.workers.max=4`,
  `kotlin.daemon.jvmargs=-Xmx3072M`, `org.gradle.jvmargs=-Xmx3072M`。
- 現行 CI は full `check`(frontend wasmJs コンパイル/テスト含む)を 3072M キャップ
  のまま約 6〜8 分で green。よって 3072M は実証済みで十分。

## キャッシュが現状機能していない問題（最重要）

`setup-gradle` はデフォルトで **default branch でのみキャッシュを書き込む**
(他 branch は read-only)。現行 workflow のトリガーは `pull_request` のみで
default branch では走らないため、**キャッシュは一度も書き込まれず、毎回コールド**。
既存の `setup-gradle` はキャッシュ用途として実質ノーオペになっている。

→ `push: branches: [main]` トリガーを追加し、main の run が **同じ 4 job matrix**
を実行して各 job 種別(lint / test-backend / test-frontend / integrationTest)ごとの
キャッシュエントリを seed する。PR 側はそれを read-only で復元する。これは最適化
ではなく「キャッシュを存在させるための必須条件」。

## 設計

### トリガー

```yaml
on:
  pull_request:
  push:
    branches: [main]
```

加えて `concurrency` で同一 ref の進行中 run を cancel し、無駄な実行を抑える。

```yaml
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

### ジョブ構成（4 job）

```
lint  ──►  test-backend
      ├─►  test-frontend
      └─►  integrationTest      （3 つは needs: lint で並列）
```

| job | コマンド | DB | AUTH_* | 役割 |
|---|---|---|---|---|
| `lint` | `./gradlew spotlessCheck` | 不要 | 不要 | fast-fail ゲート |
| `test-backend` | `./gradlew test jvmTest` | 不要 | 不要 | JVM 系: backend:api 単体(integration タグ除外) + KMP の jvmTest(domain 等) |
| `test-frontend` | `./gradlew jsTest wasmJsTest` | 不要 | 要(placeholder) | JS/Wasm browser test: frontend + 共有モジュールの js/wasm |
| `integrationTest` | `./gradlew :backend:api:integrationTest` | 要(Postgres service + `TEST_DB_*`) | 不要 | 唯一 DB が要る所 |

- FE/BE 分割は **プラットフォーム境界(JVM vs JS/Wasm)** で割る。これが KMP の
  自然な切れ目で、現行 `check` のテストカバレッジを過不足なく再現する。
- `test-backend` の `./gradlew test jvmTest`: JVM 専用モジュールの `test`
  (backend:api 単体)+ KMP モジュールの `jvmTest`(domain など)。重複は無い
  (KMP モジュールに `test` タスクは無く、JVM モジュールに `jvmTest` は無い)。
- `test-frontend` の `./gradlew jsTest wasmJsTest`: 共有 KMP モジュール + frontend
  の js/wasm browser test。ubuntu runner の Chrome を使う(現行 `check` も同様に
  green)。
- 3 つのテスト job はすべて `needs: lint`。lint をゲートにするのは要件
  「lint を実施した後に test / integrationTest」に従うため。lint は数十秒で
  終わるためゲート化による latency 増は軽微で、フォーマット違反を即 fail できる。
- **正確なタスク名**(`jsTest`/`jsBrowserTest`, `wasmJsTest`/`wasmJsBrowserTest`)
  は実装時に `./gradlew tasks` で確認して確定する。

### キャッシュ戦略

- `gradle/actions/setup-gradle` 標準のキャッシュ(Gradle User Home: 依存・wrapper・
  build-cache・configuration-cache)をそのまま使う。自前キャッシュは作らない。
- job 単位でキャッシュエントリが分かれる。main の push run が seed、PR run が
  read-only で復元。
- **正直な注記(受容するコスト)**: 別 VM 並列のため、PR 1 回の中では
  test-backend / test-frontend / integrationTest の **変更モジュールのコンパイルは
  重複** する(runner 跨ぎのローカル build-cache 共有は remote build cache 基盤
  無しでは不可)。共有されるのは依存ダウンロードと、main で seed 済みの build-cache
  ヒット分。これは job 並列を選んだ代償であり、設計上の既知トレードオフ。

### 環境変数

- `AUTH_CLIENT_ID` / `AUTH_AUDIENCE` / `AUTH_PROJECT_ID` の placeholder は
  **test-frontend job のみ** に設定する(frontend コンパイルが要求する箇所)。
- `integrationTest` job のみ `TEST_DB_URL` / `TEST_DB_USER` / `TEST_DB_PASSWORD`
  と Postgres service を設定する(現行 ci.yml と同値)。

### アーティファクト

- 各テスト job は失敗時に `actions/upload-artifact` で
  `**/build/reports/` と `**/build/test-results/` をアップロードする。
- **artifact 名は job ごとに一意化**(`build-reports-<job>`)。`upload-artifact@v4`
  は同名 artifact で fail するため。

### あえてやらないこと（YAGNI / リスク回避）

- **path ベースの workflow 分割はしない**。`:domain` / `:rpc` / `:shared` が FE/BE
  双方から使われるため、「frontend 変更時は backend を skip」型は shared 変更を
  取りこぼしテスト不足になり危険。
- **remote build cache 基盤は導入しない**。今回のスコープ外。
- **frontend のヒープ増量はデフォルトでは行わない**。3072M は現行 CI で実証済みで、
  job 分割によりむしろ daemon の負荷は下がる。OOM が出た場合の escape hatch として
  `-Pkotlin.daemon.jvmargs=-Xmx6144M` を job 内コメントで明記するのみ。
- **`integrationTest` の `--max-workers=1` は強制しない**。現行 `check` が
  `org.gradle.parallel=true` で green なため踏襲。Postgres 接続枯渇
  (`max_connections=100`、test class ごとの JVM fork が HikariPool を開く)で
  flaky 化した場合の mitigation として `--max-workers=1` をコメントで残す。

## 想定 wall-clock

`lint`(数十秒) + max(test-backend, test-frontend, integrationTest)。lint は安価な
ゲート。現行の直列 `check`(約 6〜8 分)に対し、3 テストの並列化で総時間の短縮を狙う
(コンパイル重複ぶんは相殺要因)。

## 互換性 / 既存設定の踏襲

- `permissions: contents: read` は維持。
- `actions/checkout` の `persist-credentials: false` は維持。
- 既存のアクション pin(SHA + バージョンコメント)は踏襲し、Renovate の管理対象の
  まま据え置く。

## 受け入れ条件

- PR で 4 job(lint → test-backend / test-frontend / integrationTest)が表示され、
  lint 失敗時に後続 3 job が走らない。
- main への push でも同じ 4 job が走り、キャッシュが書き込まれる。
- 既存 `check` が検出していたテスト・lint をすべてカバーしている(カバレッジ後退
  なし)。
- 失敗時に job ごとに一意名のレポート artifact が上がる。
