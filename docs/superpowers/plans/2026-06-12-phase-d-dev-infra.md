# フェーズ D: 開発基盤・CI 実装プラン

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development(推奨)または superpowers:executing-plans でタスク単位に実装する。各ステップは進捗追跡のためチェックボックス(`- [ ]`)記法。本 spec は親プラン `2026-06-12-refactoring-master-plan.md` のフェーズ D を展開したもの。

**Goal:** CI の検証穴(frontend wasm ビルド未検証 / timeout なし / setup-java 暗黙依存)と無駄を塞ぎ、ローカル開発基盤(compose / mise / README / gitignore)を実態に同期させる。**Kotlin コードには一切触らない。**

**Architecture:** 既存の CI(4 job: lint → test-backend / test-frontend / integration-test)と compose(postgres / zitadel / garage + 各 init)構成は維持。変更は「検証穴を塞ぐ」「設定とドキュメントを実態へ同期」のみで、構造再設計はしない。他のコードフェーズ(R / 0〜5)とファイルが重ならないため**いつでも並行可・独立 1 PR**。

**Tech Stack:** GitHub Actions / Docker Compose / Gradle(Kotlin Multiplatform)/ mise / renovate

**ブランチ:** `refactor/pD-dev-infra`(main 起点)。フェーズ内は小さくコミット。コミットメッセージに issue/PR 番号を書かない。

---

## 着手前に読む(本 spec 作成時の現物確認サマリ・2026-06-12 再確認済み)

親プランの file:line は 2026-06-12 のスナップショット。**本 spec 作成にあたり全対象ファイルを現物 Read 済み**で、座標と前提を更新した。実装時もコミット直前に再 grep して確認すること。主要な更新点:

- **【座標ズレ】application.yaml の `$PORT` は 3 行目**(親プラン D-12 は「:4」だが実際は `:3`)。
- **【前提誤り・要注意】D-2「`test jvmTest` は二重実行」は誤り。** `./gradlew test jvmTest --dry-run` で実測した結果、両者は **対象モジュールが完全に分離**している:
  - `test` → `:backend:api` / `:backend:core` / `:backend:schedules`(JVM モジュール)
  - `jvmTest` → `:domain` / `:rpc` / `:shared`(KMP モジュール。`test` タスクを持たない)
  - → **`jvmTest` を外すと domain/rpc/shared のテストが CI から消える。削除は不採用。** D-2 は「重複ではない」ことを CI にコメントで残す対応に変更(後述)。
- **【前提要注意】D-6 の garage healthcheck に wget は使えない。** garage イメージ(`dxflrs/garage`)は scratch ベースで shell も wget も持たない(compose.yml:98 のコメントが明記)。`garage` バイナリ自身(`/garage status`)を使う案に変更し、クリーン起動で必ず実機検証する(後述)。
- **【確認済み】D-4 の github-actions manager は稼働中。** renovate PR #101 `chore(deps): update actions/checkout digest to df4cb1c` が現に出ている → manager 追加は不要、`group:springBoot` 削除のみ。
- **【確認済み】release.yml は `.github/release.yml`(workflows 配下ではない)。** ラベルドリブン changelog。カテゴリは `Impact: Breaking` / `Kind: Feature` / `Kind: Enhancement` / `Kind: Bug Fix` / `Kind: Dependencies` / 除外 `Meta: Release note ignored`。リポジトリには他に `Kind: Refactoring` / `Kind: Tests` / `Kind: Documentation` ラベルも存在(release.yml では `*` で Other Changes 扱い)。
- **【確認済み】PR テンプレートは未存在**(`.github/pull_request_template.md` なし)→ 新設。
- **【確認済み】env インベントリ付録**: `docs/superpowers/plans/2026-06-12-env-inventory.md`(D-8 の README 環境変数リファレンスの元ネタ)。
- **【確認済み】`compileProductionExecutableKotlinWasmJs` は実在タスク**(`:frontend:tasks --all` で確認)。`generateAuthConfig`(frontend/build.gradle.kts:52-53)は `AUTH_CLIENT_ID` / `AUTH_PROJECT_ID` を `.orElse` なしで要求 → wasm ビルド job にも placeholder env が必要。
- **【確認済み】`gradle.properties:3` は `kotlin.daemon.jvmargs=-Xmx3072M`(コメントアウトではなく有効・3072M)。** 親プランの「コメントアウト中の jvmargs を有効化」は ci.yml:70 のコメント(`-Pkotlin.daemon.jvmargs=-Xmx6144M` を付与せよ)を指す。OOM 時はこのフラグを CI コマンドに付ける。

**親プランの決定事項(再燃防止・抜粋):**
- `kotlin-js-store` lockfile はコミット管理に切替(ignore 解除)= D-9。
- Zitadel masterkey は `${ZITADEL_MASTERKEY:-...}` で環境変数化(dev デフォルト付き)= D-6。
- `PORT` デフォルトは 8090 に変更 = D-12。
- `dependabot.yml` は追加しない。`SECURITY.md` / job レベル permissions は今はやらない。`ktorLib` 外部 version catalog のインライン化はしない(renovate 追従だけ D-4 で確認)。

---

## 実行順序の指針

1 PR だが、同一ファイルを触るタスクはまとめてコミットすると diff が綺麗。推奨グルーピング:
1. **ci.yml 系**(D-11 → D-1 → D-2 → D-3): setup-java を先に入れてから wasm job 追加・timeout 追加。
2. **renovate / PR プロセス**(D-4, D-5)。
3. **compose / docker**(D-6, D-10)。
4. **設定同期**(D-7, D-12)。
5. **gitignore / README**(D-9 → D-8。D-9 で lockfile を確定させてから README に手順を書く)。

各タスク末尾で `git commit`。最後に検証セクションのクリーン起動 + draft PR で CI 一巡。

---

## Task D-11: CI に setup-java を明示追加

**狙い:** 現状は ubuntu-latest プリインストール JDK への暗黙依存。ランナーイメージ更新で壊れる。全 job に `actions/setup-java`(temurin / 25)を明示。先に入れることで以降の job 編集の足場が固まる。

**Files:**
- Modify: `.github/workflows/ci.yml`(4 job: lint / test-backend / test-frontend / integration-test の各 `Set up Gradle` ステップ直前)

- [ ] **Step 1: 各 job の checkout と Set up Gradle の間に setup-java ステップを挿入**

4 つの job それぞれで、`actions/checkout` ステップの直後・`Set up Gradle` ステップの直前に以下を挿入する(SHA は renovate が追従するので最新 pin を使う。挿入時点で `renovate.json` 管理対象になるよう `# v5` 等のコメントを付ける)。

```yaml
      - name: Set up JDK
        uses: actions/setup-java@v5  # renovate が SHA pin + digest 更新を管理する
        with:
          distribution: temurin
          java-version: "25"
```

> 注: 既存の他 action(checkout / setup-gradle)は SHA pin + `# v6` コメント形式。renovate の `helpers:pinGitHubActionDigests` 相当が効くよう、初回は `actions/setup-java@v5` で入れて push 後に renovate の digest pin PR を取り込むか、既存と同様に手で SHA pin する。**どちらでも可だが既存スタイル(SHA pin)に揃えるのが望ましい。** 実際の最新 SHA は `gh api repos/actions/setup-java/git/refs/tags/v5 --jq '.object.sha'` で取得。

- [ ] **Step 2: foojay resolver との関係をコメントで明示**

`settings.gradle.kts` の foojay-resolver はローカルの toolchain 自動解決用。CI では setup-java が JDK を用意することを 1 行コメントで残す(lint job の setup-java ステップ上などに)。

```yaml
        # ローカルは mise(openjdk-25)/ foojay resolver で JDK 解決。CI はこの setup-java を正とする。
```

- [ ] **Step 3: コミット**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: 全 job に setup-java(temurin 25)を明示しランナー JDK への暗黙依存を解消"
```

---

## Task D-1: frontend wasm 本番ビルドの CI 検証を追加

**狙い:** 現状 test-frontend job は `jsTest wasmJsTest` のみ。**テストが通っても本番 wasm 成果物のコンパイルが壊れることを検知できない。** `compileProductionExecutableKotlinWasmJs` を追加する(フル `wasmJsBrowserDistribution` はローカル OOM 実績があるため compile に留める)。

**Files:**
- Modify: `.github/workflows/ci.yml`(test-frontend job)

- [ ] **Step 1: test-frontend job に本番 wasm ビルドステップを追加**

`Browser tests (JS/Wasm)` ステップ(現 ci.yml:67-74)の直後に以下を追加。`generateAuthConfig` が `AUTH_CLIENT_ID` / `AUTH_PROJECT_ID` を要求するため同じ placeholder env を付ける。

```yaml
      - name: Production wasm build (成果物コンパイル検証)
        # テストは成果物の破損(本番 wasm compile)を検知しない。本番ビルドの compile だけ検証する
        # (フル wasmJsBrowserDistribution は重く OOM 実績があるため compile タスクに留める)。
        env:
          AUTH_CLIENT_ID: ci-placeholder
          AUTH_PROJECT_ID: ci-placeholder
        run: ./gradlew compileProductionExecutableKotlinWasmJs
```

- [ ] **Step 2: OOM ガードのコメントを実態に合わせる**

既存コメント(ci.yml:70 付近、`WasmJs compile で OOM したら -Pkotlin.daemon.jvmargs=-Xmx6144M を付与する`)は維持。draft PR で本番 wasm compile が OOM した場合のみ、本ステップの `run` を次に変更する:

```yaml
        run: ./gradlew compileProductionExecutableKotlinWasmJs -Pkotlin.daemon.jvmargs=-Xmx6144M
```

> `gradle.properties:3` の既定は `-Xmx3072M`。draft PR の所要時間とメモリを見てから判断する(検証セクション参照)。OOM しなければフラグは付けない。

- [ ] **Step 3: コミット**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: frontend の本番 wasm コンパイル検証を test-frontend job に追加"
```

---

## Task D-2: `test jvmTest` の役割をコメント明示(削除はしない)

**狙い(親プランから変更):** 親プラン D-2 は「`jvmTest` は `test` と二重実行なので削除」だったが、**現物検証で前提が誤りと判明**。`./gradlew test jvmTest --dry-run` の結果、両タスクは対象モジュールが完全に分離している:

```
:backend:api:test  / :backend:core:test  / :backend:schedules:test   ← test(JVM モジュール)
:domain:jvmTest    / :rpc:jvmTest        / :shared:jvmTest            ← jvmTest(KMP モジュール。test タスクを持たない)
```

→ **`jvmTest` を外すと domain/rpc/shared のテストが CI から落ちる。削除は不採用。** 代わりに、将来この行を見た人が再び「重複」と誤認しないよう CI にコメントを残す。

**Files:**
- Modify: `.github/workflows/ci.yml`(test-backend job の `Unit tests (JVM)` ステップ)

- [ ] **Step 1: `Unit tests (JVM)` ステップにコメントを追加**

現 ci.yml:44-45:

```yaml
      - name: Unit tests (JVM)
        run: ./gradlew test jvmTest
```

を次に変更(`run` 行は変えない。コメントのみ追加):

```yaml
      - name: Unit tests (JVM)
        # test と jvmTest は対象が分離している(重複ではない):
        #   test    → :backend:* (kotlin-jvm モジュール)
        #   jvmTest → :domain / :rpc / :shared (KMP モジュール。test タスクを持たない)
        # どちらを外しても片方のモジュール群のテストが CI から落ちるため両方必須。
        run: ./gradlew test jvmTest
```

- [ ] **Step 2: コミット**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: test と jvmTest が対象分離(重複でない)である旨をコメント明示"
```

---

## Task D-3: 全 job に timeout / paths-ignore / 失敗時ログ収集を追加

**狙い:** timeout なしで吊られると CI 分を浪費。docs 変更でも全 job が走る。integration 失敗時に compose ログが残らない。

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: `on:` に paths-ignore を追加**

現 ci.yml:3-6:

```yaml
on:
  pull_request:
  push:
    branches: [main]
```

を次に変更(docs / markdown 変更では CI を回さない):

```yaml
on:
  pull_request:
    paths-ignore:
      - "docs/**"
      - "**.md"
  push:
    branches: [main]
    paths-ignore:
      - "docs/**"
      - "**.md"
```

- [ ] **Step 2: 各 job に `timeout-minutes` を追加**

`lint` / `test-backend` / `test-frontend` の `runs-on: ubuntu-latest` の直下に `timeout-minutes: 30`、`integration-test` の同位置に `timeout-minutes: 60` を追加する。例(lint):

```yaml
  lint:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
```

integration-test:

```yaml
  integration-test:
    needs: lint
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
```

- [ ] **Step 3: integration-test に失敗時 compose ログ収集を追加**

integration-test job の `Integration tests` ステップ(現 ci.yml:105-117)の直後・`Upload reports on failure` の直前に追加:

```yaml
      - name: Dump compose logs on failure
        if: failure()
        run: docker compose logs --no-color > compose-logs.txt 2>&1 || true

      - name: Upload compose logs on failure
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7
        with:
          name: compose-logs-integration-test
          path: compose-logs.txt
```

> 既存の reports 用 upload-artifact と同じ SHA pin を流用する(renovate が一括管理)。

- [ ] **Step 4: コミット**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: 全 job に timeout / docs paths-ignore / integration 失敗時 compose ログ収集を追加"
```

---

## Task D-4: renovate.json から `group:springBoot` を削除

**狙い:** Spring 不使用なのに `group:springBoot` プリセットを継承している(誤継承)。github-actions manager は稼働中(PR #101 実績)なので追加不要。

**Files:**
- Modify: `renovate.json`(現 :3-6 の `extends`)

- [ ] **Step 1: `group:springBoot` を extends から削除**

現 renovate.json:3-6:

```json
  "extends": [
    "config:recommended",
    "group:springBoot"
  ],
```

を次に変更:

```json
  "extends": [
    "config:recommended"
  ],
```

- [ ] **Step 2: JSON 妥当性を確認**

Run: `python3 -c "import json; json.load(open('renovate.json'))" && echo OK`
Expected: `OK`

- [ ] **Step 3: コミット**

```bash
git add renovate.json
git commit -m "ci: renovate の誤継承プリセット group:springBoot を削除(Spring 不使用)"
```

---

## Task D-5: PR テンプレート新設(ラベルドリブン changelog 連動)

**狙い:** `.github/release.yml` がラベルで changelog を分類する。PR 作成時にラベル付与を促すチェックリスト付きテンプレートを追加する。

**Files:**
- Create: `.github/pull_request_template.md`

- [ ] **Step 1: テンプレートを作成**

`.github/release.yml` のカテゴリ(`Impact: Breaking` / `Kind: Feature` / `Kind: Enhancement` / `Kind: Bug Fix` / `Kind: Dependencies` / 除外 `Meta: Release note ignored`)とリポジトリ既存ラベル(`Kind: Refactoring` / `Kind: Tests` / `Kind: Documentation`)に対応させる。

```markdown
## 概要

<!-- 何を・なぜ変更したか。背景や関連する設計判断があれば記載 -->

## 変更内容

<!-- 主な変更点を箇条書きで -->

-

## ラベル(リリースノート分類)

リリースノートは付与ラベルで自動分類される(`.github/release.yml`)。**少なくとも 1 つの `Kind:` ラベルを付ける**こと。

- [ ] `Kind: Feature` — 新機能
- [ ] `Kind: Enhancement` — 機能強化
- [ ] `Kind: Bug Fix` — バグ修正
- [ ] `Kind: Refactoring` — 内部リファクタリング(API 破壊なし)
- [ ] `Kind: Tests` — テスト追加・修正
- [ ] `Kind: Documentation` — ドキュメント
- [ ] `Kind: Dependencies` — 依存更新
- [ ] `Impact: Breaking` — 後方互換が失われる破壊的変更(該当時のみ)
- [ ] `Meta: Release note ignored` — リリースノートに載せない(雑多な内部変更)

## 確認

- [ ] `./gradlew test jvmTest` green
- [ ] 該当する検証(統合テスト / 実描画 eyeball 等)を実施した
```

- [ ] **Step 2: コミット**

```bash
git add .github/pull_request_template.md
git commit -m "docs: ラベルドリブン changelog に対応した PR テンプレートを新設"
```

---

## Task D-6: compose の garage healthcheck / depends_on condition / masterkey env 化

**狙い:** garage に healthcheck がなく `--wait` が ready を保証していない / init の `depends_on` に condition がない / Zitadel masterkey がリテラル。

> **【要注意・実機検証必須】** garage イメージは scratch(shell / wget なし。compose.yml:98 のコメント参照)。**親プランの「wget で :3903 を叩く healthcheck」は実行不能。** 代わりに garage バイナリ(`/garage status`)を healthcheck に使う。これは「ノードが RPC に応答できる=起動済み」を表す。**ただし `garage status` の exit code 挙動と所要時間はクリーン起動で必ず実測すること**(検証 Step で gate)。実測で不適なら fallback として「garage-init の完走を待つ」設計(下記代替案)に切り替える。

**Files:**
- Modify: `compose.yml`(garage サービス :82-93 / zitadel :25-28 / zitadel-init depends_on :72-73 / garage-init depends_on :103-104)

- [ ] **Step 1: garage サービスに healthcheck を追加**

現 compose.yml:82-93 の garage サービス、`restart: "unless-stopped"`(:93)の直前に healthcheck を追加:

```yaml
  garage:
    image: "dxflrs/garage:v2.3.0"
    container_name: "mindstock-garage"
    volumes:
      - "./docker/garage.toml:/etc/garage.toml:ro"
      - "mindstock-garage-meta:/var/lib/garage/meta"
      - "mindstock-garage-data:/var/lib/garage/data"
    ports:
      - "3900:3900"
    # scratch イメージで shell/wget を持たないため、garage バイナリ自身で ready を判定する。
    # /garage status は /etc/garage.toml(mount 済)を読みノードへ RPC して状態を返す。
    healthcheck:
      test: ["CMD", "/garage", "status"]
      interval: "10s"
      timeout: "5s"
      retries: 5
      start_period: "10s"
    restart: "unless-stopped"
```

- [ ] **Step 2: garage-init の depends_on に condition を明示**

現 compose.yml:103-104:

```yaml
    depends_on:
      - garage
```

を次に変更(healthcheck 通過を待つ):

```yaml
    depends_on:
      garage:
        condition: "service_healthy"
```

- [ ] **Step 3: zitadel-init の depends_on に condition を明示**

zitadel には healthcheck がない(本タスクでは追加しない)。`service_started` を明示する。現 compose.yml:72-73:

```yaml
    depends_on:
      - zitadel
```

を次に変更:

```yaml
    depends_on:
      zitadel:
        condition: "service_started"
```

- [ ] **Step 4: Zitadel masterkey を環境変数化(dev 既定値付き)**

現 compose.yml:28:

```yaml
    command: 'start-from-init --masterkey "MasterkeyNeedsToHave32Characters" --tlsMode disabled'
```

を次に変更(compose は単一引用符内でも `${}` を補間する):

```yaml
    command: 'start-from-init --masterkey "${ZITADEL_MASTERKEY:-MasterkeyNeedsToHave32Characters}" --tlsMode disabled'
```

- [ ] **Step 5: compose 構文の妥当性確認**

Run: `docker compose config --quiet && echo OK`
Expected: `OK`(YAML / interpolation エラーがないこと。`ZITADEL_MASTERKEY` 未設定でも既定値で展開される)

- [ ] **Step 6: コミット**

```bash
git add compose.yml
git commit -m "compose: garage に healthcheck(garage status)/ init の depends_on condition 明示 / masterkey を env 化"
```

> **代替案(Step 1 の healthcheck が実機で不適だった場合):** garage の healthcheck は付けず、`mise.toml` / `ci.yml` 側の `docker compose up -d --wait postgres garage` から garage を外し、代わりに `docker compose run --rm garage-init`(alpine + curl で :3903 を実際に叩く)の完走をもって ready とする(現状の起動フローは既に garage-init を foreground 実行している)。この場合 compose の healthcheck 変更は取り下げ、Step 2 の `service_healthy` は `service_started` に下げる。**どちらを採るかは検証セクションの実測で決める。**

---

## Task D-10: garage.toml の rpc_secret に dev-only コメント

**狙い:** ゼロ埋め `rpc_secret` の意図(dev 専用)が toml 側にない(garage-init.sh には説明あり)。

**Files:**
- Modify: `docker/garage.toml:7`

- [ ] **Step 1: rpc_secret 行の直前にコメントを追加**

現 docker/garage.toml:7:

```toml
rpc_secret = "0000000000000000000000000000000000000000000000000000000000000000"
```

を次に変更:

```toml
# dev-only: 単一ノードなので RPC 暗号化は不要。本番では別途秘密値を設定すること。
rpc_secret = "0000000000000000000000000000000000000000000000000000000000000000"
```

- [ ] **Step 2: コミット**

```bash
git add docker/garage.toml
git commit -m "docker: garage.toml の rpc_secret が dev-only である旨をコメント追記"
```

---

## Task D-7: 設定の実態同期(mise コメント / testcontainers イメージ / 廃止 .env.garage 言及)

**狙い:** ドキュメント / コメントが実態とずれている 3 点を直す。

**Files:**
- Modify: `mise.toml:7`
- Modify: `backend/core/build.gradle.kts:61`(testContainersImageName)/ `:42`(description の `.env.garage` 言及)

- [ ] **Step 1: mise.toml の誤コメントを修正**

現 mise.toml:7:

```toml
# Test DB (postgres-test service on port 5433)
```

を実態(`mindstock_test` は postgres:5432 上に `docker/postgres-init.sh` が作成。5433 の postgres-test サービスは存在しない)に修正:

```toml
# Test DB (postgres-init.sh が postgres:5432 上に mindstock_test を作成)
```

- [ ] **Step 2: testcontainers の postgres イメージを compose と統一**

現 backend/core/build.gradle.kts:61:

```kotlin
        testContainersImageName.set("postgres:18.0-alpine")
```

を compose.yml:3(`postgres:18.4-alpine`)と一致させる:

```kotlin
        testContainersImageName.set("postgres:18.4-alpine")
```

- [ ] **Step 3: integrationTest description から廃止済み `.env.garage` 言及を削除**

現 backend/core/build.gradle.kts:42:

```kotlin
    description = "Runs @Tags(\"integration\") specs against the Garage storage in .env.garage (STORAGE_*)."
```

`.env.garage` は廃止済み(STORAGE_* は application.yaml デフォルト or env 上書き)。次に修正:

```kotlin
    description = "Runs @Tags(\"integration\") specs against the Garage storage (STORAGE_* / application.yaml デフォルト)."
```

> 同ファイル :50-52 の `System.getenv` lazy 化(`providers.environmentVariable`)と convention 化はフェーズ 5-5 の担当。**D では触らない**(コンフリクト回避)。

- [ ] **Step 4: ビルド構成が壊れていないか確認**

Run: `./gradlew :backend:core:help --quiet && echo OK`
Expected: `OK`(設定評価が通ること)

- [ ] **Step 5: コミット**

```bash
git add mise.toml backend/core/build.gradle.kts
git commit -m "chore: mise コメント / testcontainers イメージ(18.4)/ 廃止 .env.garage 言及を実態同期"
```

---

## Task D-12: backend のデフォルトポートを 8090 に変更

**狙い:** `PORT` 未設定時のデフォルト 8080 が frontend dev server(8080)と衝突。mise / webpack proxy / README はすべて 8090 前提。デフォルトを 8090 に揃える。

**Files:**
- Modify: `backend/api/src/main/resources/application.yaml:3`(**親プランの「:4」は誤り。実際は 3 行目**)

- [ ] **Step 1: port デフォルトを 8090 に変更**

現 application.yaml:3:

```yaml
    port: "$PORT:8080"
```

を次に変更:

```yaml
    port: "$PORT:8090"
```

- [ ] **Step 2: PORT 未設定で 8090 に立つことを確認(検証セクションでも再確認)**

Run(別ターミナルで依存起動済みが前提。確認だけなら起動ログの port を見る):
`./gradlew :backend:api:run`
Expected: 起動ログに `Responding at http://0.0.0.0:8090`(`PORT` 環境変数未設定時)。

> 注: `mise` 環境下では `mise.toml:6` が `PORT=8090` を注入するため元々 8090。本変更は **`mise` を介さない `./gradlew` 直叩き**(env 未設定)を 8090 に揃えるのが目的。確認は env を明示的に外して行う(`env -u PORT ./gradlew :backend:api:run` 等)。

- [ ] **Step 3: コミット**

```bash
git add backend/api/src/main/resources/application.yaml
git commit -m "config: backend のデフォルトポートを 8090 に変更(frontend dev server 8080 との衝突回避)"
```

---

## Task D-9: .gitignore の残骸削除 + kotlin-js-store の lockfile コミット化

**狙い:** Spring/STS/NetBeans の残骸を削除し、`kotlin-js-store/` の ignore を解除して lockfile をコミット(依存再現性の標準慣行・★決定済み)。

**Files:**
- Modify: `.gitignore`(:1 HELP.md / :9-19 STS / :30-35 NetBeans / :46 kotlin-js-store)
- Create: `kotlin-js-store/yarn.lock`(ビルドで生成してコミット)

- [ ] **Step 1: Spring/STS/NetBeans 残骸を削除**

現 .gitignore は以下を含む(Spring Initializr 由来の残骸):
- `:1` `HELP.md`
- `:9-19` `### STS ###` ブロック(`.apt_generated` / `.classpath` / `.factorypath` / `.project` / `.settings` / `.springBeans` / `.sts4-cache` / `bin/` 等)
- `:30-35` `### NetBeans ###` ブロック(`/nbproject/private/` / `/nbbuild/` / `/dist/` / `/nbdist/` / `/.nb-gradle/`)

これらを削除する。**IntelliJ(`### IntelliJ IDEA ###`)/ VS Code / KMP / AI agents の各ブロックは実使用なので残す。** 削除後の `.gitignore` は概ね次の構成にする(順序は現状維持・該当ブロックのみ除去):

```gitignore
.gradle
.tmp
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/main/**/build/
!**/src/test/**/build/

### IntelliJ IDEA ###
.idea
*.iws
*.iml
*.ipr
out/
!**/src/main/**/out/
!**/src/test/**/out/

### VS Code ###
.vscode/

### AI agents ###
.claude/settings.local.json
.claude/scheduled_tasks.lock

### KMP / Kotlin / local-dev ###
.fleet/
.kotlin/
.DS_Store
**/.DS_Store
Thumbs.db
local.properties
.env
.env.*

# generated by docker/zitadel-init.sh
.env.zitadel
docker/machinekey/
```

> 上記から **`HELP.md`(旧 :1)・STS ブロック・NetBeans ブロック・`kotlin-js-store/`(旧 :46)が消えている**ことを確認。`bin/` とそのネゲート(旧 :17-19)は STS ブロックの一部なので削除(KMP/Gradle で `bin/` は使わない)。

- [ ] **Step 2: kotlin-js-store の lockfile を生成**

ignore 解除後、Kotlin/JS の yarn lockfile を生成する(JS/Wasm 依存解決を一度走らせる):

Run: `./gradlew kotlinUpgradeYarnLock` または `./gradlew :frontend:wasmJsTestNpm` 等の npm 解決を伴うタスク。最も確実なのは:
`./gradlew kotlinWasmStoreYarnLock`

Expected: `kotlin-js-store/yarn.lock`(必要なら `kotlin-js-store/package.json` 関連)が生成される。

> 実際のタスク名は環境差があるため、`./gradlew tasks --all | grep -i yarn` で `kotlinUpgradeYarnLock` / `kotlinWasmUpgradeYarnLock` / `kotlin*StoreYarnLock` のいずれかを確認してから実行する。lockfile が `kotlin-js-store/` 配下に出ることをゴールとする。

- [ ] **Step 3: lockfile が生成されたことを確認**

Run: `ls -la kotlin-js-store/ && git status --short kotlin-js-store/`
Expected: `kotlin-js-store/yarn.lock` が untracked(`??`)で出る。

- [ ] **Step 4: コミット**

```bash
git add .gitignore kotlin-js-store/
git commit -m "chore: gitignore の Spring/STS/NetBeans 残骸を削除し kotlin-js-store lockfile をコミット管理化"
```

---

## Task D-8: README を実態へ書き換え + 環境変数リファレンス新設

**狙い:** README が compose(garage 追加)/ mise タスク / 8090 起動に追随していない。サービス一覧に garage を追加し、起動手順を `mise run up` 正にし、環境変数リファレンスを新設する。**D-9 の lockfile 確定後に行う**(再現性の記述が確定するため)。

**Files:**
- Modify: `README.md`(サービス表 :8-14 / 起動手順 :16-53 / 環境変数セクション新設)
- 参照(コピーしない・要約元): `docs/superpowers/plans/2026-06-12-env-inventory.md`

- [ ] **Step 1: サービス一覧表に garage / garage-init を追加**

現 README.md:8-14 のサービス表(`compose.yml の 3 サービス`)を、実態の 5 サービス(+ init 2)に更新。`zitadel-init` 行は既存。garage 系を追加し、見出しも「3 サービス」→「主なサービス」に修正:

```markdown
`compose.yml` のサービス:

| サービス | 役割 |
|---|---|
| `postgres`(:5432) | アプリ DB(`mindstock`)+ テスト DB(`mindstock_test`)+ Zitadel DB。`docker/postgres-init.sh` が test/zitadel DB を作成 |
| `zitadel`(:8081) | OIDC IdP。Login UI は v1 を使用(v4 既定の v2 は無効化)。初回 init で IAM 管理用 PAT を `docker/machinekey/pat.txt` に発行 |
| `zitadel-init` | 上記 PAT で Management API を叩き、Project / API(JWT)/ PKCE アプリを冪等に作成し `AUTH_*` を repo ルートの `.env.zitadel` に書き出す |
| `garage`(:3900) | S3 互換オブジェクトストレージ(商品画像の保管先) |
| `garage-init` | garage の layout / bucket `mindstock-images` / 固定 dev アクセスキーを冪等にセットアップ(資格情報は `application.yaml` の `external.storage` デフォルトと一致) |
```

- [ ] **Step 2: 起動手順を `mise run up` 正に書き換え**

現 README.md:16-53(「1. 起動 + 自動セットアップ」〜「3. backend + frontend 起動」)を次に置換。`docker compose up -d` 単体は init の完走を待たないため `mise run up` を正とし、backend/frontend も mise タスクで案内する:

```markdown
### 1. 起動 + 自動セットアップ(推奨: mise)

```sh
mise run up
```

`mise run up` は (1) `docker compose up -d --wait postgres zitadel garage` で依存を起動し、(2) `zitadel-init` / `garage-init` を foreground で完走させる。完走すると repo ルートに `.env.zitadel`(`AUTH_*`)が生成される。進捗は `docker compose logs -f zitadel-init`。

> `mise` を使わない場合は `docker compose up -d --wait postgres zitadel garage` の後に `docker compose run --rm zitadel-init` と `docker compose run --rm garage-init` を順に実行する(単に `docker compose up -d` するだけでは init の完走を待たない)。

生成される `.env.zitadel` の中身:

```sh
AUTH_ISSUER=http://localhost:8081
AUTH_JWKS_URL=http://localhost:8081/oauth/v2/keys
AUTH_PROJECT_ID=<自動採番>
AUTH_AUDIENCE=<自動採番: API mindstock-backend の clientId>
AUTH_CLIENT_ID=<自動採番: PKCE アプリの clientId>
AUTH_REDIRECT_URI=http://localhost:8080/auth/callback
AUTH_POST_LOGOUT_REDIRECT_URI=http://localhost:8080/
```

### 2. 環境変数の読み込み

`mise` 利用なら `mise.toml` の `_.file = ".env.zitadel"` で自動読み込みされる。使わない場合は各ターミナルで:

```sh
set -a; . ./.env.zitadel; set +a
```

backend / frontend の両方が `AUTH_*` を要求する(未設定だと `:frontend:generateAuthConfig` がビルド失敗=意図的)。DB / Storage は `application.yaml` の既定が compose と一致するため通常未設定で可。

### 3. backend + frontend 起動

```sh
mise run backend     # ターミナル A(:8090。Flyway migration が走る)
mise run frontend    # ターミナル B(http://localhost:8080。--continuous 付き)
```

`mise` を使わない場合(`./gradlew` 直叩き):

```sh
./gradlew :backend:api:run                                    # :8090(application.yaml の PORT デフォルトが 8090)
./gradlew :frontend:wasmJsBrowserDevelopmentRun --continuous  # http://localhost:8080(/api を :8090 へプロキシ)
```

ブラウザで http://localhost:8080 を開く → Zitadel ログイン(**`admin@localhost` / `Password1!`**)。
```

> **注意(D-12 と整合):** backend デフォルトポートは **8090**(本 PR で変更)。frontend dev server(8080)が `/api` を :8090 へプロキシする(`frontend/webpack.config.d/proxy.js`)。旧 README の「backend は既定 :8080 だが PORT=8090 で起動」という記述は不要になるので残さない。

- [ ] **Step 3: 「環境変数リファレンス」セクションを新設**

README 末尾(「再セットアップ / 注意」の前後)に新セクションを追加。**`2026-06-12-env-inventory.md` を要約**(全文コピーはしない。開発者が「何を設定すべきか」を一覧できることがゴール)。死変数(`KTOR_ENV` 等)は本 PR 同梱の他フェーズで消えるため載せない:

```markdown
### 環境変数リファレンス

ローカル開発では基本的に `mise run up` が生成・注入するため手動設定は不要。全体像は以下。詳細インベントリは `docs/superpowers/plans/2026-06-12-env-inventory.md`。

| 変数 | 用途 | 既定 / 供給元 | 手動設定 |
|---|---|---|---|
| `AUTH_ISSUER` / `AUTH_JWKS_URL` / `AUTH_AUDIENCE` | backend の JWT 検証 | `.env.zitadel`(`mise run up` が生成) | 不要(未設定だと起動時に fail-fast) |
| `AUTH_CLIENT_ID` / `AUTH_PROJECT_ID` / `AUTH_REDIRECT_URI` | frontend の PKCE ログイン(ビルド時定数) | `.env.zitadel` | 不要 |
| `PORT` | backend の待受ポート | 既定 `8090`(`application.yaml`)。`mise` も 8090 を注入 | 不要 |
| `DB_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD` | backend の DB 接続 | `application.yaml` 既定が compose と一致 | 任意上書きのみ |
| `STORAGE_ENDPOINT` / `STORAGE_REGION` / `STORAGE_BUCKET` / `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` / `STORAGE_CORS_ORIGINS` | 商品画像ストレージ(garage) | `application.yaml` 既定が garage-init の固定 dev キーと一致 | 本番のみ上書き |
| `TEST_DB_URL` / `TEST_DB_USER` / `TEST_DB_PASSWORD` | 統合テストの DB | `mise.toml` / CI が供給(`mindstock_test`) | 不要 |
| `ZITADEL_MASTERKEY` | Zitadel の masterkey | 既定 `MasterkeyNeedsToHave32Characters`(`compose.yml`) | 本番のみ上書き |
```

> 環境変数の値や読む側コードの座標は本 PR 同梱の他フェーズ(0-1 死変数削除 / 2-8 fail-fast / 0-12 死定数削除)でも動く。**ただし D はコードに触らないため、README には「現状動く範囲の要約」だけを書き、フェーズ 2 完了後に fail-fast 文言を追従させる**(本 PR では「未設定だと起動時に fail-fast」を将来形でなく現状寄りに書きすぎない)。実装時、2-8 が未マージなら「未設定だと JWT 検証で失敗」と表現する。

- [ ] **Step 4: README に書いた手順をそのまま実行して再現確認**

検証セクションのクリーン起動で README 記載の `mise run up` → `mise run backend` → `mise run frontend` をそのままなぞって齟齬がないこと。

- [ ] **Step 5: コミット**

```bash
git add README.md
git commit -m "docs: README をサービス実態(garage 追加)/ mise 起動手順 / 環境変数リファレンスに同期"
```

---

## フェーズ検証(全タスク完了後・親プランの検証要件)

- [ ] **クリーン起動で全サービス healthy:**
  ```sh
  docker compose down -v
  mise run up
  ```
  全サービスが healthy になり `zitadel-init` / `garage-init` が完走、`.env.zitadel` が生成されること。**特に D-6 の garage healthcheck(`/garage status`)が機能して `--wait` が garage ready を待つことを実測**(`docker compose ps` で garage が healthy)。不適なら D-6 の代替案(garage-init 完走を ready とする)に切替。

- [ ] **PORT 未設定で 8090 に立つ:**
  ```sh
  env -u PORT ./gradlew :backend:api:run
  ```
  起動ログが `http://0.0.0.0:8090`。

- [ ] **統合テスト green:**
  ```sh
  ./gradlew :backend:api:integrationTest :backend:core:integrationTest
  ```
  (依存起動済みが前提)

- [ ] **CI を draft PR で一巡:** 全 4 job green。特に新規 **wasm 本番ビルド job の所要時間とメモリ**を確認(OOM なら D-1 Step 2 のフラグを付与)。`paths-ignore` が docs-only コミットで job を skip すること、`timeout-minutes` が効いていること、setup-java が JDK 25 を入れていることを確認。

- [ ] **renovate.json の妥当性:** `python3 -c "import json; json.load(open('renovate.json'))"` が成功。

- [ ] **削除残骸の grep:** `git grep -n "springBoot" -- renovate.json`(0 件)/ `git grep -nE "HELP\.md|\.springBeans|nbproject" -- .gitignore`(0 件)/ `git grep -n "18.0-alpine"`(0 件)/ `git grep -n "\.env\.garage" -- backend/`(0 件)。

**規模目安:** 設定 / docs 約 14 ファイル・±180 行。リスク: 低(CI と compose の変更は draft PR とクリーン起動で検証してからマージ)。

---

## 親プランからの逸脱・判断ログ(レビュー時の論点先回り)

実装 PR の説明にも転記すること:

1. **D-2 は「削除」から「コメント明示」に変更。** `test jvmTest --dry-run` で両タスクが対象モジュール分離(重複でない)と実測。`jvmTest` 削除は domain/rpc/shared のテスト消失を招くため不採用。
2. **D-6 の healthcheck は wget → `/garage status` に変更。** garage は scratch イメージで wget 不可。実機検証で不適なら代替案(garage-init 完走を ready とする)に切替。
3. **D-12 の座標修正。** `application.yaml` の `$PORT` は 4 行目でなく **3 行目**。
4. **D-4 の github-actions manager 追加は不要と確定。** renovate が現に action digest 更新 PR(#101)を出している。`group:springBoot` 削除のみ実施。
5. **フェーズ境界の厳守:** D-7 で触れた `backend/core/build.gradle.kts` の `System.getenv` lazy 化 / convention 化は**フェーズ 5-5 の担当**であり D では触らない(コンフリクト回避)。D-8 の環境変数リファレンスの fail-fast 文言は**フェーズ 2-8 未マージ時は現状寄りに表現**する。
