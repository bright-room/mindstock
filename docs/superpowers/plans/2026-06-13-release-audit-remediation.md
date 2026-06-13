# リリース前監査 是正 実装計画書（2026-06-13）

監査レポート `docs/2026-06-13-release-audit.md`（A. 調査結果 / B. フェーズ分けプラン）を、そのまま着手できる粒度に具体化した実装計画。各タスクの「現状」は対象ファイルを実読した上で記述している（推測ではない）。

## 入力と前提

- 正本: 監査レポート `docs/2026-06-13-release-audit.md`、画面モック `docs/ref/mindstock.zip`（`app/*.jsx`）、設計書 `docs/superpowers/specs/`、ルール `.claude/rules/*.md`、忠実化記録 `docs/superpowers/fidelity/`。
- 監査基準コード: `main`。本計画も同 tree を実読して作成。
- このセッションではコードを変更していない（計画策定のみ）。

## 計画策定時に確定した方針（ユーザ承認済み）

| 論点 | 決定 |
|---|---|
| **F-1（買い物リスト残留）** | **案A**: 補充（replenish）時に `manuallyWanted` を解除する。モック `data.jsx`（補充で `wanted:false`）と一致。 |
| **C-1（refresh 購読の所在）** | **spec を実装に合わせ改訂**（App.kt の UI 層一元購読 `LoadWithRefresh` を正式設計とする。コード変更なし）。 |
| **C-2（ShoppingList 補充の occurredAt）** | **`now()` 固定を意図仕様として明記**（買い物クイック補充は日付ピッカー非表示。コード変更なし、spec/コメントに記録）。 |
| **F-2（世帯切替波及）** | **再現先行タスク**。実読では `sessionState.activeHouseholdId`（App.kt:267）+ 各 VM の `remember(householdId)` で全タブが再読込される構造のため、監査の根本原因記述と矛盾（偽陽性の可能性）。まず再現を確認し、再現した場合のみ真因をデバッグ・修正する。推測で修正コードを書かない。 |
| **Phase 4 運用ドキュメント** | **既決どおり見送り・再確認のみ**。デプロイ/監視/バックアップ/SECURITY.md は本リリースで整備せず、トリガー条件を明文化する。 |

## 監査 B からの主な再分類（実読で判明）

- **R-1**: i18n 違反ではない。`MoveSheet.kt:114,119` は `"$current$unit"` / `"$after$unit"`（数値+単位の表記）で、ルール `frontend-i18n-and-font.md:15` が明示的に strings 対象外と規定。**整合性タスクに格下げ**（直上の 99 行が `move_current_short` リソースを使うため一貫化する）。勝手に削除せず Phase 0 に残す。
- **F-8**: **既に実装済み**（`ActivityScreen.kt:89-96` が Correction 行を除外し対象行へタグ付与＝設計 `p6-1b-design.md:103` と一致）。新規実装ではなく検証タスク（P2-0）。
- **F-6**: hydration 依存で安全に動作中、挙動変更不要。**検証テスト追加のみ**（P1-3）。
- **D-1**: コードは既に `StorageProperties.kt:13` で `List<String>`、`application.yaml:31-32` も YAML リスト。README は形式を明記していない（「カンマ区切り」記述は実在しない）。**README 文書修正のみ**（実装は変えない）。

---

## 全タスク一覧サマリ

| ID | タイトル | 紐付く指摘 | Q | 重大度 | 工数 | 依存 |
|---|---|---|---|---|---|---|
| **P0-1** | PreferenceStore を internal 化 | R-2 | Q3 | med | S | — |
| **P0-2** | 例外翻訳表に ArchivedProductMovementException 追記 | R-3 | Q3 | med | S | — |
| **P0-3** | 増減プレビューの数値+単位を resource 化（整合性） | R-1 | Q3 | low | S | — |
| **P0-4** | 陳腐コメント修正（P6-1b 参照の除去） | R-4 | Q3 | low | S | — |
| **P0-5** | README: STORAGE_CORS_ORIGINS の形式を明記 | D-1 | Q4 | low | S | — |
| **P0-6** | README: AUTH_* トラブルシュート節追加 | D-2 | Q4 | low | S | — |
| **P0-7** | localStorage/sessionStorage 使い分け基準をルール追記 | D-3 | Q4 | low | S | — |
| **P0-8** | frontend-architecture に ReadyContent 構造説明追記 | D-4 | Q4 | low | S | — |
| **P0-9** | ResidentRpcService.me() 削除の grep 確認記録 | D-5 | Q4 | low | S | — |
| **P1-1** | 補充で manuallyWanted 解除（案A）＋テスト | F-1 | Q1/Q5 | **high** | M | — |
| **P1-2** | 世帯切替波及の再現確認（→ 再現時のみ修正） | F-2 | Q1/Q5 | **high** | S | — |
| **P1-3** | appendMovement identity 遷移の検証テスト | F-6 | Q1 | med | S | — |
| **P2-0** | F-8（Correction 除外）の検証のみ | F-8 | Q5 | low | — | — |
| **P2-1** | ProductDetail 予測日数表示 | F-3 | Q2/Q5 | med | S | — |
| **P2-2** | wanted「リスト」バッジ＋needCount に want 加算 | F-4 | Q2/Q5 | med | M | **P1-1** |
| **P2-3** | デスクトップ 3 列グリッド（wide 分岐） | F-5 | Q2/Q5 | med | S | — |
| **P2-4** | オンボーディング cancel「別アカウント」導線 | F-7 | Q5 | low | S | — |
| **P2-5** | WelcomeScreen wide/compact 分岐 | F-9 | Q2 | low | S | — |
| **P2-6** | ConfirmStep カード radius 18→22dp | D-6 | Q2 | med | S | — |
| **P3-1** | C-1: spec 改訂（UI層一元購読を正）＋ルール明記 | C-1 | Q1 | low | S | P1/P2 確定後 |
| **P3-2** | C-2: now() 固定を意図仕様として明記 | C-2 | Q1 | low | S | P1/P2 確定後 |
| **P3-3** | C-3: selectedTab hoist＋responsive 切替テスト | C-3 | Q1 | low | S | — |
| **P4-1** | 運用文書のトリガー条件明文化（既決再確認） | 既決A-6/D既決 | Q4 | low | S | P0–P3 |
| **P4-2** | known-issues / fidelity チェックリスト最終更新 | 横断 | 全 | low | S | P1–P3 |
| **P4-3** | フル検証＋22件クローズ突き合わせ | 横断 | 全 | — | M | 全タスク |

工数表記: S = 半日以内 / M = 1〜2 日 / L = 数日。

## フェーズ間依存

```
Phase 0 ─┐
         ├─（互いに独立・並列着手可）
Phase 1 ─┘
   │
   │ P1-1（F-1）の manuallyWanted セマンティクス確定
   ▼
Phase 2 ── P2-2 のみ P1-1 に依存。他タスク（P2-0,1,3,4,5,6）は Phase 0/1 と並列可
   │
   ▼
Phase 3 ── P3-1/P3-2 は doc 系で P1/P2 の最終コードを反映するため後置。P3-3 は独立コード変更
   │
   ▼
Phase 4 ── P0〜P3 完了後。P4-3 が全タスクの最終ゲート
```

- **Phase 0 と Phase 1 は完全並列**（触る領域が重複しない: Phase 0 = ルール/文書/軽微 frontend、Phase 1 = domain/application/test）。
- **Phase 2** は P2-2（F-4）だけが P1-1（F-1）に依存。残りは Phase 0/1 と並列着手してよい。
- **Phase 3** の doc 系（P3-1/P3-2）は、Phase 1/2 で実装が確定した後に spec を最終化する（先に書くと再修正になる）。P3-3 はいつでも着手可。
- **Phase 4** は P0〜P3 完了が前提。

## クリティカルパス（最短でリリース可能状態）

```
P1-1（F-1 補充で wanted 解除・M）
  └→ P2-2（F-4 wanted バッジ＋needCount・M / F-1 のセマンティクスに依存）
       └→ P4-3（最終 eyeball ＋ 22 件クローズ突き合わせ・M）
```

- high バグのうち **P1-2（F-2）は再現確認タスクで P1-1 と並列**、再現しなければ修正不要。
- Phase 0 の文書/ルール群（9 タスク）は全て独立・並列で、クリティカルパスに乗らない。
- Phase 2 の他タスク（P2-1,3,4,5,6）と Phase 3 もパス外（並列消化可能）。
- したがって最短経路は **F-1 → F-4 → 最終検証** の M×3 直列。F-4 を frontend-merge で実装する場合は backend 契約変更が無いぶん短縮できる。

---

# Phase 0: ルール・文書の即時是正

- **ゴール / DoD**: Q3 のルール違反（R-1〜R-4）と Q4 の文書・実体不一致（D-1〜D-5）をゼロにする。
- **このフェーズ完了時に検証すべきこと**:
  - `./gradlew :frontend:compileKotlinWasmJs` と `./gradlew :backend:api:compileKotlin` が通る（フル `build` は frontend WasmJs が OOM するため、コンパイル確認は分割）。
  - R-2 の grep（`grep -rn "object PreferenceStore" frontend/src`）で全宣言に `internal` が付く。
  - R-3 のルール表に `ArchivedProductMovementException` 行が存在。
  - R-4 の grep（`grep -rn "P6-1b" frontend/src/commonMain/.../inventory`）が 0 件。
  - README / ルール文書が実体（`application.yaml` / `AuthSettings.kt` / `TokenStore.kt` / `PreferenceStore.js.kt` / `App.kt`）と一致。
- **並列**: P0-1〜P0-9 は全て並列着手可（互いに依存なし）。

## P0-1 PreferenceStore を internal 化

- **紐付く指摘**: R-2（Q3 / med）`frontend-kmp-structure.md:13`「platform 依存（expect/actual）の宣言は不用意に public にしない」。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/preference/PreferenceStore.kt`
  - `frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/core/preference/PreferenceStore.js.kt`
  - `frontend/src/wasmJsMain/kotlin/net/brightroom/mindstock/frontend/core/preference/PreferenceStore.wasmJs.kt`（存在確認の上、同様に対応。`frontend-kmp-structure.md` が両 actual の存在を明記）
- **現状**: `expect object PreferenceStore`（common）と各 `actual object PreferenceStore` が修飾子なし＝デフォルト `public`。同様に他の expect/actual（`SessionStorage`・`BrowserNav`・`ImagePicker` 等）も確認対象。
- **変更内容**: expect 宣言と全 actual 宣言に `internal` を付与。利用箇所は全て `:frontend` モジュール内（`TokenStore` 等）なので `internal`（module 内可視）で破綻しない。同 PR 内で他の expect/actual（`secureRandomBytes`/`sha256`/`base64UrlNoPad` は既に internal の想定だが grep で確認）も逸脱があれば揃える。
- **受け入れ条件**: `grep -rn "expect object PreferenceStore\|actual object PreferenceStore" frontend/src` の全ヒットに `internal` が付く。`PreferenceStore` の参照側がコンパイルエラーにならない。
- **検証方法**: `./gradlew :frontend:compileKotlinWasmJs :frontend:compileKotlinJs`（両ターゲット）。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低。可視性のみで挙動不変。

## P0-2 例外翻訳表に ArchivedProductMovementException 追記

- **紐付く指摘**: R-3（Q3 / med）`backend-rpc-and-transactions.md:74-85` の例外翻訳表の陳腐化。
- **対象ファイル**:
  - `.claude/rules/backend-rpc-and-transactions.md`（74-85 行の表）
  - （根拠確認のみ）`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt:80-81`
- **現状**: `SessionGuard.kt:80-81` が `ArchivedProductMovementException → RpcError.Conflict` に翻訳しているが、ルール表に当該行が無い（表は `LastOwnerException`/`DuplicateJanException`/`CannotArchiveWithStockException`/`InsufficientStockException`/`InvitationInvalidException` 等で止まっている）。
- **変更内容**: 表に `| ArchivedProductMovementException（アーカイブ済み商品の在庫変動） | Conflict |` を追記。実装の翻訳と一致させる。
- **受け入れ条件**: 表に当該行が存在し、`SessionGuard.kt` の翻訳と過不足なく一致（表 ↔ 実装の双方向で漏れなし）。
- **検証方法**: `grep -n "ArchivedProductMovementException" .claude/rules/backend-rpc-and-transactions.md backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/guard/SessionGuard.kt` で両方ヒット。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（文書のみ）。

## P0-3 増減プレビューの数値+単位を resource 化（整合性）

- **紐付く指摘**: R-1（Q3 / low）。**注**: ルール `frontend-i18n-and-font.md:15` により数値+単位の表記は strings 対象外＝厳密には i18n 違反ではない。直上 `MoveSheet.kt:99` が `stringResource(Res.string.move_current_short, current, unit)` を使う一方、114/119 行が直接連結している**一貫性の不揃い**を解消する（監査 R-1 を「対応済み」にするための整合化）。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/MoveSheet.kt`（114, 119 行）
  - `frontend/src/commonMain/composeResources/values/strings.xml`
- **現状**: `AppText("$current$unit", ...)`（114 行）と `AppText("$after$unit", ...)`（119 行）が数値と単位を直接連結。99 行は同種の表示を `move_current_short` リソース経由で行っている。
- **変更内容**: `strings.xml` に数値+単位整形用リソース（例 `<string name="qty_with_unit">%1$d%2$s</string>`）を追加し、114/119 行を `stringResource(Res.string.qty_with_unit, current, unit)` / `stringResource(Res.string.qty_with_unit, after, unit)` に置換。文言は追加せず（locale 非依存の整形のみ）。
- **受け入れ条件**: `MoveSheet.kt` に `"$current$unit"` / `"$after$unit"` の直接連結が残らない。増減プレビューの表示（「N単位 ▷ M単位」）が変化しない。
- **検証方法**: `grep -n "\$current\$unit\|\$after\$unit" frontend/src/commonMain/.../MoveSheet.kt` が 0 件。`?preview=move` ハーネス＋dev server で MoveSheet を描画し表示差が無いこと。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低。表示不変。

## P0-4 陳腐コメント修正（P6-1b 参照の除去）

- **紐付く指摘**: R-4（Q3 / low）コメント陳腐化。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/StockSummary.kt:5`
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/SummaryStrip.kt:33`
- **現状**:
  - `StockSummary.kt:5` = `/** 在庫サマリ(買い物 CTA 用)。予測日数/wanted は P6-1b まで未対応。 */`
  - `SummaryStrip.kt:33` = `/** 買い物 CTA。need 件数で accent/surface を切替。予測/wanted は P6-1b。 */`
  - 予測日数は既に実装済み（`ProductCard.kt:76,105-112` / `ShoppingListScreen` で `stock.forecast()` 使用）。wanted 集計は P2-2（F-4）で対応予定。
- **変更内容**: 両コメントから `P6-1b` 参照を除去し現状を反映。例: StockSummary は「need 件数 = outCount + lowCount（手動希望の加算は F-4 で対応）」、SummaryStrip は「need 件数で accent/surface を切替」のみに。**P2-2 完了後**、StockSummary のコメントを「needCount = out + low + want」へ最終化する（P2-2 のチェックリストに含める）。
- **受け入れ条件**: 両ファイルに文字列 `P6-1b` が残らない。コメントが現状の責務と一致。
- **検証方法**: `grep -rn "P6-1b" frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory` が 0 件。
- **依存タスク**: なし（wanted 部分の最終文言は P2-2 で更新）。
- **工数 / リスク**: S / 低。

## P0-5 README: STORAGE_CORS_ORIGINS の形式を明記

- **紐付く指摘**: D-1（Q4 / low）。
- **対象ファイル**:
  - `README.md`（76 行付近の env テーブル）
  - （根拠確認のみ）`backend/api/src/main/resources/application.yaml:31-32`、`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/external/storage/StorageProperties.kt:13`
- **現状**: 実装は `cors-allowed-origins: List<String>`、`application.yaml` は `- "$STORAGE_CORS_ORIGINS:http://localhost:8080"`（env が**単一リスト要素**に展開される）。README はこの形式を明記していない。env 値にカンマを含めても 1 要素の文字列になり、複数オリジンとしては解釈されない（split 実装は無い）。
- **変更内容**: README の該当行/補足に「`STORAGE_CORS_ORIGINS` は dev 既定の単一オリジンを上書きする env。**複数オリジンが必要な場合は `application.yaml` の `cors-allowed-origins` リストに `-` で追記する**（env のカンマ区切りは複数オリジンに展開されない）」と明記。実装は変更しない。
- **受け入れ条件**: README に上記の形式説明があり、`application.yaml` の実挙動（単一要素上書き / 複数はリスト追記）と一致。誤解を招く「カンマ区切りで複数指定可」のような記述を残さない。
- **検証方法**: 手動レビュー（README ↔ `application.yaml` ↔ `StorageProperties.kt` の三点照合）。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（文書のみ）。

## P0-6 README: AUTH_* トラブルシュート節追加

- **紐付く指摘**: D-2（Q4 / low）。
- **対象ファイル**:
  - `README.md`（48 行付近）
  - （根拠確認のみ）`backend/api/src/main/kotlin/net/brightroom/mindstock/configuration/auth/AuthSettings.kt:26-28`
- **現状**: `AuthSettings.kt` は AUTH_* 未設定時に `check(!value.isNullOrBlank()) { "external.auth.$key (env $env) が未設定です。\`.env.zitadel\` を生成しましたか?(\`mise run up\`)" }` で fail-fast。README は「未設定だとビルド/JWT 検証に失敗する」とは書くが、復旧手順を独立節にまとめていない。
- **変更内容**: README に「トラブルシューティング」節を追加。AUTH_* 未設定時の fail-fast メッセージ（`external.auth.* が未設定です`）と復旧手順（`.env.zitadel` を `mise run up` で生成 → backend/frontend を再起動）を記載。
- **受け入れ条件**: README にトラブルシュート節があり、`AuthSettings.kt` の実メッセージ・実コマンド（`mise run up`）と一致。
- **検証方法**: 手動レビュー（README ↔ `AuthSettings.kt`）。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（文書のみ）。

## P0-7 localStorage/sessionStorage 使い分け基準をルール追記

- **紐付く指摘**: D-3（Q4 / low）。
- **対象ファイル**:
  - `.claude/rules/frontend-architecture.md`（「永続化の使い分け」節を追加）
  - （根拠確認のみ）`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/auth/TokenStore.kt:9`（sessionStorage）、`frontend/src/jsMain/kotlin/net/brightroom/mindstock/frontend/core/preference/PreferenceStore.js.kt:6`（localStorage）
- **現状**: token は `SessionStorage`（タブを閉じると破棄）、設定（アクティブ世帯など）は `PreferenceStore`=localStorage（永続）に保存しているが、どのルールにも使い分け基準が無い。
- **変更内容**: `frontend-architecture.md` に短い基準を追記。「**認証トークン等のセッション限定値は `SessionStorage`（タブ終了で破棄）**、**ユーザ設定等の永続値は `PreferenceStore`（localStorage）**。新規の永続化はこの基準で選ぶ」。
- **受け入れ条件**: ルールに当該基準が明記され、`TokenStore`（session）/ `PreferenceStore`（local）の現状と整合。
- **検証方法**: 手動レビュー。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（文書のみ）。

## P0-8 frontend-architecture に ReadyContent 構造説明追記

- **紐付く指摘**: D-4（Q4 / low）。
- **対象ファイル**:
  - `.claude/rules/frontend-architecture.md`
  - （根拠確認のみ）`frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt:265-522`（`AuthState.Ready` → `ReadyContent` → `AppShell`）
- **現状**: ルールに `app/` 層・Controller 三点セットの説明はあるが、`ReadyContent`（4 タブシェル本体。モックの AppContent/ScreenSwitch 相当）と、その world-cross 状態 hoist（`openedState`/`catalogOverlayState`/`settingsSheetState` を呼び出し側で hoist し `ReadyContent` に渡す；`selectedTab` は現状 `ReadyContent` ローカル）の構造説明が無い。
- **変更内容**: `frontend-architecture.md` の `app/` 節に短い小節を追加。`App.kt` の `AuthState.Ready` 分岐が `householdId`（= `sessionState.activeHouseholdId`）を起点に各タブ VM を `remember(householdId)` で生成し、world-cross 状態（詳細オーバーレイ/カタログオーバーレイ/世帯シート）を hoist して `ReadyContent` へ渡す——という現状構造を記述。
- **受け入れ条件**: ルールに `ReadyContent` の役割と hoist 構造の説明があり、`App.kt` の実装と一致。
- **検証方法**: 手動レビュー（ルール ↔ `App.kt:265-522`）。
- **依存タスク**: なし。**注**: P3-3 が `selectedTab` を hoist する場合、当該記述の `selectedTab` 部分を P3-3 完了時に更新する（P3-3 のチェックリストに含める）。
- **工数 / リスク**: S / 低（文書のみ）。

## P0-9 ResidentRpcService.me() 削除の grep 確認記録

- **紐付く指摘**: D-5（Q4 / low）。
- **対象ファイル**:
  - `docs/superpowers/specs/2026-06-06-ws-rpc-transport-redesign-design.md:181-184`（「`ResidentRpcService.me()` は削除」記述）
- **現状**: 設計どおり `me()` は RPC 層に存在せず `SessionRpcService.whoami()` に移行済み（実コードで確認済み）。ただし grep 確認の記録が残っていない。
- **変更内容**: 当該 spec 箇所に確認記録を 1 行追記（例: 「2026-06-13 確認: `grep -rn "fun me(" rpc/src backend` で `ResidentRpcService.me()` の不在、`SessionRpcService.whoami()` の存在を確認済み」）。
- **受け入れ条件**: spec に確認記録があり、`grep -rn "fun me(\|ResidentRpcService" rpc/src backend` の結果（me() 不在 / whoami 存在）と一致。
- **検証方法**: 上記 grep を再実行し記録と一致を確認。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（記録のみ）。

---

# Phase 1: 機能バグの解消（high）

- **ゴール / DoD**: known-issues #2 の再現手順が解消し、世帯切替の波及挙動が（再現確認の上で）保証され、identity 遷移の安全性がテストで担保される。
- **このフェーズ完了時に検証すべきこと**:
  - P1-1: 追加テストが green。手動再現「手動希望の商品を補充して十分にする → 買い物リストから消える」。
  - P1-2: 世帯切替の実機再現結果（再現する/しない）が記録され、再現時は修正＋テストで解消。
  - P1-3: identity 遷移の検証テストが green。
  - `./gradlew :backend:core:test`（+ 必要に応じ `:backend:core:integrationTest`）green。
- **並列**: P1-1 / P1-2 / P1-3 は並列着手可。

## P1-1 補充で manuallyWanted 解除（案A）＋テスト

- **紐付く指摘**: F-1（Q1/Q5 / **high**、リリースブロッカー候補）。known-issues #2。
- **対象ファイル**:
  - `backend/core/src/main/kotlin/net/brightroom/mindstock/application/service/stock/StockRegisterService.kt`（`replenish`）
  - `backend/core/src/main/kotlin/net/brightroom/mindstock/application/repository/product/ProductRegisterRepository.kt`（`setWanted` interface・27 行）
  - 配線（DI コンテナ）: `StockRegisterService` を組み立てている箇所（`backend/core` または `backend/api` の DI/module 定義）
  - テスト新規: `backend/core/src/test/.../application/service/stock/StockRegisterServiceTest.kt`
  - （根拠確認のみ）`domain/.../inventory/shopping/ShoppingNeed.kt:22-26`・`ShoppingEntry.kt:11-13`、モック `app/data.jsx:215-222`
- **現状**:
  - `ShoppingNeed.judge(status, manuallyWanted)` = `status != 十分 → 在庫不足` / `manuallyWanted() → 手動希望` / `else → 不要`。`ShoppingEntry.onList()` は `need().onShoppingList`。
  - `StockRegisterService.replenish` は `stock.replenish(...)` の movement 追記のみで `manuallyWanted` に触れない。
  - `manuallyWanted` は `ProductWantedEventsTable` にイベント追記する形（`ProductRegisterDataSource.setWanted` → `ProductRegisterRepository.setWanted` interface）。`StockRegisterService` は `ProductRegisterRepository` を**注入していない**（`residentRepository`/`stockRepository`/`stockRegisterRepository`/`householdRepository`/`productRepository` のみ）。
  - 結果: 手動希望（status=十分でも 手動希望）の商品は補充後も `手動希望` のままリストに残る＝ known-issues #2。
- **変更内容**（案A・unconditional clear）:
  1. `StockRegisterService` に `private val productRegisterRepository: ProductRegisterRepository` を注入追加。
  2. `replenish` の末尾（`appendMovement` 後）に `productRegisterRepository.setWanted(productId, Wanted(false))` を追加（`Wanted` VO は `domain/.../inventory/shopping/Wanted` を想定。コンストラクタ `Wanted(false)`）。`consume` は変更しない（消費は希望を解除しない）。
  3. DI 配線に `ProductRegisterRepository` の引数を追加。
  - **設計上の位置づけ**: `manuallyWanted` は Stock から分離した read-model 合成入力（既決）。補充に伴う解除は application 層の orchestration として表現する（ドメイン集約 Stock は変更しない）。
- **受け入れ条件**:
  - 手動希望（`manuallyWanted=true`）の商品を、在庫が `十分` になる量だけ補充すると `ShoppingEntry.onList()` が `false` になり買い物リストから消える。
  - 補充しても `在庫不足` のままなら、`在庫不足` 判定でリストに残る（手動希望の解除が掲載に影響しない）。
  - モック `data.jsx:215-222`（補充で `wanted:false`）の挙動と一致。
- **検証方法**:
  - 新規ユニットテスト（`StockRegisterServiceTest`、JVM=Kotest FunSpec 可）: fake/mockk の `ProductRegisterRepository` で、`replenish` 後に `setWanted(productId, Wanted(false))` が呼ばれること、`consume` では呼ばれないことを検証。
  - 補助の結合確認（任意）: `:backend:core:integrationTest` で「手動希望 → 補充 → shoppingList から消える」E2E（要 `STORAGE_*` / `mise run up`）。
  - `./gradlew :backend:core:test`。
- **依存タスク**: なし（P2-2 がこの結果に依存）。
- **工数 / リスク**: M / **中**。
  - **影響範囲**: `StockRegisterService` のコンストラクタ署名変更 → DI 配線と既存テストのインスタンス化箇所に波及。`replenish` 経路（在庫タブ・買い物タブ双方の補充）に共通で効く。
  - **トランザクション境界**: movement INSERT と wanted イベント INSERT は別トランザクション（DataSource 自前境界）。補充成功・wanted 解除失敗のとき「在庫は増えたがリストに残る」状態が起こり得る（家庭内在庫の規模では許容、既存の DataSource 単位トランザクション方針と整合）。許容しない判断なら 1 トランザクション化を別途検討（リスクとして記録）。
  - **ロールバック方針**: 追加した注入と 1 行の `setWanted` 呼び出しを revert すれば従来挙動に戻る（純加算的変更）。

## P1-2 世帯切替波及の再現確認（→ 再現時のみ修正）

- **紐付く指摘**: F-2（Q1/Q5 / **high**）。
- **対象ファイル**（調査・必要時修正の候補）:
  - `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt:138,267,405,419,422,428,440,449,475-494,603-605`
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/core/session/AppSession.kt:34,36`
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/AuthViewModel.kt:141-144`
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/household/SettingsViewModel.kt:196-198`
- **現状（実読の証拠 — 偽陽性の可能性）**:
  - `App.kt:138` `val sessionState by session.state.collectAsState()`（reactive）。
  - `App.kt:267` `val householdId = sessionState.activeHouseholdId`。
  - `homeVm`/`shopVm`/`activityVm` は全て `remember(householdId)`（405/428/440 行）。Shop/Activity の load は `LoadWithRefresh(vm, refresh)` の `LaunchedEffect(key = vm)`。
  - 切替経路: `SettingsViewModel.switchHousehold` → `AuthViewModel.switchActiveHousehold`（142-143）→ `AppSession.setActiveHousehold` = `_state.update { it.copy(activeHouseholdId = id) }`（36 行）。
  - → 切替は `activeHouseholdId` を reactive に更新するため `householdId` が変わり、各 `remember(householdId)` VM が新インスタンス化 → `LaunchedEffect(key = newVm)` が `load()` を再実行する構造。**監査の根本原因（「session 反映のみ / refresh 未発火」）は `remember(householdId)` keying を見落とした偽陽性の可能性が高い**。
  - ローカルでは 2 世帯の実機再現が不可（2nd Zitadel ID 要）。
- **変更内容（条件分岐）**:
  1. **まず実機再現を確認**（2 世帯を用意し、各タブ＝在庫/買い物/活動に居る状態で世帯ピル → スイッチャ → 別世帯を選択 → そのタブのデータが新世帯に切り替わるか）。
  2. **再現しない場合**: F-2 を「検証済み非バグ（監査偽陽性）」として記録（→ P4-2 で known-issues / 監査レポートに追記）。コード変更なし。
  3. **再現する場合のみ**: 真因を `systematic-debugging` で特定（例: `sessionState` が再構成されない条件、`load()` が新 householdId を参照しない条件など）。監査が挙げた「refresh 未発火」は真因でない可能性が高いので、推測で `refresh.request()` を足さず、観測された真因に対する最小修正を行い、再現が消えることを確認。回帰テスト（`AppSessionTest` 等の commonTest）を追加。
- **受け入れ条件**: 世帯切替後、在庫・買い物・活動の 3 タブが（手動リロードなしに）新世帯のデータを表示する、が**実機で確認**される。または、再現しないことが確認され記録される。
- **検証方法**: 実機（headed Chromium + dev server、2nd Zitadel ID）での手動再現。再現・修正時は commonTest で session→householdId→VM 再生成の不変条件を可能な範囲でテスト。
- **依存タスク**: なし。
- **工数 / リスク**: S（再現しない場合は記録のみ）/ 中（再現する場合はデバッグ工数が増える）。推測修正を避けることで、不要コード追加のリスクを排除。

## P1-3 appendMovement identity 遷移の検証テスト

- **紐付く指摘**: F-6（Q1 / med）。
- **対象ファイル**:
  - テスト新規/追記: `backend/core/src/integrationTest/.../infrastructure/datasource/stock/StockRegisterDataSourceTest`（既存があれば追記）
  - （根拠確認のみ）`domain/.../inventory/stock/Stock.kt:33-43`（`replenish` が `MovementIdentity.Pending` で追記）、`backend/core/.../infrastructure/datasource/stock/StockRegisterDataSource.kt:16-33`（INSERT）、`StockHydration.kt:18-20`（`Persisted` で hydrate）
- **現状**: `appendMovement` は Pending movement を INSERT するのみ（戻り値 Unit）。再 load 時に `toStockMovement` が `MovementIdentity.Persisted(id)` に hydrate。連続操作は各操作後に Repository から再 load する設計のため、Pending→Persisted のズレは顕在化しない（挙動変更不要）。ただし「INSERT → 再 load で Persisted 化される」不変条件の回帰テストが無い。
- **変更内容**: 挙動は変えず、検証テストを追加。`appendMovement` で追記 → `stockRepository.findByProduct`（または history）で読み直すと、追記した movement が `MovementIdentity.Persisted`（採番済み id）で返ること、同一商品を連続して 2 回追記しても両 movement が一意の Persisted id で hydrate されることを検証。
- **受け入れ条件**: 追加テストが green。「INSERT → 再 load で Persisted」「連続操作で id 衝突なし」が担保される。
- **検証方法**: `./gradlew :backend:core:integrationTest`（要 `STORAGE_*` / `mise run up`）。DB を使わない範囲で済むなら `:backend:core:test`。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（テスト追加のみ、挙動不変）。

---

# Phase 2: モック完成度 98% 達成

- **ゴール / DoD**: 是正対象画面が視覚・機能とも 98% 以上。`ProductDetail` の F-3、`Onboarding` の D-6 を解消し、F-4/F-5/F-7/F-9 を実装。
- **このフェーズ完了時に検証すべきこと**:
  - `?preview` ハーネス（`webMain/PreviewHarness.kt` + `Main.kt` の `?preview=` 分岐）＋ dev server `--continuous` で、モック clone を並列描画して各画面を突き合わせ（手順は `fidelity-verify-loop-mechanics`）。
  - `docs/superpowers/fidelity/` の該当チェックリスト更新。
  - `./gradlew :frontend:compileKotlinWasmJs` が通る。
- **並列**: P2-0/P2-1/P2-3/P2-4/P2-5/P2-6 は並列着手可。**P2-2 のみ P1-1 完了が前提**。

## P2-0 F-8（Correction 除外）の検証のみ

- **紐付く指摘**: F-8（Q5 / low）。
- **対象ファイル**: `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/activity/ui/ActivityScreen.kt:87-119`、`docs/superpowers/specs/2026-06-07-p6-1b-shopping-activity-design.md:103`
- **現状**: 既に実装済み。`ActivityScreen` は `feed` から `Correction.target` 集合（`correctedIds`）を作り、`feed` から Correction 行を除外（`it.movement !is StockMovement.Correction`）し、対象行に「訂正済」タグを付与＝設計と一致。
- **変更内容**: なし（実装変更しない）。設計どおりであることを確認し、監査の F-8 を「実装済み・設計一致」としてクローズ記録（→ P4-2）。
- **受け入れ条件**: `ActivityScreen.kt` の Correction 除外＋対象行タグ付与が設計 `p6-1b-design.md:103` と一致していることをレビューで確認。
- **検証方法**: コードレビュー＋（任意）`?preview=activity` で訂正を含む feed の表示確認。
- **依存タスク**: なし。
- **工数 / リスク**: — / なし（検証のみ）。

## P2-1 ProductDetail 予測日数表示

- **紐付く指摘**: F-3（Q2/Q5 / med）。`ProductDetail` 97% の主因。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductDetailScreen.kt`
  - （既存利用例）`frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductCard.kt:76,105-112`
  - `frontend/src/commonMain/composeResources/values/strings.xml:317,319`（`forecast_days_left` / `forecast_days_left_plain`）
  - モック `app/screens-c.jsx:210`
- **現状**: `ProductDetailScreen` は `Stock` と `wanted` を受け取るが予測日数表示が無い。予測ロジックは既存（`ProductCard` が `val forecast = stock.forecast(EvaluatedTime.now())` → `ConsumptionForecast.DaysRemaining` のとき「あと約N日」）。`ProductDetailScreen` は同じ `stock` を保持しているので再利用可能。
- **変更内容**: `ProductDetailScreen` の在庫カード（数量/ステータス付近）に、`stock.forecast(EvaluatedTime.now())` が `ConsumptionForecast.DaysRemaining` かつ在庫 > 0 のとき「· あと約N日」（`Res.string.forecast_days_left`、色は accent）を追加。モック `screens-c.jsx:210` の配置（数量の下、accent 色 600）に合わせる。
- **受け入れ条件**:
  - 在庫があり予測可能な商品の詳細を開くと「· あと約N日」が表示され、`ProductCard` と同じ算出結果・同じ文言リソースを使う。
  - 在庫 0 / 予測不能（履歴不足）のときは表示しない（モック `days !== null && product.qty > 0` 条件）。
  - 色・位置がモック `screens-c.jsx:210`（accent・数量直下）と一致。
- **検証方法**: `?preview=product-detail` ＋ dev server `--continuous` でモック clone と並列描画して差分確認。`fidelity/product-detail.md` を更新。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（既存ロジック再利用・加算的）。

## P2-2 wanted「リスト」バッジ＋needCount に want 加算

- **紐付く指摘**: F-4（Q2/Q5 / med）。P6-4a で「Spec2 送り」とされ P6-4b に再掲漏れ（scope 落ち）。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/ProductCard.kt`
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/CompactCard.kt`
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/StockSummary.kt`（`needCount`）
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/InventoryViewModel.kt` / 対応 `InventoryUiState`
  - `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt`（`InventoryRoute` 配線）/ `frontend/src/commonMain/.../feature/inventory/ui/InventoryRoute.kt`
  - （根拠）`rpc/.../product/ProductRpcService.kt:16,22`（`list:Stocks` / `shoppingList:ShoppingList`）、モック `app/screens-a.jsx:53-54,105-109,149`・`app/data.jsx:51-54`
- **現状**: 在庫一覧は `repository::list`（`ProductRpcService.list → Stocks`）で wanted を含まない。`ProductCard`/`CompactCard` は StatusDot + forecast のみで「リスト」バッジ無し。`StockSummary(outCount, lowCount)`、`needCount = outCount + lowCount`（want 未加算）。手動希望情報は `shoppingList`（`ShoppingList` = `ShoppingEntry(stock, manuallyWanted)`）が保持。モックは `want = products.filter(status==='ok' && wanted)`、`need = out + low + want`、カードに status=ok && wanted で「リスト」バッジ（cart icon）。
- **変更内容**（推奨: frontend-merge。backend 契約を変えずに `shoppingList` の `manuallyWanted` を在庫一覧へ流す）:
  1. `InventoryViewModel` の load で `list`（Stocks）に加えて `shoppingList` を取得し、`manuallyWanted=true` の `ProductId` 集合（=モックの「status=ok && wanted」に相当する on-list の手動希望）を導出。`InventoryUiState.Content` に `wantedProductIds: Set<ProductId>`（または各カードへ渡す `wanted: Boolean`）を追加。
  2. `ProductCard` / `CompactCard` に `wanted: Boolean` を渡し、`stock.status() == 十分（ok）&& wanted` のとき「リスト」バッジ（accent + cart icon。モック `screens-a.jsx:105-109,149`）を表示。
  3. `StockSummary` に `wantCount` を追加し `needCount = outCount + lowCount + wantCount`。`StockSummary` 生成元（在庫一覧の集計箇所）で wanted 集合から want を数える。`SummaryStrip` の need 件数表示はそのまま新 `needCount` を使う。
  4. P0-4 で保留した `StockSummary` のコメントを「needCount = out + low + want」へ最終化。
  - **代替案（非推奨）**: backend で `ProductRpcService.list` の read-model に `manuallyWanted` を持たせる。単一情報源になるが RPC 契約・`Stocks` モデル変更で blast radius が大きい。
- **受け入れ条件**:
  - 在庫一覧で `status=十分（ok）かつ手動希望` の商品に「リスト」バッジ（accent・cart）が表示される（モック `screens-a.jsx:105-109`）。`CompactCard` でも同様（`screens-a.jsx:149`）。
  - 買い物 CTA の need 件数 = `out + low + want`（モック `data.jsx:51-54`）。
  - F-1（P1-1）の解除挙動と整合: 補充で手動希望が外れた商品はバッジが消え want から減る。
- **検証方法**: `?preview=stock-home` ＋ dev server `--continuous` でモック clone と並列描画。need 件数の算出を手動データで確認。`fidelity/stock-home.md` 更新。`./gradlew :frontend:compileKotlinWasmJs`。
- **依存タスク**: **P1-1**（manuallyWanted のセマンティクス確定）。
- **工数 / リスク**: M / 中。
  - **影響範囲**: `InventoryViewModel` の load が 2 RPC（list + shoppingList）になる。`ProductCard`/`CompactCard` の引数追加、`StockSummary` の API 変更（`needCount` 利用箇所＝`SummaryStrip` に波及）。
  - **懸念**: 「status=ok && wanted」判定が frontend と domain（`ShoppingNeed`）で二重定義になり得る。可能な限り `shoppingList` の `ShoppingEntry`（手動希望＝`need=手動希望`）を真とし、frontend で status を再判定しない設計にして乖離を避ける。
  - **ロールバック方針**: バッジ表示と `wantCount` 加算は加算的変更。`needCount` を `out+low` に戻し、カード引数・VM の二重 load を revert すれば従来表示に戻る。

## P2-3 デスクトップ 3 列グリッド（wide 分岐）

- **紐付く指摘**: F-5（Q2/Q5 / med）。P6-4a で「Spec2 送り」→ scope 落ち。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/inventory/ui/StockHomeScreen.kt:103`
  - （根拠）`frontend/src/commonMain/.../app/shell/ShellKind.kt`（`LocalIsWideShell`）、`InventoryRoute.kt:43`（`wide` 取得済み）、モック `app/app.jsx:154`（`columns={desktop ? 3 : 2}`）
- **現状**: `StockHomeScreen` は `wide` を引数で受け取る（`InventoryRoute.kt:43` で `LocalIsWideShell.current` を渡している）が、グリッドは `columns = GridCells.Fixed(2)`（103 行）で `wide` を未使用。
- **変更内容**: `StockHomeScreen.kt:103` を `columns = GridCells.Fixed(if (wide) 3 else 2)` に変更。
- **受け入れ条件**: ウィンドウ幅 ≥ 840dp（wide shell）で在庫グリッドが 3 列、< 840dp で 2 列（モック `app.jsx:154`）。
- **検証方法**: dev server `--continuous` でウィンドウ幅を 840dp 跨ぎでリサイズし列数切替を確認。`fidelity/stock-home.md` 更新。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（1 行）。ロールバック: 1 行 revert。

## P2-4 オンボーディング cancel「別アカウント」導線

- **紐付く指摘**: F-7（Q5 / low）。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/onboarding/ui/OnboardingScreen.kt`
  - `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt:196-220`（`AuthState.NeedOnboarding` 分岐）
  - モック `app/screens-onboard.jsx:3,55-59`・`app/app.jsx:138`
- **現状**: `OnboardingScreen` に cancel コールバックが無い（Welcome step に「別のアカウントでログイン」導線が無い）。`AuthState.NeedOnboarding` 分岐（App.kt:196-220）には `reauth` が in scope（`OnboardingViewModel` に渡している）。`SettingsScreen.onLogout = { reauth.request() }`（App.kt:509）と同じ再認証導線が利用可能。モックは Welcome footer に「別のアカウントでログイン」（`onCancel`）。
- **変更内容**:
  1. `OnboardingScreen` に `onCancel: () -> Unit` を追加し、Welcome step の footer に「別のアカウントでログイン」リンク（モック `screens-onboard.jsx:58` のスタイル）を表示。
  2. `App.kt` の `NeedOnboarding` 分岐で `onCancel = { reauth.request() }` を配線（`reauth.request()` がトークン破棄 → authorize へ遷移＝別アカウントログイン。`frontend-rpc-and-error.md` の再認証導線を踏襲）。
  3. 実機確認時、`NeedOnboarding` 状態で `reauth.request()` がトークン破棄＋authorize 再遷移として正しく機能するか確認（同じ `ReauthController` 経路）。
- **受け入れ条件**: オンボーディング Welcome step に「別のアカウントでログイン」が表示され、タップで再認証（authorize 画面）へ遷移する。文言/配置がモック `screens-onboard.jsx` と一致。
- **検証方法**: `?preview=onboarding` で表示確認。実機（再認証遷移）は手動。`fidelity/onboarding.md` 更新。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低。`AuthFlow` への新規メソッド追加は不要（既存 `reauth` 再利用）。
- **注**: 実機の authorize 遷移確認は認証環境を要する（ローカル制約あり）。導線とトークン破棄の配線まではローカルで実装・確認可。

## P2-5 WelcomeScreen wide/compact 分岐

- **紐付く指摘**: F-9（Q2 / low）。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/app/welcome/WelcomeScreen.kt:64-68`
  - `frontend/src/commonMain/.../app/shell/ShellKind.kt`（`LocalIsWideShell`）
  - 設計 `docs/superpowers/specs/2026-06-07-p6-4a-responsive-shell-welcome-design.md:131-132`、モック `app/app.jsx:154`
- **現状**: `WelcomeScreen` は `Modifier.widthIn(max = 420.dp)`（67 行）固定で wide 分岐が無い。設計 131 行は「Compact は縦並び全幅、Wide はモック desktop-login 準拠の中央寄せカード」。
- **変更内容**: `WelcomeScreen` で `LocalIsWideShell.current` を読み、wide のとき中央寄せ幅（設計準拠の約 880dp）、compact のとき従来の `widthIn(max = 420.dp)` + 水平パディングに分岐。
- **受け入れ条件**: 幅 ≥ 840dp で splash が中央 ~880dp 幅、< 840dp で 420dp 幅 + パディング（設計 `p6-4a-...:131`）。
- **検証方法**: dev server `--continuous` で 840dp 跨ぎリサイズして確認。`fidelity/`（welcome 該当があれば）更新。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低。ロールバック: 分岐を revert。

## P2-6 ConfirmStep カード radius 18→22dp

- **紐付く指摘**: D-6（Q2 / med）。`Onboarding` 98% の唯一の差分。
- **対象ファイル**:
  - `frontend/src/commonMain/kotlin/net/brightroom/mindstock/frontend/feature/onboarding/ui/OnboardingScreen.kt:382-389`
  - （根拠）`designsystem/theme/MindstockTokens.kt`（`radiusLg = 22.dp`）、モック `app/core.jsx:72`（`radius.lg = 22`）・`app/screens-onboard.jsx:152`
- **現状**: ConfirmStep のカードが `RoundedCornerShape(18.dp)`（clip と border の双方）でハードコード。モック正は `radius.lg` = 22dp。
- **変更内容**: 382-389 行の `RoundedCornerShape(18.dp)`（2 箇所）を `RoundedCornerShape(tokens.radiusLg)`（22dp）に変更。
- **受け入れ条件**: ConfirmStep カードの角丸が 22dp（`tokens.radiusLg`、他の lg radius 要素と一致／モック `radius.lg`）。
- **検証方法**: `?preview=onboarding`（Confirm step）でモック clone と並列描画して角丸一致を確認。`fidelity/onboarding.md` 更新。
- **依存タスク**: なし。
- **工数 / リスク**: S / 低（数値変更）。ロールバック: revert。

---

# Phase 3: 仕様逸脱の整理・整合

- **ゴール / DoD**: spec と実装の乖離（C-1〜C-3）が、コード修正 or spec 改訂のいずれかで解消され、記録される。
- **このフェーズ完了時に検証すべきこと**:
  - C-1/C-2: 改訂後の spec 文言が実装と一致（差分レビュー）。
  - C-3: `selectedTab` hoist 後も既存挙動が不変、responsive 切替テストが green。
  - 既存テスト green（`./gradlew test`）。
- **並列**: P3-1/P3-2 は doc 系で並列可（ただし P1/P2 の実装確定後に着手）。P3-3 は独立。

## P3-1 C-1: spec 改訂（UI 層一元購読を正）＋ルール明記

- **紐付く指摘**: C-1（Q1 / low）。決定: **spec を実装に合わせ改訂**。
- **対象ファイル**:
  - `docs/superpowers/specs/2026-06-07-p6-1b-shopping-activity-design.md:60,84`
  - `.claude/rules/frontend-architecture.md`（`InventoryRefreshController` 節）
  - （根拠）`App.kt:375-384`（`LoadWithRefresh`）, `App.kt:422,477,489`（在庫/買い物/活動の購読）
- **現状**: spec は「各 ViewModel が起動時に refresh signal を collect→load()」（60/84 行）。実装は UI 層の `LoadWithRefresh`（`App.kt:375-384`）が一元購読し各 VM の `load()` を呼ぶ（在庫は `InventoryRoute`/`App.kt:422`、買い物 `:477`、活動 `:489`）。VM には `refresh` が DI されているが購読は UI 層。
- **変更内容**（コード変更なし）:
  1. `p6-1b-design.md:60,84` の「ViewModel で refresh を購読」記述を、「refresh の購読は UI 層の `LoadWithRefresh`（`App.kt`）で一元化する（VM は `load()` を提供するのみ）」に改訂。
  2. `frontend-architecture.md` の `InventoryRefreshController` 節に「在庫変更の波及購読は `App.kt` の `LoadWithRefresh` で一元化する（各画面 VM はロード関数を提供）」を明記。
- **受け入れ条件**: spec / ルールが実装（UI 層一元購読）と一致し、「ViewModel 購読」の旧記述が残らない。
- **検証方法**: 差分レビュー（spec/ルール ↔ `App.kt:375-384,422,477,489`）。
- **依存タスク**: Phase 1/2 の実装確定後（refresh 関連の最終形を反映）。
- **工数 / リスク**: S / 低（文書のみ）。

## P3-2 C-2: now() 固定を意図仕様として明記

- **紐付く指摘**: C-2（Q1 / low）。決定: **`now()` 固定を意図仕様として明記**（コード変更なし）。
- **対象ファイル**:
  - `docs/superpowers/specs/2026-06-07-p6-1b-shopping-activity-design.md`（買い物リスト補充の節）
  - `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt:482-484`（コメント追記）
  - （根拠）`frontend/src/commonMain/.../feature/shopping/ui/ShoppingListScreen.kt:89,195-197,200`（`onReplenish` に occurredAt 無し、`showDatePicker=false`）
- **現状**: `ShoppingListScreen.onReplenish` は `(ProductId, Int, String)` で occurredAt を持たず、MoveSheet が渡す occurredAt を破棄（`195-197` の `_`）。`App.kt:482-484` が `OccurredAt.now()` をハードコード。買い物リストの補充は日付ピッカー非表示（`showDatePicker=false`、P6-1b 設計）。
- **変更内容**（コード変更なし）:
  1. `p6-1b-design.md` に「**買い物リストからの補充は `OccurredAt.now()` 固定**（クイック補充。日付ピッカー非表示）。バックデートは商品詳細の MoveSheet で行う」を明記。
  2. `App.kt:482-484` の `OccurredAt.now()` 箇所に意図コメントを付す（例: `// 買い物リストのクイック補充は now 固定（バックデートは商品詳細から）`）。
- **受け入れ条件**: spec に now 固定が意図仕様として記載され、コードのコメントと一致。`ShoppingListScreen` のコールバック署名（occurredAt 無し）が「逸脱」でなく「意図」として記録される。
- **検証方法**: 差分レビュー（spec ↔ `App.kt:482-484` / `ShoppingListScreen.kt:195-200`）。
- **依存タスク**: Phase 1/2 の実装確定後。
- **工数 / リスク**: S / 低（文書 + コメント）。

## P3-3 C-3: selectedTab hoist ＋ responsive 切替テスト

- **紐付く指摘**: C-3（Q1 / low）。
- **対象ファイル**:
  - `frontend/src/webMain/kotlin/net/brightroom/mindstock/frontend/App.kt:313-325,391-404,449,452-454`
  - テスト: `frontend/src/commonTest/.../`（shell/タブ状態の検証可能ロジックがある箇所。なければ新規）
  - `.claude/rules/frontend-architecture.md`（P0-8 の `selectedTab` 記述を hoist 後に更新）
- **現状**: `selectedTab` は `ReadyContent` 内ローカル（`App.kt:449 var selectedTab by remember { mutableStateOf(Tab.Stock) }`）で、他の world-cross 状態（`openedState`/`catalogOverlayState`/`settingsSheetState` は呼び出し側で hoist し `ReadyContent` に引数で渡す＝273-276,322-324）と非対称。responsive（wide/compact）切替時のタブ状態一貫性テストは無い。
- **変更内容**:
  1. `selectedTab` を `ReadyContent` 呼び出し側（`AuthState.Ready` 分岐、他の hoist 状態と同じ層）に hoist し、`ReadyContent` に `selectedTab: Tab` / `onSelectTab: (Tab) -> Unit` を引数追加。`ReadyContent` 内の `AppShell(selectedTab=..., onSelectTab=...)` はそれを使う。
  2. responsive 切替の状態一貫性に関する検証可能ロジックをテスト（commonTest、Kotest assertions）。UI 描画網羅は追わず、「wide/compact 切替で選択タブが保持される」「`shellKindFor` の境界（840dp）」等の純ロジックを対象（`frontend-compose-conventions.md` のテスト方針に従う）。
  3. P0-8 の `selectedTab` 記述を hoist 後の構造に更新。
- **受け入れ条件**:
  - `selectedTab` が他の world-cross 状態と同じ層で hoist され、`ReadyContent` は引数で受ける（非対称解消）。
  - wide/compact 切替でも選択タブが保持される（手動 + 可能ならテスト）。
  - 既存挙動（タブ切替・各タブ表示）が不変。
- **検証方法**: `./gradlew :frontend:compileKotlinWasmJs` + commonTest（`./gradlew test` の frontend 該当）。dev server で 840dp 跨ぎリサイズしタブ保持を確認。
- **依存タスク**: なし（ただし P0-8 のドキュメント記述と整合を取る）。
- **工数 / リスク**: S / 低〜中。
  - **影響範囲**: `ReadyContent` の署名変更。`selectedTab` を参照/更新する箇所（`AppShell`・`InventoryRoute` の `onShop`/`onOpenSettings` が `selectedTab = ...` を書く 469-470 行）の経路を hoist 後の `onSelectTab` に通す。
  - **ロールバック方針**: hoist を戻して `ReadyContent` ローカル `remember` に復帰すれば従来構造に戻る（挙動不変のリファクタ）。

---

# Phase 4: 運用ドキュメント完成と最終検証

- **ゴール / DoD**: Q1〜Q5 全てに YES と宣言できる状態。運用文書は既決どおり見送り、トリガー条件を明文化。確定リスト 22 件（再分類後）が全てクローズ。
- **このフェーズ完了時に検証すべきこと**:
  - 監査レポートの確定リスト（F-1〜F-9 / C-1〜C-3 / R-1〜R-4 / D-1〜D-6）が全てクローズ（各タスクの受け入れ条件達成）。
  - `./gradlew build`（+ `./gradlew :backend:core:integrationTest`、要 `STORAGE_*` / `mise run up`）green。frontend は OOM 回避のため `:frontend:compileKotlinWasmJs` で確認。
  - 全画面 eyeball（dev server / 実機）でモック忠実度 98% 以上。
- **依存**: Phase 0〜3 完了が前提。

## P4-1 運用文書のトリガー条件明文化（既決再確認）

- **紐付く指摘**: 既決 A-6 / Q4 既決（デプロイ・監視・バックアップ・SECURITY.md）。決定: **本リリースでは整備せず、トリガー条件のみ明文化**。
- **対象ファイル**:
  - `docs/superpowers/plans/2026-06-12-refactoring-master-plan.md`（見送り節）または `README.md`/`docs/` に運用整備のトリガーを追記
- **現状**: 「公開運用が視野に入った時点で」と先送りされているが、その判断時点（トリガー）が未定義（監査の構造的課題 5）。
- **変更内容**: 運用文書（デプロイ手順・監視・バックアップ・SECURITY.md）整備の**トリガー条件**を明文化（例: 「家庭内クローズド利用を超え外部ユーザに公開する／インターネット到達可能なホスティングに置く時点で着手」）。本リリースは内部/限定利用として「現時点は不要」と記録。
- **受け入れ条件**: トリガー条件が文書化され、本リリースでの非整備理由が記録される。リリース判断と運用文書整備が連動する基準が残る。
- **検証方法**: 手動レビュー。
- **依存タスク**: P0〜P3（最終のリリース形態確認のため後置）。
- **工数 / リスク**: S / 低（文書のみ）。

## P4-2 known-issues / fidelity チェックリスト最終更新

- **紐付く指摘**: 横断（全 Q）。
- **対象ファイル**:
  - `docs/known-issues.md`
  - `docs/superpowers/fidelity/*.md`（stock-home / product-detail / onboarding 等、Phase 2 で触れた画面）
  - `docs/2026-06-13-release-audit.md`（F-2 の判定結果を追記）
- **現状**: known-issues #2（F-1）が未解決として残る。fidelity チェックリストは Phase 2 実装前の状態。F-2 の偽陽性可能性が監査レポートに未反映。
- **変更内容**:
  1. `known-issues.md` から #2 を解消済みに更新（P1-1 完了反映）。
  2. Phase 2 で触れた画面の `fidelity/*.md` を最新化（F-3/F-4/F-5/F-9/D-6 の達成、`fidelity` checklist 項目更新）。
  3. P1-2 の F-2 判定結果（再現せず＝偽陽性 / 再現＝修正済み）を監査レポートに追記。P2-0 の F-8（実装済み）も確認記録。
- **受け入れ条件**: known-issues / fidelity / 監査レポートが Phase 1〜3 の実態と一致。未解決として残るのは既決逸脱（A:将来実装 6 件 / B:恒久判断 12 件）のみ。
- **検証方法**: 手動レビュー（各文書 ↔ 実装）。
- **依存タスク**: P1〜P3。
- **工数 / リスク**: S / 低（文書のみ）。

## P4-3 フル検証＋22 件クローズ突き合わせ

- **紐付く指摘**: 横断（全 Q）。リリース最終ゲート。
- **対象ファイル**: 横断（成果物全体）。
- **現状**: 個別タスク完了後の統合検証が未実施。
- **変更内容**: なし（検証作業）。以下を実施:
  1. `./gradlew build`（frontend WasmJs は OOM のため除外 or `:frontend:compileKotlinWasmJs` で代替）+ `./gradlew test` + `./gradlew :backend:core:integrationTest`（要 `STORAGE_*` / `mise run up`）を実行し green を確認。
  2. dev server / 実機で全画面 eyeball（モック忠実度 98% 以上、Phase 2 の各受け入れ条件）。
  3. 監査レポートの確定リスト（再分類後 22 件相当）を 1 件ずつ「クローズ / 既決として残置」に突き合わせ、本トレーサビリティ表で Q1〜Q5 が YES になることを確認。
- **受け入れ条件**: 全ビルド/テスト green、全画面 eyeball 合格、確定リスト全件がクローズ（または既決として明示残置）。
- **検証方法**: 上記コマンド出力 + eyeball スクショ + 突き合わせ表。
- **依存タスク**: 全タスク（P0-1〜P3-3、P4-1、P4-2）。
- **工数 / リスク**: M / 中（統合検証。実機 eyeball・integrationTest 環境を要する）。

---

# トレーサビリティ表（Q1〜Q5 → 解消タスク）

全タスク完了で各 Q が YES になることを示す。

## Q1 要求充足（機能バグ・ギャップ・仕様整合）→ YES

| A の論点 | 重大度 | 解消タスク |
|---|---|---|
| F-1 買い物リスト残留 | high | P1-1 |
| F-2 世帯切替波及 | high | P1-2（再現確認 → 再現時修正 / 偽陽性なら記録） |
| F-3 ProductDetail 予測 | med | P2-1 |
| F-4 wanted バッジ/need 加算 | med | P2-2 |
| F-5 デスクトップ 3 列 | med | P2-3 |
| F-6 identity 遷移 | med | P1-3 |
| F-7 cancel 導線 | low | P2-4 |
| F-8 Correction 表示 | low | P2-0（実装済み確認） |
| F-9 Welcome wide 分岐 | low | P2-5 |
| C-1 refresh 購読 | low | P3-1 |
| C-2 occurredAt 配線 | low | P3-2 |
| C-3 selectedTab hoist | low | P3-3 |

→ 全機能ギャップ解消 + 仕様逸脱の記録/整合で **Q1 = YES**。

## Q2 モック再現度（98% 以上）→ YES

| A の論点 | 解消タスク |
|---|---|
| ProductDetail 97%（F-3 予測欠落） | P2-1 |
| Onboarding 98%（D-6 radius 18→22） | P2-6 |
| StockHome の機能側差分（F-4/F-5） | P2-2 / P2-3 |
| Welcome wide 分岐（F-9） | P2-5 |

→ ProductDetail（97→98%+）と Onboarding（D-6 解消）で是正対象が消え、**全画面 98% 以上＝Q2 = YES**。

## Q3 ルール遵守（重大違反ゼロ・軽微もクローズ）→ YES

| A の論点 | 解消タスク |
|---|---|
| R-1 増減プレビュー文言（実は規約 exempt → 整合化） | P0-3 |
| R-2 PreferenceStore public | P0-1 |
| R-3 例外翻訳表陳腐化 | P0-2 |
| R-4 陳腐コメント | P0-4 |

→ 軽微 4 件クローズで **Q3 = YES**（重大違反は監査時点でゼロ）。

## Q4 運用ドキュメント → YES

| A の論点 | 解消タスク |
|---|---|
| D-1 CORS 形式 | P0-5 |
| D-2 AUTH トラブルシュート | P0-6 |
| D-3 storage 使い分け基準 | P0-7 |
| D-4 ReadyContent 構造説明 | P0-8 |
| D-5 me() 削除記録 | P0-9 |
| 本番運用文書（既決見送り）のトリガー定義 | P4-1 |

→ 開発系の不一致 5 件を解消し、本番運用系はトリガー明文化で「意図的不在」を制御可能化＝**Q4 = YES**。

## Q5 モック機能充足 → YES

| A の論点 | 解消タスク |
|---|---|
| ShoppingList F-1 | P1-1 |
| StockHome F-4 | P2-2 |
| ActivityTab F-8 | P2-0 |
| ProductDetail F-3 | P2-1 |
| Onboarding F-7 | P2-4 |

→ 主要フロー（詳細・補充消費・訂正・カタログ・通知）100% に加え確定ギャップ解消で **Q5 = YES**。

---

# 補足: 監査の構造的課題への対応（任意・リリース後候補）

本計画は確定 22 件のクローズを主目的とするが、監査が指摘した構造的課題は以下のタスクで部分的に緩和される（恒久対応はリリース後ロードマップ候補）:

1. **正本の重層化** → P4-2 で fidelity/監査の整合を取る。「現在の正本マップ 1 ページ」は別途。
2. **フェーズ送りの scope 落ち** → F-4/F-5（P2-2/P2-3）を本計画で回収。「送り先リスト」運用は別途。
3. **仕様違反と実装裁量の境界** → P3-1/P3-2 で C-1/C-2 を記録し基準の一例を示す。
4. **横断状態管理の App.kt 集中** → P3-3（selectedTab hoist）で一部緩和。F-2（P1-2）の再現確認も同根の検証。
5. **運用文書先送りのトリガー定義** → P4-1 で明文化。
