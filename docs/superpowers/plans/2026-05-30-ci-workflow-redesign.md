# CI ワークフロー再設計 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `.github/workflows/ci.yml` を単一 `check` job から、lint ゲート →
test-backend / test-frontend / integrationTest の 4 job 並列構成に作り替え、
`setup-gradle` キャッシュを `push:[main]` seed で実機能化する。

**Architecture:** lint(`spotlessCheck`)を `needs` ゲートにし、後続 3 job を並列実行。
DB は integration-test job のみ。FE/BE はプラットフォーム境界(JVM vs JS/Wasm)で分割。
キャッシュは `gradle/actions/setup-gradle` 標準を使い、main の push run で seed、
PR run は read-only 復元。main は `concurrency` で cancel しない(automerge 対応)。

**Tech Stack:** GitHub Actions, Gradle (KMP), `gradle/actions/setup-gradle`,
actionlint / yamllint(ローカル検証)。

**Spec:** `docs/superpowers/specs/2026-05-30-ci-workflow-redesign-design.md`

---

## このプランの性質に関する注記

対象は CI 設定 YAML 1 ファイル。コードの単体テストは存在しないため、各タスクの
「検証」は **actionlint / yamllint による静的検証** と **`./gradlew … --dry-run`
で対象タスクが解決すること** で行い、最終的な受け入れは **PR 上で 4 job が green に
なること** で確認する。重い browser test / DB test はローカルで回さず CI に委ねる。

## File Structure

- **Modify(全面書き換え): `.github/workflows/ci.yml`** — 唯一の変更対象。4 job を定義。
  - checkout + setup-gradle のボイラープレートは 4 job で重複するが、4 job 程度では
    composite action 化は過剰(YAGNI)。現行も単一ファイルで composite 基盤を持たない
    ため、素直に繰り返す。
- 既存の action pin(checkout `…ce83dd # v6`、setup-gradle `…252f6e # v6`、
  upload-artifact `…fc6a0a # v7`)は Renovate 管理のまま**そのまま踏襲**する。

## 確定済みの Gradle コマンド(dry-run で検証済み)

| job | コマンド | 含まれる主なタスク |
|---|---|---|
| lint | `./gradlew spotlessCheck` | 全 7 モジュールの spotlessCheck |
| test-backend | `./gradlew test jvmTest` | backend:api/core/schedules `:test` + domain/rpc/shared `:jvmTest` |
| test-frontend | `./gradlew jsTest wasmJsTest` | domain/rpc/shared/frontend の js/wasm browser test |
| integration-test | `./gradlew :backend:api:integrationTest` | `@Tags("integration")` 付き spec |

---

## Task 1: 新しい ci.yml を書く

**Files:**
- Modify(全面置換): `.github/workflows/ci.yml`

- [ ] **Step 1: `.github/workflows/ci.yml` を以下の内容で全面置換する**

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

# PR は新しい push で古い run を cancel(CI 分節約)。
# main(push)は cancel せず全コミットを完走させる:
#   - 各コミットの CI 検証を残す(Renovate automerge 対応)
#   - setup-gradle のキャッシュ seed を各コミットで行う
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6
        with:
          persist-credentials: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6

      - name: Spotless check
        run: ./gradlew spotlessCheck

  test-backend:
    needs: lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6
        with:
          persist-credentials: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6

      - name: Unit tests (JVM)
        run: ./gradlew test jvmTest

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7
        with:
          name: build-reports-test-backend
          path: |
            **/build/reports/
            **/build/test-results/

  test-frontend:
    needs: lint
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6
        with:
          persist-credentials: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6

      - name: Browser tests (JS/Wasm)
        # AUTH_* は frontend/build.gradle.kts:generateAuthConfig が要求する。
        # CI ではコンパイルが通れば十分なので placeholder で埋める。
        # WasmJs compile で OOM したら -Pkotlin.daemon.jvmargs=-Xmx6144M を付与する。
        env:
          AUTH_CLIENT_ID: ci-placeholder
          AUTH_AUDIENCE: ci-placeholder
          AUTH_PROJECT_ID: ci-placeholder
        run: ./gradlew jsTest wasmJsTest

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7
        with:
          name: build-reports-test-frontend
          path: |
            **/build/reports/
            **/build/test-results/

  integration-test:
    needs: lint
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:18.0-alpine
        env:
          POSTGRES_USER: mindstock
          POSTGRES_PASSWORD: mindstock
          POSTGRES_DB: mindstock_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U mindstock -d mindstock_test"
          --health-interval 5s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # v6
        with:
          persist-credentials: false

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6

      - name: Integration tests
        # HikariPool 接続枯渇で flaky 化したら `--max-workers=1` を付与する。
        env:
          TEST_DB_URL: jdbc:postgresql://localhost:5432/mindstock_test
          TEST_DB_USER: mindstock
          TEST_DB_PASSWORD: mindstock
        run: ./gradlew :backend:api:integrationTest

      - name: Upload reports on failure
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7
        with:
          name: build-reports-integration-test
          path: |
            **/build/reports/
            **/build/test-results/
```

- [ ] **Step 2: yamllint で構文を検証する**

Run: `yamllint .github/workflows/ci.yml`
Expected: エラー無し(終了コード 0)。行長 warning が出る場合は許容
(`options: >-` の長い health-cmd 等)。エラーが出たらインデントを修正。

- [ ] **Step 3: actionlint でワークフローを検証する**

Run: `actionlint .github/workflows/ci.yml`
Expected: エラー無し(終了コード 0)。`needs`, `concurrency` 式、service 定義、
action ref が全て妥当であることを確認。エラーが出たら該当箇所を修正。

- [ ] **Step 4: 4 job のコマンドが Gradle タスクとして解決することを確認する**

Run:
```bash
./gradlew spotlessCheck --dry-run >/dev/null && echo "lint OK"
./gradlew test jvmTest --dry-run >/dev/null && echo "test-backend OK"
./gradlew jsTest wasmJsTest --dry-run >/dev/null && echo "test-frontend OK"
./gradlew :backend:api:integrationTest --dry-run >/dev/null && echo "integration-test OK"
```
Expected: 4 行すべて `… OK` が出力される(各コマンドが BUILD SUCCESSFUL)。

- [ ] **Step 5: lint と backend 単体テストをローカルで実走して green を確認する**

(browser test / DB test は遅く/環境依存のため CI に委ねる。ここでは DB 不要で
高速な 2 つだけ実走する)

Run: `./gradlew spotlessCheck test jvmTest`
Expected: `BUILD SUCCESSFUL`。失敗する場合は既存コードの問題であり本変更のスコープ外
だが、その旨を記録して停止し、ユーザに報告する。

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: lint ゲート + test/integrationTest 並列化と setup-gradle キャッシュ実機能化

単一 check job を 4 job(lint→test-backend/test-frontend/integration-test)に分割。
push:[main] で setup-gradle キャッシュを seed。main は concurrency で cancel しない。

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: PR を作成し、実 CI で 4 job を検証する

**Files:** なし(GitHub 上での確認)

- [ ] **Step 1: ブランチを push する**

Run: `git push -u origin feat/ci-workflow-redesign`
Expected: push 成功。

- [ ] **Step 2: PR を作成する**

Run:
```bash
gh pr create --base main --title "ci: CI ワークフローを 4 job 並列構成に再設計" \
  --body "$(cat <<'EOF'
## 概要
単一 `check` job を以下の 4 job に分割し、`setup-gradle` キャッシュを実機能化する。

- `lint`(spotlessCheck)を `needs` ゲートにし、後続 3 job を並列実行
- `test-backend`(`test jvmTest`) / `test-frontend`(`jsTest wasmJsTest`) / `integration-test`(`:backend:api:integrationTest`)
- `push:[main]` トリガー追加でキャッシュを seed(従来は pull_request のみで書き込み 0=ノーオペだった)
- main は `concurrency` で cancel せず全コミット完走(Renovate automerge 対応)
- artifact 名を job ごとに一意化(upload-artifact v4 の同名衝突回避)

設計: `docs/superpowers/specs/2026-05-30-ci-workflow-redesign-design.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
Expected: PR URL が表示される。

- [ ] **Step 3: CI の 4 job の結果を待って確認する**

Run: `gh pr checks --watch`
Expected: `lint` / `test-backend` / `test-frontend` / `integration-test` の 4 つが
表示され、lint 成功後に 3 つが走り、全て `pass`。

- [ ] **Step 4: lint ゲート挙動の確認(任意・観察のみ)**

`gh run view <run-id>` で、`test-backend` 等が `lint` 成功後に開始していること
(タイムライン上 lint より後)を確認する。失敗時の挙動(lint 落ちたら後続が走らない)
は今回の green PR では発生しないため、コードレビューでの目視確認に委ねる。

- [ ] **Step 5: 完了報告**

4 job が green であること、各 job の所要時間、従来の単一 job(約 6〜8 分)との
wall-clock 比較をユーザに報告する。

---

## Self-Review

- **Spec coverage:**
  - lint ゲート + test/IT 並列 → Task 1 の lint/test-backend/test-frontend/
    integration-test job(`needs: lint`)。✓
  - FE/BE 分割(プラットフォーム境界)→ test-backend(`test jvmTest`) /
    test-frontend(`jsTest wasmJsTest`)。✓
  - setup-gradle キャッシュ実機能化 + `push:[main]` seed → Task 1 の `on.push` と
    setup-gradle ステップ。✓
  - concurrency(main は cancel しない)→ Task 1 の `concurrency` ブロック。✓
  - artifact 名一意化 → `build-reports-<job>`。✓
  - DB は integration-test のみ + `TEST_DB_*` → integration-test job の service と env。✓
  - AUTH_* は test-frontend のみ → test-frontend job の env。✓
  - ヒープ増量しない(escape hatch コメントのみ)→ test-frontend のコメント。✓
  - `--max-workers=1` 強制しない(コメントのみ)→ integration-test のコメント。✓(⚠️ 2026-05-30: プールキャップで接続枯渇を解消し、このコメント自体も撤去済み。`docs/superpowers/specs/2026-05-30-integration-test-pool-capping-design.md`)
  - permissions / persist-credentials / action pin 踏襲 → Task 1 で維持。✓
- **Placeholder scan:** プレースホルダ無し。全 step に実コマンド/実 YAML を記載。✓
- **Type consistency:** job 名(lint / test-backend / test-frontend / integration-test)、
  artifact 名(build-reports-<job>)が Task 1・Task 2・Self-Review 全体で一致。✓
