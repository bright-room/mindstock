# コードベース全体リファクタリング マスタープラン

> **For agentic workers:** これは 6 フェーズのマスタープラン。各フェーズの着手時に superpowers:writing-plans でフェーズ詳細プラン(テスト・コード付きのタスク分解)を起こし、superpowers:subagent-driven-development で実行する。本書はフェーズ間の順序・スコープ・判断の正本。

**Goal:** 監査で確定した約 80 件の指摘(高 11・中 26・低 44)を、挙動無風な掃除 → 原則適合 → 構造是正 → テスト/ビルド補強の順で、フェーズごとに独立マージ可能な PR として解消する。

**Architecture:** 既存アーキテクチャ(presentation → application ← infrastructure / リッチドメイン / 単一 `/api/rpc` + アプリ層認可)は維持。変更は「原則への適合」と「重複・漏出の解消」であり、再設計はしない。

**Tech Stack:** Kotlin Multiplatform / Ktor / kotlinx-rpc / Exposed / Compose Multiplatform(Wasm)

---

## 監査サマリ(2026-06-12 実施・全 2 回)

**第 1 回(Kotlin コード)**: 7 体の調査エージェント(domain / backend:core / backend:api / frontend 基盤 / frontend 機能 / rpc+shared / テスト+ビルド)による全 416 ファイル・約 24,000 行の監査。
**第 2 回(開発基盤+ルール)**: 4 体の調査エージェント(.github CI/CD / docker・compose・mise 等のローカル開発基盤 / backend 系ルール+CLAUDE.md / frontend 系ルール)による監査。第 2 回の主な発見:

- **CI の穴**: frontend の wasm **ビルド**が CI で未検証(テストのみ)/ 全 job に timeout なし / paths フィルタなしで docs 変更でも全 job 実行 / renovate に `group:springBoot` プリセットが混入(Spring 不使用)
- **ローカル基盤の綻び**: README が compose.yml(garage 追加)と mise タスクに追随していない / `mise.toml` コメントと実値の矛盾 / testcontainers の postgres 18.0 vs compose の 18.4 / garage に healthcheck なしで `--wait` が機能していない / `kotlin-js-store` lockfile が gitignore されて再現性なし
- **ルールの乖離(最重要)**: ルール内サンプルが実 API と食い違い、**コピペするとコンパイルエラーになる箇所が 3 つ**(`Uuid.uuidv7()`→実際は `generateV7()` / `this.applicationCall` extension→実際は `sessionOf(call)` / `userMessageOf()`→実際は `errorText()`)。designsystem ルールの atom 一覧が 6 個のまま(実在 25 個)。「P6-1 で配線予定」等の完了済み計画文言が残存
- **ルールの不足**: 統合テスト規約・runGuarded の例外→RpcError 翻訳マップ・UiText/errorText・suspend fun 公開 VM 規約・webMain ソースセット・Controller 三点セット(Reauth/Toast/Refresh)が未文書化

**第 3 回(Gradle スクリプト深掘り+環境変数フロー+ツールチェーン一貫性)**: 3 体の調査エージェントによる監査(環境変数 36 変数の全数インベントリ作成済み)。主な発見:

- **Gradle の正しさ**: `integrationTest` タスク内の `System.getenv()` が configuration phase で実行され、有効化済みの configuration cache を不必要に無効化しうる(core/api 両方)。しかも core と api の `integrationTest` タスク定義はほぼ完全コピペ。さらに **`:backend:api:integrationTest` は `@Tags("integration")` のテストが 1 件もなく現状空実行**(裏取り済み)
- **未使用依存(裏取り済み)**: `navigation-compose`(NavHost 等の参照ゼロ)/ `compose-ui-tooling-preview`(@Preview 参照ゼロ)/ domain・rpc の `jvmTest` ソースセット向け `kotest-runner-junit5`(ソースセット自体が不存在)/ backend/api の flyway・postgres-jdbc の `testImplementation` 重複(implementation から推移する)
- **環境変数の穴**: backend の `AUTH_ISSUER`/`AUTH_AUDIENCE`/`AUTH_JWKS_URL` にデフォルトがなく、未設定時は起動が通って JWT 検証で深く死ぬ(fail-fast なし)/ `KTOR_ENV` は読む側ゼロの死変数(Environment.kt 削除と対)/ frontend の `AuthConfig.AUDIENCE`・`POST_LOGOUT_REDIRECT_URI` は生成されるが参照ゼロ / `PORT` デフォルト 8080 が frontend dev server と衝突(mise は 8090 を注入するが Gradle 直叩きで混乱)/ 「設定すべき env の全リスト」を得られるドキュメントが存在しない
- **CI のツールチェーン暗黙依存**: `setup-java` がなく ubuntu-latest のプリインストール JDK 25 に暗黙依存(ランナー更新で壊れるリスク)

**正直な総評: コードベースは「大量の無駄」の状態にはない。** 確定デッドコードは約 180 行(全体の 1% 未満)で、層構造・@Rpc 規約・VO 規律・認可の単一化は概ね守られている。価値が大きいのは以下の 4 領域:

1. **原則違反の残党** — primitive 引数(`limit: Int`, `wanted: Boolean`, `current: Int` × 4)、`id` public とドメイン内 id 比較、1 ファイル 3 型
2. **ロジック漏出** — ShoppingList 構築が application 層、activity の整形が presentation 層
3. **frontend の構造負債** — App.kt 695 行への集中、`handleFailure` 7 VM コピペ、エラー二重表示
4. **テスト空白 + ビルドの罠** — DataSource 層テストゼロ、`check` が integrationTest を巻き込む

**監査で確定した実バグ/リスク(最優先で認識すべきもの):**
- `HouseholdCapability.マスタ管理` が定義のみで、商品マスタ編集(changeUnit/changeMinimum/archive 等)に認可チェックが一切ない(docs/spec は「マスタ編集は `マスタ管理`」と規定)→ **認可漏れ**。フェーズ 2 で実装
- `tasks.check` → `integrationTest` 依存により、DB なし環境で `./gradlew build` が必ず赤になる → フェーズ 0 で除去

---

## 決定事項(確認したい点は ★)

- **`id` public の扱い(ユーザ回答済み 2026-06-12)**: ルール自体が形骸化しているため**廃止**。`domain-guideline.md` の原則 4(`id` は private)を削除し、`id` は public のまま、ドメイン内 id 比較も現状許容。**コード変更なし**(旧タスク 2-5 は欠番)
- **`マスタ管理` capability(ユーザ回答済み 2026-06-12)**: **認可チェックを実装で確定**(docs/spec と一致させる)。挙動変更(メンバー権限ではマスタ編集不可になる)を含むため、フェーズ 2 PR で明示
- **`:backend:schedules`**: プレースホルダのまま**維持**(トラック B gap #4「通知」のバッチで使用予定のため削除しない)
- **トラック B(occurredAt / 消費予測 / 通知)との順序**: domain/application を触るフェーズ 0〜2 を先に完了させてからトラック B を再開する(コンフリクト回避)
- **primitive 許容のルール改訂案は不採用**: ルール監査員は「`limit: Int` / `wanted: Boolean` はルール例外として許容」を提案したが、VO 原則をドキュメント緩和で逃げない方針(working agreement)に従い**フェーズ 1 の VO 化を維持**。ただし**述語の Boolean 戻り値**(`usable()` / `isBelow()` / `existsByJan()` 等)は VO 原則の対象外(データ値でなく判定結果)としてルールに明記する
- **区分(enum)の判定はカプセル化する(ユーザ判断 2026-06-12)**: 区分の状態判定は区分内の述語メソッド(`ProductStatus.isアーカイブ済()` / `InvitationValidity.is有効()` 等)で表現し、呼び出し側で `status == アーカイブ済` のような **外部での `==` 比較(外側で判定)はしない**(tell-don't-ask)。**参照ゼロでも区分の意図を表す述語メソッドは死コード扱いで消さない**。これにより 0-4 / 0-5(述語削除案)は不採用。**フェーズ R で `domain-guideline.md` に明文化**(述語の Boolean 戻り値が VO 原則対象外である点と併記)
- **ルール整備(フェーズ R)はフェーズ 1 より先に完了させる**: ルールは編集時に自動ロードされ、以降のフェーズの実行エージェントが誤サンプル(コンパイル不能なコード例)を踏むため
- **`kotlin-js-store` lockfile はコミット管理に切替**(ignore 解除)。Kotlin/JS の依存再現性を担保する標準慣行
- **Zitadel masterkey は環境変数化**(dev デフォルト値付き `${ZITADEL_MASTERKEY:-...}`)。本番流用を構造的に阻止する
- **`integrationTest` タスクは convention 化して維持**: core/api のコピペ定義を `kotlin-jvm` convention に統合し、`System.getenv` を `providers.environmentVariable` に置換(configuration cache 対応)。api 側は現状テスト 0 件の空実行だが、フェーズ 3 以降の e2e 受け皿としてタスクは残す(空実行であることをコメント明示)
- **backend の AUTH_* は起動時 fail-fast 化**: デフォルト値は与えない(誤った既定値で動く方が危険)。未設定なら起動時に明示エラーで死ぬガードを追加
- **`PORT` のデフォルトは 8090 に変更**: frontend dev server(8080)との衝突を排除し、mise・README・webpack proxy(8090)と一致させる
- **frontend の `AuthConfig.AUDIENCE` / `POST_LOGOUT_REDIRECT_URI` 生成は削除**: 参照ゼロの死定数。ログアウト機能実装時に `endSessionUrl()` ごと再追加する(`.env.zitadel` の生成自体は backend が AUTH_AUDIENCE を読むため維持)
- 各フェーズ = 1 ブランチ 1 PR。フェーズ内は小さくコミット

## 見送り(やらないと決めたもの)

- `AppSession.State` の sealed 化(nullable 4 フィールド) — 大規模になるため**既知債務として記録のみ**(docs/known-issues に追記)
- JWKS フェッチ失敗と改ざんの 401 区別(503 化) — 現設計の許容範囲。ログ文脈追加のみフェーズ 2 に含める
- `DateChip`/`SuggestionChips` の共通 pill プリミティブ抽出 — 投機的抽象のため見送り
- `Archivability.在庫あり` の命名変更 — 「なぜアーカイブ不可か」を表す設計意図として現状維持
- Controller の薄い委譲テストの削除 — 削除せず、testing.md に「Controller にロジックがなければテスト不要」の方針を追記(フェーズ R)
- `upload-artifact # v7` の SHA/バージョン表記疑義 — **誤検出と判定**(調査員の知識切れ。renovate がコメント込みで SHA pin を管理しており整合)
- CI の `lint` → `test-*` 直列(`needs: lint`) — lint 失敗時の無駄実行防止という意図的設計として維持
- `dependabot.yml` 追加 — 不要(renovate の `config:recommended` が github-actions manager をデフォルトで管理。直近の renovate PR で action SHA 更新が来ている実績だけフェーズ D で確認)
- `SECURITY.md` / job レベル permissions 明示 — 公開運用が視野に入った時点で。今はやらない
- `gradle.properties` の `org.gradle.workers.max=4` — 現状妥当として維持
- `SettingsScreen`(app/settings)と `SettingsViewModel`(feature/household)のパッケージ分散 — コード移動はせず、フェーズ R で「`app/` 層の位置付け」をルールに明記して解消
- `ktorLib` 外部 version catalog(`io.ktor:ktor-version-catalog:3.5.0`、settings.gradle.kts:32-34)のインライン化 — 現状維持。Ktor 公式の管理方式として妥当。renovate が追従しているかだけ D-4 の実績確認に含める
- Node/Yarn バージョンの明示固定 — Kotlin ツールチェーン内包の自動解決で十分。lockfile コミット(D-9)で再現性は担保される
- ログアウト機能(`endSessionUrl` の配線) — リファクタリングではなく機能追加。死定数の掃除(0-12)のみ行い、実装はプロダクト判断に委ねる

---

## フェーズ R: ルール・ドキュメント整備(docs のみ・フェーズ 0 と並行可、フェーズ 1 着手前に完了必須)

**狙い:** `.claude/rules/` と CLAUDE.md を実コードに同期させる。ルールは編集時に自動ロードされ後続フェーズの実行品質を直接左右するため、**コード変更より先に**正す。原則: コードが正しくルールが古い箇所はルールを直す。コードを直す予定の箇所(フェーズ 1〜5 対象)はルールを先回りで書き換えず、該当フェーズ完了時に追従させる。

| # | 対象 | 内容 |
|---|------|------|
| R-1 | **コピペでコンパイルエラーになるサンプル 3 件(最優先)** | `domain-guideline.md:54,132` の `Uuid.uuidv7()` → 実 API `Uuid.generateV7()` / `backend-rpc-and-transactions.md:25` の `this.applicationCall` extension(不存在)→ 実装 `sessionOf(call)`(SessionAccess.kt:8)/ `frontend-rpc-and-error.md:13` の `userMessageOf()` → 実名 `errorText()` |
| R-2 | `domain-guideline.md` | 原則 4「`id` は private」(:28)とサンプル中の `private val id`(:108,119)を**削除**(形骸化のため廃止が決定。`id` は public が正)/ 許容ライブラリリスト(:14-19)に `org.kotlincrypto.random` を追加(InvitationCode で使用中)/ sealed 項(:88-91)に `@JvmInline` variant + `@Serializable` の polymorphic 破壊の罠を追記 / 「述語メソッドの Boolean 戻り値は VO 原則の対象外」を明記 |
| R-3 | `error-handling.md` | 「不在を別戦略のトリガーにする catch(master 不在→外部 API フォールバック、CatalogService.kt:27-34)は許容」を追記 / configuration 層(MindstockAuthPlugin)での例外吸収の扱いを明記 / `private fun` の nullable 戻り値(クラス内 sentinel 用途)の許容を明記 |
| R-4 | `backend-software-architecture.md` | Hydration 命名(:68)に「複数テーブル組み立ては `assemble<Aggregate>(...)` 形式も許容」を追記 / DataSource 節に `Created.now()` 慣行(tx 内で取得)を追記 / `Handler` 廃止記述(:101)を削除 |
| R-5 | `backend-rpc-and-transactions.md` | `UserPublicRpcService` の例示(:19)を実状「`allowUnregistered` / `requireRegistered` ガードで同一 interface 内で表現」に修正 / `runGuarded` の例外→RpcError 翻訳マップ(IAE→BadRequest / ResourceNotFound→NotFound / OwnerRequired→Unauthorized / LastOwner→Conflict 等。SessionGuard.kt が正)を追記 / `correct` の `OccurredAt.now()` サーバ生成は現仕様と注記(トラック B occurredAt 対応で見直し予定) |
| R-6 | `testing.md` | `RolePermissions.allows(...)` static サンプル(:61)をインスタンス形式に修正 / 統合テスト規約(`@Tags("integration")` + FunSpec + `TEST_DB_*` 環境変数)を追記 / Scenario・Service テスト方針(FunSpec + mockk で Repository をスタブ)を追記 / Controller テスト方針(ロジック=ガード分岐・例外翻訳がなければテスト不要、追加時に同時に書く)を追記 |
| R-7 | `frontend-designsystem.md` | atom 一覧(:13。6 個記載 vs 実在 25 個)を「一覧はコードが正(`designsystem/atom/` 参照)+ 代表例数個」方式に変更 / `NavigationSuiteScaffold` の言及(:14)を削除し「独自 `WideShell` / `BottomNav`」に修正 / シェル層での `MaterialTheme` 基本 API 直接使用を例外として明記 / `MindstockTokens` / `MindstockType` / `Shadow` / `avatarColorOf` のテーマ拡張群の使い方を追記 |
| R-8 | `frontend-rpc-and-error.md` | 「P6-1 で配線予定」「`closeAll()` 用意済み」等の完了済み計画文言を現状(ReauthController→App.kt LaunchedEffect→`rpc.close()`+再認証)に更新 / `RpcOutcome` / `toOutcome()` 変換層を追記 / `handleFailure` パターンはフェーズ 4-4 の共通化完了後に追従記載 |
| R-9 | `frontend-architecture.md` / `frontend-kmp-structure.md` / `frontend-i18n-and-font.md` / `frontend-compose-conventions.md` | VM への依存注入は「Repository の関数参照を渡すスタイル」と明記(:25 の乖離修正)/ `app/` 層の位置付け(App.kt=配線ホスト、世帯横断画面の置き場)を明記 / `webMain` ソースセット(js+wasmJs 共通のブラウザコード置き場、`kotlinx.browser.window` 許容)を追記 / expect/actual 一覧に `PreferenceStore` / `pickImage` を追加 / i18n「暫定例外」を現状(UiText 化完了。`RpcError.Internal("...")` の英語識別子は対象外)に更新 / `UiText` / `errorText` パターンを追記 / `suspend fun` 公開 VM 規約(VM 内で launch しない)を追記 / Controller 三点セット(Reauth/Toast/InventoryRefresh)の責務と配線を追記 / `?preview` ハーネスの再追加手順(webMain PreviewHarness.kt + Main.kt `?preview=` 分岐)と忠実化検証ループへの参照を追記 |
| R-10 | `CLAUDE.md` | `:backend:core` の説明に Scenario を追加(「Repository / Service / Scenario interface」)/ 統合テストコマンドの表記を実態確認の上修正(`./gradlew integrationTest` か `:backend:api:integrationTest` か) |
| R-11 | 軽微な明確化(まとめて) | `domain-one-class-per-file.md` の適用範囲を domain 限定と明記(infrastructure は対象外)/ `domain-immutable-construction.md` に小エンティティ(`HouseholdMember.withRole` 等)も同ルールに服する旨を明記 / `frontend-i18n-and-font.md` に時刻フォーマット(`hm()` 等の locale 非依存表記)は strings 対象外と明記 / `frontend-kmp-structure.md:21` の Pkce 例示パスを実ファイル構成に合わせ修正 |

**検証:** ルール中の全コード例を実コードと突き合わせ(コピペでコンパイル可能か)。file:line 参照の実在確認。
**規模目安:** docs のみ 13 ファイル。リスク: ゼロ。

---

## フェーズ 0: 無風の掃除(挙動変更なし・即マージ可)

**狙い:** デッドコード・ドキュメントずれ・ビルドの罠を先に消し、以降のフェーズの diff を綺麗にする。

| # | 対象 | 内容 |
|---|------|------|
| 0-1 | `backend/api/.../configuration/Environment.kt` + `application.yaml:2` | enum 全 13 行削除(参照ゼロ確認済)。併せて `application.yaml` の `environment: "$KTOR_ENV:LOCAL"` 行も削除(読む側ゼロの死変数) |
| 0-2 | `frontend/.../core/navigation/Route.kt` | sealed interface Route 全 14 行削除(タブ切替は AppShell の Tab enum で完結。参照ゼロ確認済) |
| 0-3 | `frontend/.../feature/inventory/ui/StockHomePreview.kt` | ファイルごと削除(111 行。ハーネス撤去時の取り残し。参照ゼロ確認済) |
| 0-4 | (不採用) | `isアーカイブ済()` 削除案は**撤回**(2026-06-12 ユーザ判断)。区分の判定は区分内にカプセル化し外側で `==` 比較しない方針。参照ゼロでも現状維持。コード変更なし |
| 0-5 | (不採用) | `is有効()` のインライン削除案は**撤回**(0-4 と同方針)。`InvitationValidity.is有効()` と `Invitation.usable()=validity.is有効()` を現状維持。コード変更なし |
| 0-6 | `shared/.../extensions/kotlinx/datetime/LocalTime.kt` | `LocalTime.now()` 8 行削除(全モジュール参照ゼロ) |
| 0-7 | `backend/core/.../StockRegisterRepository.kt` + `StockRegisterDataSource.kt` | `appendMovement` 戻り値を `Unit` 化し `rebindIdentity`(約 20 行)を削除。呼び出し元 3 箇所(StockRegisterService.kt:39,53,67)は全て戻り値無視を確認済 |
| 0-8 | `backend/api/build.gradle.kts:86-88` | `tasks.check { dependsOn(integrationTest) }` を削除(integrationTest は CI job と明示実行のみに) |
| 0-9 | `Makefile` | 削除して mise.toml に一本化(README に `mise run up` 等を記載) |
| 0-10 | `frontend/build.gradle.kts:21` + `gradle/libs.versions.toml:65` | 未使用依存 `material3-adaptive-navigation-suite` を削除(`NavigationSuiteScaffold` は不使用、シェルは独自実装。`compose.adaptive` は `currentWindowAdaptiveInfo` で使用中のため**残す**) |
| 0-11 | 未使用依存の一掃(全て参照ゼロ裏取り済み) | `navigation-compose`(frontend/build.gradle.kts:22 + libs.versions.toml:66,81)/ `compose-ui-tooling-preview`(frontend/build.gradle.kts:24)/ domain・rpc の不存在 `jvmTest` ソースセット向け `kotest-runner-junit5` 宣言(domain/build.gradle.kts:18、rpc/build.gradle.kts:27)/ backend/api の `flyway-core`・`flyway-database-postgresql`・`postgres-jdbc` の `testImplementation` 重複宣言(:50-51。implementation から推移するため不要。`testFixturesImplementation` は隔離のため残す) |
| 0-12 | frontend の死定数 AuthConfig 生成 | `generateAuthConfig`(frontend/build.gradle.kts:56,58)から `AUDIENCE` / `POST_LOGOUT_REDIRECT_URI` の生成を削除し、未呼出の `AuthClient.endSessionUrl()` も削除。CI の `AUTH_AUDIENCE: ci-placeholder`(ci.yml:73)も不要になるため削除(ログアウト実装時にセットで再追加) |

**検証:** `./gradlew build`(DB なしで通ること=0-8 の確認を兼ねる)+ `:frontend:compileKotlinWasmJs`、削除シンボルの `grep` 残参照ゼロ。
**規模目安:** 約 -250 行。リスク: 最小。

---

## フェーズ 1: 原則適合スイープ(primitive→VO・型/ファイル規約)

**狙い:** 「公開 API は VO」「1 ファイル 1 型」「nullable/!! 禁止」の残違反を全層で一掃する。機械的だが RPC interface に触るため frontend/backend を同時に変える。

| # | 対象 | 内容 |
|---|------|------|
| 1-1 | `domain/.../stock/StockStatus.kt:12` | `of(current: Int, ...)` → `of(current: NetQuantity, ...)`。`Stock.status()` の `currentQuantity()()` 二段変換を解消 |
| 1-2 | `domain/.../stock/Archivability.kt:11` | `of(currentQuantity: Int)` → `of(currentQuantity: NetQuantity)` |
| 1-3 | `domain/.../product/setting/MinimumStock.kt:15-17` | `isBelow(current: Int)` / `shortage(current: Int)` → `NetQuantity` 受け。**併せて `ShoppingListScreen.kt:297` の独自計算(`max(1, min - qty + ...)`)を domain の `shortage()` 使用に置換**(ロジック一元化。deadcode 解消も兼ねる) |
| 1-4 | 新 VO `SearchLimit`(`:domain`) | `CatalogRpcService.search(name, limit: Int)` の `limit` を VO 化。`init { require(value in 1..100) }`。波及: CatalogRpcService / CatalogController / CatalogService / CatalogRepository(backend) / CatalogDataSource / CatalogRepository(frontend) / AddProductViewModel の 7 ファイル |
| 1-5 | 新 VO `Wanted`(`:domain`) | `setWanted(productId, wanted: Boolean)` と `ShoppingEntry.manuallyWanted: Boolean` を `Wanted` VO に統一。波及: ProductRegisterRpcService / Controller / Service / Repository / DataSource / frontend InventoryRepository |
| 1-6 | `domain/.../resident/identity/auth/AuthIdentity.kt` | `AuthProvider` / `AuthSubject` を各 1 ファイルに分割(1 ファイル 1 型) |
| 1-7 | `domain/.../household/Profile.kt` / `domain/.../resident/profile/Profile.kt` | 同名 `Profile` の衝突を解消 → `HouseholdProfile` / `ResidentProfile` に改名 |
| 1-8 | テキスト VO 5 件(DisplayName / HouseholdName / ProductName / CatalogItemName / ProductUnit) | 重複バリデーション(trim+length パターン約 75 行)を `:domain` 内 internal 拡張関数 `String.requireTrimmedWithin(max)` に集約 |
| 1-9 | `backend/core/.../StockHydration.kt:39-40` | Correction ブランチの `!!` × 2 を明示例外(`?: throw` + 不整合メッセージ)に置換 |
| 1-10 | `shared/.../Json.kt` | `KrpcJson` に `namingStrategy = null` を明示(kRPC 内部型の SnakeCase 巻き込み防止)、`CustomJson` の `prettyPrint = false` 化 |
| 1-11 | RPC 命名統一 | `registerDisplayName` → `register`、`imageUrl` → 動詞形 or KDoc 明示(rpc + Controller + frontend Repository 各 6 行程度) |

**検証:** `./gradlew test` / `./gradlew integrationTest`。既存 domain テスト(約 110 関数)が安全網。
**規模目安:** 約 20 ファイル・±300 行。リスク: 低(シグネチャ変更はコンパイラが波及先を保証)。

---

## フェーズ 2: ドメインロジック引き込み・層責務是正(挙動変更 1 件含む)

**狙い:** リッチドメイン原則の実質化。application/presentation に漏れたビジネスロジックを domain に引き込み、認可漏れを塞ぐ。

| # | 対象 | 内容 |
|---|------|------|
| 2-1 | `domain/.../stock/Stocks.kt` + `backend/core/.../ProductService.kt:62-68` | `Stocks.buildShoppingList(wantedProductIds: Set<ProductId>): ShoppingList` をドメインメソッド化し、ProductService の合成ロジックを移管。`Stocks`/`Products` が `size()` しか持たない貧血状態の是正起点 |
| 2-2 | `backend/api/.../StockController.kt:23-29` + `StockService` | flatten + sortedByDescending の整形ロジックを application 層へ。`StockService.activity(): ActivityFeed` を返す形に格上げし Controller は橋渡しのみに(backend:core と backend:api 両監査で同一指摘) |
| 2-3 | `backend/api/.../HouseholdController.kt:22-27` | `previewInvite` の 2 Service 直列呼び出しを `PreviewInviteScenario` に抽出(「複数 Service またぎは Scenario」規約) |
| 2-4 | **認可漏れ修正** `HouseholdCapability.マスタ管理` | ProductRegisterService の changeUnit / changeMinimum / archive / unarchive / 画像変更等に `Household.requireCapability(マスタ管理)` チェックを追加。**挙動変更: メンバー権限はマスタ編集不可になる(docs/spec 通り。ユーザ承認済み)**。Service テスト+統合テストを同時追加 |
| 2-5 | (欠番) | `id` ルール廃止の決定により削除(ドメイン内 id 比較は現状許容。コード変更なし) |
| 2-6 | `backend/core/.../HouseholdHydration.kt` / `InvitationHydration.kt` | コンストラクタ呼ぶだけの no-op ラッパー関数を削除し呼び出し元へインライン化(Hydration パターンは実マッピングを持つものだけ残す) |
| 2-7 | `backend/api/.../SessionGuard.kt:82` | unhandled exception ログに `authSubject` / `residentId` の構造化フィールドを追加(追跡性) |
| 2-8 | **AUTH_* の起動時 fail-fast ガード** | `application.yaml:15-17` の `AUTH_ISSUER` / `AUTH_AUDIENCE` / `AUTH_JWKS_URL` はデフォルトなしのため、未設定だと起動が通って JWT 検証で深く死ぬ。configuration 読み込み時に `requireNotNull` + 「`.env.zitadel` を生成したか(`mise run up`)」を案内する明示エラーで即死させる |

**検証:** `./gradlew test integrationTest`。2-4 は「オーナー可・メンバー不可」の双方向テスト必須。
**規模目安:** 約 15 ファイル・±250 行。リスク: 中(2-4 のみ挙動変更。PR 説明に明記)。

---

## フェーズ 3: infrastructure 品質(テスト安全網 → 性能)

**狙い:** テストゼロの DataSource 層に安全網を張ってから、抱え持ちの N+1 を解消する。**順序厳守(テストが先)。**

| # | 対象 | 内容 |
|---|------|------|
| 3-1 | **先行** DataSource 統合テスト追加 | Hydration 経路(ResultRow → 集約)の round-trip テストを Stock / Household / Product / Invitation の 4 系統に追加(既存 `ProductImageTransferTest` の testcontainers 構成に倣う)。save → load → 集約一致を検証 |
| 3-2 | `backend/core/.../StockDataSource.kt:56-63` | `listByHousehold` の per-product `loadMovements`(2N+1)を movements 一括取得 + groupBy に置換。TODO コメント(P5 送り)を完済 |
| 3-3 | `backend/core/.../HouseholdDataSource.kt:44-46` | `listByResident` の per-household hydrate(1+3N)を一括取得に置換 |
| 3-4 | `backend/core/.../HouseholdRegisterDataSource.kt:69,86,102` | 3 メソッドで重複する `HouseholdMembershipEventsTable.insert` を private helper `insertMembershipEvent(householdId, residentId, role, status)` に共通化 |
| 3-5 | `Created.now()` の取得タイミング統一 | 全 DataSource 13 箇所を「tx 内で取得」に統一(現状 InvitationRegisterDataSource.issue のみ tx 外) |

**検証:** 3-1 のテストが 3-2/3-3 のクエリ書き換え前後で green であること。`EXPLAIN` 確認は任意、クエリ数は Exposed ログで before/after を記録。
**規模目安:** テスト +400 行 / 本体 ±150 行。リスク: 中(クエリ変更)→ 3-1 で抑止。

---

## フェーズ 4: frontend 構造リファクタ

**狙い:** App.kt への集中と VM 間コピペの解消。**見た目の変更はゼロ**(忠実化済みの画面に視覚回帰を起こさない)。

| # | 対象 | 内容 |
|---|------|------|
| 4-1 | `frontend/src/webMain/.../App.kt`(695 行) | `AuthState.Ready` ブランチ(約 340 行)を `ReadyContent` / `CatalogOverlayContent` / `HouseholdSheets` に関数分割。VM 生成・wiring を各関数に同伴 |
| 4-2 | `App.kt:499-513, 572-588` | `CatalogOverlay.Master` / `Settings` での `ProductMasterViewModel` 二重生成を解消(分岐の外で単一 `remember`)。Settings ブランチが load 系を使わない問題は write 専用 interface 分離まではせず、共有 VM で許容 |
| 4-3 | `App.kt` 内 6 箇所 | `LaunchedEffect(vm){load()}` + `LaunchedEffect(refresh){collect{load()}}` の対を `LoadWithRefresh(vm, refresh)` ヘルパー Composable に集約 |
| 4-4 | 7 ViewModel の `handleFailure` コピペ | `ReauthController`+`ToastController` を受ける共通ヘルパー(拡張関数 or 小さな collaborator)に集約。SettingsViewModel の `failWith` 微差も吸収 |
| 4-5 | エラー UX の二重表示解消(6 VM の `load()`) | load 失敗 = `UiState.Error`(画面表示)のみ / mutation 失敗 = トーストのみ、に分類整理。エラーテキスト色も `tokens.statusOut` に統一(ArchivedScreen の `tokens.sub` を是正) |
| 4-6 | `InventoryViewModel.kt:38-41` | `_view`/`_query` の手動二重管理を `combine(_stocks, _view, _query)` + `stateIn` の単一フロー合成に変更 |
| 4-7 | `ShoppingListViewModel.kt:29` | `replenishStock` に `OccurredAt` を通し、`App.kt` 側の `OccurredAt.now()` 固定差し込みを解消(Inventory/ProductDetail と対称に) |
| 4-8 | `App.kt:628-683` `ProductSettingsSheetWithImage` | `imageBusy`・picker 呼び出し・楽観表示フラグを ViewModel 側に移し、Composable は表示専任に |
| 4-9 | atom 昇格 | `AvatarBadge(name, size)`(3 箇所の散在実装を統合、フォントサイズはサイズ比例)/ `SectionLabel`(3 画面の private 定義を designsystem/atom に昇格、スタイル差はパラメータ化) |
| 4-10 | `designsystem/theme/MindstockType.kt:33` | `notoSansJpFamily()` の毎回再構築を解消(CompositionLocal or remember で 1 箇所に) |
| 4-11 | `AuthViewModel.kt:79,116` + `App.kt:186` | `AuthState.Failed.message` を `UiText` 化して strings 管理へ。Failed 表示のスタイル(エラー色)も統一 |
| 4-12 | `OnboardingViewModel.kt:64` / `NeedHouseholdViewModel.kt:38` | VO バリデーションの `runCatching{...}.getOrNull()` を try/catch(or fold)に置換(nullable 原則の精神に整合) |
| 4-13 | `designsystem/atom/HouseholdPill.kt:70` | `"$memberCount 人"` のハードコードを `stringResource(Res.string.household_member_count, memberCount)` に置換(strings.xml:73 に定義済み。i18n ルール違反の解消) |

**検証:** `./gradlew :frontend:compileKotlinWasmJs` + commonTest。**dev server 実描画で全画面 eyeball(忠実化非退行の確認。手順は memory: fidelity-verify-loop-mechanics)。**
**規模目安:** 約 25 ファイル・±600 行。リスク: 中(分割は機械的だが画面数が多い)→ 実描画確認で担保。

---

## フェーズ 5: テスト・ビルド補強

**狙い:** 残るカバレッジ空白とビルド構成の重複を解消し、リファクタリング後の状態を固定する。

| # | 対象 | 内容 |
|---|------|------|
| 5-1 | Service テスト追加 | `HouseholdRegisterService` / `InvitationService` / `InvitationRegisterService` / `ResidentService` / `ResidentRegisterService` に mock ベース FunSpec(非メンバー操作の例外伝播 + 主要ハッピーパス) |
| 5-2 | `RevokeInvitationScenario` テスト | JoinHouseholdScenarioTest に倣い、正常 revoke + 無効招待 revoke |
| 5-3 | Controller テスト fixture 共通化 | 10 ファイルにコピペされた `AuthIdentity`+`MindstockSession.Registered` セットアップを testFixtures の `buildRegisteredSession()` に集約 |
| 5-4 | 無意味テスト削除 + 境界補強 | `ImageUrlTest` / `RawImageUploadTest` の値保持のみケースを削除。`ImageRef` の init require 内容を確認し境界ケース(空・非 hex・長さ)を追加 |
| 5-5 | convention plugin 整理(拡張) | **`integrationTest` タスク定義そのものを `kotlin-jvm` convention に統合**(core/api でほぼ完全コピペ状態の解消)。その際 (a) `kotest.tags.exclude` の重複も convention の `tasks.withType<Test>` へ、(b) `System.getenv()` の configuration phase 読み(core:52 / api:82)を `providers.environmentVariable(key)` の lazy 評価に置換(configuration cache の不要な無効化を解消)、(c) api 側は現状 `@Tags("integration")` テスト 0 件の空実行であることをコメント明示。`ktor-server` convention の `mainClass` 既定は削除せず KDoc で上書き前提を明示(適用モジュールが api のみのため実害なしと再判定) |
| 5-6 | Gradle 構成の硬化(小粒まとめ) | `kmp-shared` / `compose-web` convention の `webMain` ソースセットに階層関係を明示(`applyDefaultHierarchyTemplate()` 明示 or デフォルト階層に委譲。現在は Kotlin の暗黙挙動依存)/ `@js-joda/timezone` の二重宣言(shared の jsMain+wasmJsMain → webMain へ集約、frontend/build.gradle.kts:39 の npm 重複宣言は要否確認の上削除)/ `build-logic/settings.gradle.kts:9` の `google()` にルートと同じ `includeGroupAndSubgroups` フィルタを適用 / `ProductImageTransferTest.kt:40` の `region = "garage"` ハードコードを `STORAGE_REGION` 参照に揃える |
| 5-8 | 軽微な残項目(まとめて 1 コミット) | `StorageProperties.corsAllowedOrigins` を HOCON List 型に / `StorageConfiguration` の起動時 `runBlocking` CORS PUT に `withTimeout` / `rememberProductImage` を internal 化 / `RoundBtn` の KDoc を実用途(Stepper 専用)に修正 / `SettingsViewModel` の `remember` キー差(residentId)に意図コメント / `RoutingConfiguration` の `InvitationRegisterService` 先取り解決パターン不整合の整理 |

**検証:** `./gradlew test integrationTest` + CI 一巡(キャッシュ挙動含む)。
**規模目安:** テスト +500 行 / ビルド設定 ±60 行。リスク: 低。

---

## フェーズ D: 開発基盤・CI(コードフェーズと独立・いつでも並行可)

**狙い:** CI の検証穴と無駄を塞ぎ、ローカル開発のドキュメント/設定を実態に同期させる。Kotlin コードに触らないため他フェーズと衝突しない。

| # | 対象 | 内容 |
|---|------|------|
| D-1 | `.github/workflows/ci.yml` | **frontend の wasm ビルド検証を追加**(`compileProductionExecutableKotlinWasmJs` 程度。テストだけでは成果物の破損を検知できない)。CI ランナーのメモリと相談し、必要ならコメントアウト中の `kotlin.daemon.jvmargs` を有効化 |
| D-2 | `.github/workflows/ci.yml:45` | `./gradlew test jvmTest` の `jvmTest` を整理(KMP モジュールで `test` と同一クラスの二重実行)。`test` のみに統一 |
| D-3 | `.github/workflows/ci.yml` | 全 job に `timeout-minutes`(lint/test=30、integration=60)/ `paths-ignore: ['docs/**', '**.md']` を追加 / integration-test 失敗時に `docker compose logs` を artifact 収集(`if: failure()`) |
| D-4 | `renovate.json:6` | `group:springBoot` プリセットを削除(Spring 不使用の誤継承)。ついでに直近 renovate PR で GHA action の SHA 更新が来ている実績を確認(来ていなければ `github-actions` manager の設定を追加) |
| D-5 | `.github/pull_request_template.md` 新設 | release.yml のラベルドリブン changelog 運用に合わせ、ラベル選択チェックリスト付きの PR テンプレートを追加 |
| D-6 | `compose.yml` | `garage` に healthcheck を追加(admin API `:3903` への wget。現状 `--wait` が garage の ready を保証していない)/ `zitadel-init`(:72-73)・`garage-init`(:103-104)の `depends_on` に `condition` を明示 / Zitadel `--masterkey` リテラル(:28)を `${ZITADEL_MASTERKEY:-<dev既定値>}` の環境変数参照に変更 |
| D-7 | 設定の実態同期 | `mise.toml:7` のコメント「postgres-test service on port 5433」を実態(mindstock_test は postgres:5432 上に postgres-init.sh で作成)に修正 / `backend/core/build.gradle.kts:61` の `testContainersImageName` を `postgres:18.4-alpine` に統一(compose と一致)/ 同 :42 の description から廃止済み `.env.garage` への言及を削除 |
| D-8 | `README.md` | サービス一覧に `garage` / `garage-init` を追加(:7-14)/ 起動手順を `mise run up` を正として書き換え(:19-21。`docker compose up -d` 単体では init 完走を待たない)/ backend・frontend 起動も `mise run backend` / `mise run frontend` を案内(:51-52。`--continuous` の差は開発体験に直結)/ **「環境変数リファレンス」セクションを新設**(`2026-06-12-env-inventory.md`(本プラン付録)を元に、変数名・読む側・デフォルト・設定要否の一覧。現状この全体像を得られる場所がない) |
| D-9 | `.gitignore` | Spring/STS/NetBeans 残骸(`HELP.md`、`.springBeans`、`/nbproject/` 等 :1,9-35)を削除 / `kotlin-js-store/`(:46)の ignore を解除し lockfile をコミット(★決定済み: 依存再現性の標準慣行) |
| D-10 | `docker/garage.toml:7` | ゼロ埋め `rpc_secret` に「dev-only」コメントを追記(garage-init.sh には説明があるが toml 側にない) |
| D-11 | `.github/workflows/ci.yml` 全 job | `actions/setup-java`(temurin / java-version: 25)を明示追加。現状は ubuntu-latest のプリインストール JDK 25 への暗黙依存で、ランナーイメージ更新時に壊れる(foojay resolver は settings に居るが CI での動作保証をコメントで明示) |
| D-12 | `backend/api/src/main/resources/application.yaml:4` | `port: "$PORT:8080"` のデフォルトを `8090` に変更(★決定済み: frontend dev server が 8080 を使うため、`PORT` 未設定の Gradle 直叩きで衝突する。mise・webpack proxy・README は全て 8090 前提) |

**検証:** `mise run up` をクリーン状態(`docker compose down -v`)から実行し全サービス healthy → backend/frontend 起動 → 統合テスト green。`PORT` 未設定の `./gradlew :backend:api:run` が 8090 で立つこと。CI は draft PR で全 job 一巡(wasm ビルド job の所要時間とメモリを確認)。README は記載手順をそのまま実行して再現確認。
**規模目安:** 設定/docs 約 14 ファイル・±180 行。リスク: 低(CI と compose の変更は draft PR で検証してからマージ)。

---

## 実行プロトコル(全フェーズ共通)

**本書は別セッションでの spec 生成に自己完結で使えることを意図している。** フェーズ詳細 spec を書くセッションは、(1) 本書の該当フェーズのタスク表(各行に file:line・修正方針・検証を内包)、(2) 決定事項/見送りセクション(議論の再燃防止)、(3) 付録 `2026-06-12-env-inventory.md`、(4) 自動ロードされる `.claude/rules/` だけを前提とする。**会話ログや監査エージェントの生レポートには依存しない。** 注意: file:line は 2026-06-12 時点の main のスナップショットであり、先行フェーズのマージでずれる。spec 作成時は必ず現物を Read して座標を再確認すること(タスク表の「何を・なぜ」は安定、「どこ」は揮発)。

1. フェーズ着手時: main から `refactor/p<N>-<slug>` ブランチ → superpowers:writing-plans でフェーズ詳細プラン(本書のタスク表を、テストコード・実装コード付きのステップに展開)→ ユーザ確認
2. 実装: superpowers:subagent-driven-development(タスクごとに fresh subagent + 二段レビュー)。仕様確定済みの機械的 frontend タスクはインライン実装可(memory: subagent-vs-inline-frontend)
3. フェーズ完了条件: `./gradlew test` green / 該当フェーズの検証コマンド green / 削除シンボルの grep 残参照ゼロ / PR レビュー
4. コミットメッセージに issue/PR 番号を書かない(working agreement)
5. **順序**: R と 0 は並行で最初に(R はフェーズ 1 着手の前提)。0→1→2 は直列(同じ層を触るため)。3 と 4 は 2 完了後に並行可。5 は最後。**D はコードに触らないためいつでも並行可**
6. コード変更でルールの記述が変わるフェーズ(2-5 の id ルール改訂、4-4 の handleFailure 共通化等)は、該当フェーズの PR にルール追従更新を同梱する

## 規模感の見立て

| フェーズ | PR 数 | 正味 diff 目安 | リスク |
|---|---|---|---|
| R ルール整備 | 1 | docs 13 ファイル | ゼロ |
| 0 掃除 | 1 | -250 行 | 最小 |
| 1 原則適合 | 1 | ±300 行 | 低 |
| 2 ロジック引き込み | 1 | ±280 行(挙動変更 1 件+fail-fast 追加) | 中 |
| 3 infrastructure | 1 | +400/±150 行 | 中(テスト先行で抑止) |
| 4 frontend 構造 | 1〜2 | ±600 行 | 中(実描画 eyeball で担保) |
| 5 テスト/ビルド | 1 | +600 行 | 低 |
| D 開発基盤/CI | 1 | 設定/docs ±180 行 | 低(draft PR で CI 検証) |
