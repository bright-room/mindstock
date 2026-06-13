# リリース前監査レポート(2026-06-13)

リポジトリ全体を「画面モック・設計書・ルール・運用ドキュメント」の 4+1 観点で点検した結果と、是正のためのフェーズ分け実行プラン。

- 監査体制: 監査 21 + 反証 95 + 網羅性チェック 6 = 延べ 122 エージェント(マルチエージェントワークフロー 2 本 + パッチ 3 本)
- 正本: 画面モック `docs/ref/mindstock.zip`(React JSX プロトタイプ)/ 設計書 `docs/superpowers/specs/` / ルール `.claude/rules/*.md` + `CLAUDE.md` / 忠実化記録 `docs/superpowers/fidelity/`
- 全指摘は反証フェーズ(実在確認・既決事項・誤読の検証)を通過した確定リスト。監査時点のコードは `main`(bfe5808e)

## 冒頭サマリ

| 問い | 結論 | 一行要約 |
|---|---|---|
| **Q1 要求充足** | **条件付き YES** | 設計書要求は 98 項目で充足確認。確定残は機能バグ 2 件(high)+ 中小 7 件 |
| **Q2 モック再現度 98%** | **ほぼ YES** | 検証 22 画面中、是正を要する 98% 未満は **ProductDetail(97%)の 1 画面**(+既決主因の AddProduct 97%) |
| **Q3 ルール遵守** | **YES(重大違反ゼロ)** | 4 原則(層依存・nullable・リッチドメイン・@Rpc)は全モジュール違反ゼロ。軽微 3 件のみ |
| **Q4 運用ドキュメント** | **開発系 YES / 本番運用系は意図的不在** | ローカル開発・環境変数は実体一致。デプロイ/監視/バックアップは「公開運用時に整備」と既決済み |
| **Q5 モック機能充足** | **ほぼ YES** | 主要フロー(詳細・補充消費・訂正・カタログ・通知)は機能完成度 ~100%。確定ギャップ 5 件 |

**件数**: 指摘総数 **91** → 反証で誤検知除外 **30** / 「充足確認」への再分類 **20** / 既決逸脱(対応不要・明示) **18** / **確定(要対応)23 件 → 統合後 22 タスク**。

反証フェーズ自体の誤りも 3 件検出し再反証で修正済み:

1. q1-dm-01「CatalogItem 構造乖離(high)」→ P2 再設計(`docs/superpowers/specs/2026-06-02-p2-domain-reshape-product-catalog-design.md:51-63,98-100`)が全体設計 03-domain-detail を上書き済みで**誤検知**
2. 「ProductDetail の予測日数は backend 未実装が理由」→ 誤り(消費予測は実装済み)。実態は **frontend 配線漏れ = 新規確定(F-3)**
3. 「ProductSettings に画像欄なし(95%)」→ 陳腐化(`ProductSettingsSheet.kt:106` で ImageField 使用中)。98% に上方修正

---

## 是正結果(2026-06-13・確定 22 件のクローズ突き合わせ)

是正実装計画 `docs/superpowers/plans/2026-06-13-release-audit-remediation.md` に基づく対応結果。Phase 0〜4 を 5 PR(フェーズごと)で実施。

| ID | 重大度 | Phase/Task | 結果 |
|---|---|---|---|
| F-1 | high | P1-1 | **解決**: 補充時に `setWanted(false)` で手動希望を解除(known-issues #2 解消)。テスト追加 |
| F-2 | high | P1-2 | **偽陽性(実読確認 + 実機確認済み 2026-06-14)**: 各タブ VM は `remember(householdId)` で生成され、世帯切替(`AppSession.setActiveHousehold` の reactive 更新)で再生成+再 load される。監査の「refresh 未発火」は `remember(householdId)` keying の見落とし。コード変更なし。**実機確認(Playwright)で、別荘(空)⇄わたしの家(在庫3点/買い物1点/履歴多数)の往復で在庫・買い物・履歴の全タブが再読込されることを確認=バグ再現せず**。なお監査前提の「要 2nd Zitadel ID」は**誤り**で、`HouseholdRegisterController.create` は所属世帯数を制限しないため 1 アカウントで複数世帯を作成・切替できる(2nd ID 不要) |
| F-3 | med | P2-1 | **解決**: ProductDetail に消費予測「あと約N日」を表示 |
| F-4 | med | P2-2 | **解決**: 手動希望バッジ + needCount への want 加算(shoppingList の manualItems 由来) |
| F-5 | med | P2-3 | **解決**: wide shell で在庫グリッド 3 列 |
| F-6 | med | P1-3 | **解決**: appendMovement の Pending→Persisted 遷移を integration テストで担保(挙動不変) |
| F-7 | low | P2-4 | **解決 + 実機確認済み 2026-06-14**: オンボーディングに「別のアカウントでログイン」導線(reauth 再利用)。実機確認(Playwright)で押下時に `reauth.request()` → token 破棄 → Zitadel authorize へ遷移(Cookie クリア時は `:8081/ui/login/login` のホストログインに着地し別アカウント入力可)を確認 |
| F-8 | low | P2-0 | **実装済み(検証のみ)+ 実機確認済み 2026-06-14**: `ActivityScreen.kt:89-96` が Correction を除外し対象行に訂正タグ付与=設計 `p6-1b-design.md:103` と一致。コード変更なし。実機確認(Playwright)で牛乳の補充 7→5 訂正を作成 → 活動タブで対象行に「訂正済」タグ付与・訂正行は別行として非表示、商品詳細では在庫 21→19 再計算+訂正後値+訂正理由表示を確認 |
| F-9 | low | P2-5 | **解決**: WelcomeScreen を wide/compact で幅分岐 |
| C-1 | low | P3-1 | **整合(spec 改訂)**: refresh 購読を UI 層 `LoadWithRefresh` 一元化と spec/ルールに明記 |
| C-2 | low | P3-2 | **整合(意図明記)**: 買い物リスト補充の `OccurredAt.now()` 固定を spec/コメントに明記 |
| C-3 | low | P3-3 | **解決**: `selectedTab` を hoist し非対称を解消 |
| R-1 | low | P0-3 | **整合**: 増減プレビューの数値+単位を `qty_with_unit` リソース化(規約上は exempt だが一貫化) |
| R-2 | med | P0-1 | **解決**: `PreferenceStore`/`pickImage` を internal 化 |
| R-3 | med | P0-2 | **解決**: 例外翻訳表に `ArchivedProductMovementException` を追記 |
| R-4 | low | P0-4 | **解決**: 陳腐コメント(P6-1b 参照)を除去 |
| D-1 | low | P0-5 | **解決**: README に `STORAGE_CORS_ORIGINS` の形式を明記 |
| D-2 | low | P0-6 | **解決**: README に AUTH_* トラブルシュート節を追加 |
| D-3 | low | P0-7 | **解決**: localStorage/sessionStorage 使い分け基準をルールに追記 |
| D-4 | low | P0-8 | **解決**: frontend-architecture に ReadyContent 構造を追記 |
| D-5 | low | P0-9 | **解決**: ws-rpc spec に `me()` 削除の grep 確認記録を追記 |
| D-6 | med | P2-6 | **解決**: ConfirmStep カード radius を 22dp(`tokens.radiusLg`)に統一 |

**残ユーザ宿題(環境制約)**: ①F-2 の 2 世帯実機再現 → **完了(2026-06-14・Playwright 実機確認。全タブ波及を往復で確認=問題なし。なお 2nd Zitadel ID は不要だった)** ②F-7 の authorize 遷移実機確認 → **完了(2026-06-14・Playwright 実機確認。authorize/ホストログインへの遷移を確認=問題なし)** ③Phase 2 の全画面 dev server eyeball(wide/compact・予測・バッジ)→ **完了(2026-06-14・Playwright で compact/wide 両レイアウトを実描画確認。F-3 予測/F-4 wanted バッジ+needCount(自分で追加n)/F-5 wide 3列グリッド/F-9 Welcome 幅分岐/D-6 ConfirmStep 角丸/F-8 活動の訂正済タグ(訂正行除外)/WideShell 全タブ = 全項目 PASS・問題なし)**。いずれもコード↔mock の静的突き合わせは完了済み。

---

# A. 調査結果

## A-1. 観点 1: 要求充足(Q1)+ モック機能充足(Q5)

### 確定した機能バグ(リリースブロッカー候補)

| ID | 状態 | 内容 | 根拠 | 残タスク/工数 |
|---|---|---|---|---|
| **F-1** | 部分実装 **high** | 買い物リストの商品を補充してもリストから消えない(known-issues #2)。`manuallyWanted` が補充で解除されない | `docs/known-issues.md:23-36`、`domain/.../shopping/ShoppingNeed.kt:22-26`、モック正: `data.jsx:215-222`(補充時 `wanted: false`) | 補充時の wanted 解除 or 掲載判定の修正 / M |
| **F-2** | 部分実装 **high** | 世帯切替後、買い物・活動タブが再読込されない(在庫以外に切替が波及しない) | `frontend/.../app/AuthViewModel.kt:141-144`(session 反映のみ)、`frontend/src/webMain/.../App.kt:603-605`(refresh signal 発火なし) | 切替後に `refresh.request()` 等の波及 / S |

### 確定した機能ギャップ(medium 以下)

| ID | 状態 | 内容 | 根拠(正 ⇔ 実装) | 工数 |
|---|---|---|---|---|
| **F-3** | 未実装 med | ProductDetail に「あと約X日」予測表示が無い(ShoppingList・ProductCard のみ使用) | mock `screens-c.jsx:210` ⇔ `forecast_days_left` 使用先に ProductDetailScreen 無し(`strings.xml:317,319`) | S |
| **F-4** | 未実装 med | ProductCard/CompactCard の wanted「リスト」バッジ + needCount への want 加算。P6-1b 送り→spec 再掲漏れ | mock `screens-a.jsx:105-109,149`・`data.jsx:51-54` ⇔ `ProductCard.kt`(該当なし)・`StockSummary.kt:6-10` | M(ShoppingList→在庫一覧への data flow 要) |
| **F-5** | 部分実装 med | デスクトップ時グリッド 3 列にならず 2 列固定。P6-4a 設計 152 行で「Spec2 送り」→ P6-4b spec から scope 落ち | mock `app.jsx:154` ⇔ `StockHomeScreen.kt:103`(`GridCells.Fixed(2)`、wide 未使用) | S |
| **F-6** | 部分実装 med | `Stock.appendMovement` の Pending→Persisted identity 遷移が hydration 依存。同一集約を連続操作すると潜在バグ(現在は re-load 設計で回避) | `03-domain-detail.md:386-430` ⇔ `Stock.kt:60-86` | S(検証+テスト) |
| **F-7** | 未実装 low | オンボーディングの「別アカウントでログイン」(cancel)導線なし | mock `app.jsx:138` ⇔ `OnboardingScreen.kt:76-85`・`AuthFlow.kt:10-31`(該当メソッドなし) | S |
| **F-8** | 挙動差異 low | 活動タブが Correction 行自体を除外(設計は「元行に訂正済タグ」前提)。全件訂正のみの世帯で空状態になる edge case | `2026-06-07-p6-1b-shopping-activity-design.md:103` ⇔ `ActivityScreen.kt:95-96` | S |
| **F-9** | 部分実装 low | WelcomeScreen が `widthIn(max=420.dp)` 固定で Wide/Compact 分岐なし | `2026-06-07-p6-4a-responsive-shell-welcome-design.md:131` ⇔ `WelcomeScreen.kt:67` | S |

### 仕様と実装の整合(仕様逸脱だが機能は動作)

| ID | 内容 | 根拠 | 対応 |
|---|---|---|---|
| **C-1** | refresh 購読の所在: 仕様「ViewModel で購読」vs 実装「UI 層 `LoadWithRefresh` で一元購読」。体系的なアーキテクチャ選択だが仕様文言に反する | `2026-06-07-p6-1b-shopping-activity-design.md:60,84` ⇔ `App.kt:428-448` | どちらかに一本化(spec 改訂 or VM 移動)/ S-M |
| **C-2** | ShoppingList の補充配線が occurredAt を無視し App 側で `OccurredAt.now()` ハードコード(機能等価だが計画と不一致・型非対称) | `docs/superpowers/plans/2026-06-08-occurred-at-backdate.md:449-622` ⇔ `ShoppingListScreen.kt:89,195-197`・`App.kt:483` | 型を揃える or 仕様に明記 / S |
| **C-3** | `selectedTab` が ReadyContent ローカルで他の app 層状態と非対称 + responsive 切替の状態一貫性テストなし(実害は未確認) | `App.kt:449,313-325` | hoist + テスト / S |

### 既決逸脱(対応不要・明示 18 件)

リリース可否には影響しないが、性質が 2 種類に分かれる。**A=将来実装予定(申し送り。リリース後ロードマップ候補)6 件 / B=恒久的な設計判断(実装予定なし・実装側が正)12 件**。

#### A. 将来実装予定(申し送り型)

| # | 内容                                                                                                                                                   | 決定の記録 | 時期の目安 |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------|---|---|
| 1 | バーコードスキャン / メタ取得 UI(AddProduct)。※JAN 照会の backend(`CatalogRpcService.lookupByJan`)は実装済み(ただし、その先のJANコードからYahooAPIや楽天API経由での商品検索は未実装)で、カメラ/スキャナ UI のみ省略 | `docs/superpowers/fidelity/add-product.md`(P6-2 時点の決定) | 未定 |
| 2 | 初期カタログ(テンプレ商品)投入                                                                                                                                     | 実機 eyeball 指摘③「backend 要・別 PR」として繰越済み | 別 PR |
| 3 | 通知 scope②(バッチ判定)・scope③(Web Push 配信)・既読状態の永続化(現状は client 派生・永続化なし)                                                                                   | `2026-06-13-stock-alert-notifications-design.md:3-5,134`(scope①=ベル→アラート一覧のみ本リリース) | 将来 |
| 4 | `backend:schedules` の実処理(現状 `Main.kt` のみのプレースホルダ)                                                                                                    | `02-iconix.md:244`「バッチ(将来・通知等)」・`refactoring-master-plan.md:47` | 通知バッチ実装時 |
| 5 | 消費予測のバッチキャッシュ化(現状は realtime 純ドメイン算出が正式決定)                                                                                                            | `2026-06-08-consumption-forecast-design.md:106-110`(通知バッチ時に `Stock.forecast()` を再利用予定) | 通知バッチ実装時 |
| 6 | 本番デプロイ・監視・バックアップ・SECURITY.md                                                                                                                         | `refactoring-master-plan.md` 見送り節「公開運用が視野に入った時点で。今はやらない」 | **公開運用の判断時(Phase 4 で要否再確認)** |

#### B. 恒久的な設計判断(実装予定なし・実装側が正)

| # | 内容 | 決定の記録 |
|---|---|---|
| 7 | 招待はコード明示発行式。リンク/QR/共有/有効期限 UI・モックの JoinFlow 着地フローはやらない(backend に expiresAt 等なし) | `docs/superpowers/fidelity/household-sheets.md`(P6-3b で確定) |
| 8 | ユーザ名は実 displayName 表示(モックの「あなた」固定にしない) | 忠実化作業時のユーザ承認 |
| 9 | 訂正理由を履歴に表示(モックはバッジのみ)= UX 改善 | `docs/superpowers/fidelity/product-detail.md`(ユーザ承認) |
| 10 | StockBar の ok 色 = accent 橙(モックの緑から系統修正) | 同上(2026-06-08 の系統修正) |
| 11 | JoinCodeSheet は実コード照合プレビュー(モックはデモ文言) | 世帯シート忠実化時のユーザ承認 |
| 12 | AddProduct の単位デフォルト「個」(推奨単位 defaultUnit は P2 再設計で廃止) | `2026-06-02-p2-domain-reshape-product-catalog-design.md:98-99` |
| 13 | Settings の退出ボタン位置・メールアドレス表示の mock 逸脱 | `docs/superpowers/fidelity/settings.md` |
| 14 | Onboarding Welcome 見出しの折返し差(mock は `<br>` 2 行・実装 1 行) | `docs/superpowers/fidelity/onboarding.md:23` |
| 15 | ToggleRow disabled 色の非条件化 | `2026-06-08-p6-4b-fidelity-audit.md:60-64` |
| 16 | JAN 数字列にならない入力は名前検索フォールバック(設計と一致=実は逸脱ですらない) | `2026-06-07-p6-2-product-add-settings-archive-design.md:89` |
| 17 | replenish/consume の occurredAt 先行実装(後に `2026-06-08-occurred-at-backdate-design.md` で正式化され逸脱解消済み) | P6-1a spec 24-25 行「P6-4b で覆す」 |
| 18 | `gradle.workers.max=4` の維持 | `refactoring-master-plan.md` 見送り節「現状妥当として維持」 |

#### 補足: 既決だったが確定タスクへ「昇格」させた 2 件

**F-4(wanted バッジ)と F-5(デスクトップ 3 列)は元々既決(P6-4a 設計 152 行で「Spec2 送り」)だった**が、送り先の P6-4b spec に再掲されず scope 落ちしており、「やらないと決めた」のではなく「忘れられた」状態のため、既決扱いせず Phase 2 の確定タスクとした(構造的課題 2 の実例)。

### 確認済みクリーン(主要・抜粋)

設計書要求の充足確認は 6 領域で **98 項目**。代表:

- 在庫 RPC→Controller→Service→domain の全配線(`StockRegisterController.kt:16-51` 他)
- 消費予測: 設計 6 ケース+境界 3 テスト網羅(`StockForecastTest.kt:66-107`)
- 世帯管理 18 項目(作成・参加・切替永続化・権限変更・除外・招待発行/失効・rename・退出)
- シェル・認証・通知 25 項目(840dp 切替・単一 /api/rpc・whoami・アラート導出)
- アーカイブ済み商品への在庫操作ガード(frontend 導線 + domain 不変条件)
- 訂正フロー一式(MovementIdentity・理由必須・履歴畳み込み)

## A-2. 観点 2: モック再現度(Q2 視覚)

評価方法: プロパティ適合方式(モック JSX のスタイル値と Compose 実装値の突き合わせ。`docs/superpowers/fidelity/` のチェックリストを軸に補完)。

| 画面 | 再現度 | 備考(98% 未満の差分) |
|---|---|---|
| StockHome | 99% | トークン・半径・タイポ・カード全 15 項目適合。残差は機能側 F-4/F-5 |
| **ProductDetail** | **97%** | **F-3 予測日数表示欠落**。他は全適合(在庫カード 46px/0.9・履歴ノード・アバター色) |
| MoveSheet | 98% | 全差分が既決(Stepper 大数字 stack・DatePick は設計由来) |
| CorrectionSheet | 99% | 訂正理由入力は既決の追加 |
| ShoppingList | 98% | 破線ボーダー・badge10.5 適合 |
| AddToListSheet | 99% | 差分なし |
| ActivityTab | 98% | 日カード radius 適合 |
| **AddProduct** | **97%** | 主因は既決(スキャン省略・単位「個」)→ 実質是正対象なし |
| ProductMaster | 98% | AddTile accent・glyph thumb 適合 |
| Archived | 99% | opacity 0.7・owner 分岐適合 |
| ProductSettings | 98%※ | ※監査の 95% 評価は「画像欄なし」根拠が陳腐化(`ProductSettingsSheet.kt:106` に ImageField)のため上方修正 |
| 世帯シート 6 枚 | 99% | 全項目適合(CodeInput 26px/0.22em 等)。既決のみ |
| Onboarding | 98% | **D-6**: ConfirmStep カード radius 18dp vs 正 22dp(`OnboardingScreen.kt:386` ⇔ `fidelity/onboarding.md:14`)のみ |
| Settings | 99% | ToggleRow disabled 色は既決記録あり |
| BottomNav / WideShell / Welcome / 通知一覧 | 100% | 差分なし(Welcome の wide 分岐は F-9 = 機能側) |

**判定**: 是正対象として 98% 未満なのは ProductDetail のみ。F-3 と D-6 を直せば全画面 98% 以上。

## A-3. 観点 3: ルール遵守(Q3)

**重大違反ゼロを確認**(clean 49 項目): 層依存方向・nullable 戻り値禁止・リッチドメイン・@Rpc 必須・one-class-per-file・不変構築・トランザクション境界・KMP 構造・designsystem 経由 UI・ルール間の矛盾なし。

確定は軽微 3+1 件:

| ID | ルール | 違反箇所 | 重大度 |
|---|---|---|---|
| R-1 | `frontend-i18n-and-font.md:10-12`(文言は stringResource) | `MoveSheet.kt:114,119` 増減プレビューの直結文字列 | med |
| R-2 | `frontend-kmp-structure.md:13`(expect/actual は internal) | `core/preference/PreferenceStore.kt:4` / `PreferenceStore.js.kt:5` が public | med |
| R-3 | `backend-rpc-and-transactions.md:74-85` 例外翻訳表の陳腐化 | `ArchivedProductMovementException → Conflict`(`SessionGuard.kt:80-81`)が表に無い | med |
| R-4 | コメント陳腐化 | `StockSummary.kt:5`・`SummaryStrip.kt:33`「P6-1b まで未対応」(P6-1b はマージ済) | low |

## A-4. 観点 4: 運用ドキュメント(Q4)

実体一致を確認(clean 14 項目): mise/compose/zitadel-init/garage-init の手順・サービス・ポート・env 一覧・CI 4 job 構成・README 環境変数リファレンス。

| ID | 内容 | 根拠 | 対応 |
|---|---|---|---|
| D-1 | `STORAGE_CORS_ORIGINS` の形式が README「カンマ区切り」vs `application.yaml:32` YAML リスト | `README.md:76` | 形式を実装に合わせ明記 or 実装をカンマ区切り対応 / S |
| D-2 | AUTH_* 未設定時の fail-fast メッセージと復旧手順(`mise run up`)が README に無い | `AuthSettings.kt:27` ⇔ `README.md:48` | トラブルシュート節追加 / S |
| D-3 | localStorage/sessionStorage 使い分け基準が未明文(token=session、設定=local は合理的だが規則なし) | `TokenStore.kt:9` ⇔ `PreferenceStore.js.kt:6` | ルール 1 節追加 / S |
| D-4 | `frontend-architecture.md` に ReadyContent(=モック AppContent/ScreenSwitch 相当)の構造説明なし | `App.kt:391-522` | 追記 / S |
| D-5 | `ResidentRpcService.me()` 削除の grep 確認記録なし(コードは正しい・記録のみ) | `2026-06-06-ws-rpc-transport-redesign-design.md:181-184` | 記録 / S |
| 既決 | 本番デプロイ・監視・バックアップ・SECURITY.md は「公開運用が視野に入った時点で」と決定済み | `refactoring-master-plan.md`(見送り節) | リリース形態確定時に再判断 |

### 本番運用ドキュメントの整備トリガー(P4-1・2026-06-13 明文化)

本リリースは **家庭内クローズド利用**(信頼できる少人数・インターネット非公開ホスティング)を前提とし、本番運用ドキュメント(デプロイ手順 / 監視 / バックアップ / SECURITY.md)は**現時点では整備不要**と判断する。整備は以下のいずれかが満たされた時点で着手する(その時点で本トリガー節を更新する):

- **デプロイ手順 / 監視 / バックアップ**: 「家庭内クローズド利用を超えて外部ユーザに公開する」または「インターネット到達可能なホスティングに恒常的に置く」時点。最初の外部公開デプロイの計画開始がトリガー。
- **SECURITY.md(脆弱性報告窓口)**: リポジトリを公開(public)にする、または家庭外の第三者が利用しうる状態にする時点。
- **バックアップ運用**: 失われると困る実データ(複数世帯の継続利用データ)が蓄積し始めた時点。

これにより「公開運用が視野に入ったら」という曖昧な先送りを、リリース判断と連動する具体条件に置き換える(監査の構造的課題 5 への対応)。

## A-5. Q5: モック機能充足度(画面別・機能完成度)

| 画面 | 機能完成度 | 残ギャップ |
|---|---|---|
| StockHome | 90% | F-4(wanted バッジ・need 計算)/ F-5(3 列) |
| ProductDetail / MoveSheet / CorrectionSheet / DatePick | 100% | なし(訂正フロー・canEdit ガード・バックデート含め全機能パリティ) |
| ShoppingList | 95% | F-1(既知バグ)。掲載条件合成・候補追加・進捗バナーは充足 |
| AddToListSheet | 100% | なし |
| ActivityTab | 98% | F-8(Correction 行の扱い) |
| AddProduct / ProductMaster / ProductSettings / Archived | 100% | なし(重複検出・採用・権限ガード・画像差替含む) |
| 世帯シート群 | 95% | F-2(切替波及)。作成/参加/招待/権限変更/除外は充足 |
| Onboarding | 95% | F-7(cancel 導線) |
| シェル/設定/通知 | ~100% | なし |

※ 監査エージェントの当初評価(世帯系 70-90%)は誤検知込みの過小評価で、反証通過後の確定ギャップに基づき補正済み。

---

# B. フェーズ分け実行プラン

## Phase 0: ルール・文書の即時是正

- **目的/DoD**: ルール違反ゼロ・文書と実体の不一致ゼロ
- **依存**: なし(全タスク並列可・全て S)
- **タスク**: R-1(`MoveSheet.kt:114,119` 文言リソース化)/ R-2(`PreferenceStore` 2 ファイルに internal)/ R-3(翻訳表 1 行追記)/ R-4(陳腐コメント削除 ※F-4 実装まで「未対応」の事実が残る場合は文言修正)/ D-1・D-2(README)/ D-3・D-4(ルール追記)/ D-5(記録)
- **検証**: 該当 grep 再実行 + `./gradlew build`

## Phase 1: 機能バグの解消(high)

- **目的/DoD**: known-issues #2 の再現手順が解消し、世帯切替が全タブへ波及する
- **依存**: Phase 0 と独立
- **タスク**:
  - F-1 買い物リスト残留(M): 仕様判断(補充で wanted 解除 or 充足時自動除外)→ domain 修正 + テスト。対象 `ShoppingNeed.kt` / `StockRegisterService.kt`
  - F-2 世帯切替波及(S): `AuthViewModel.switchActiveHousehold` 後の refresh 発火。対象 `AuthViewModel.kt:141-144`・`App.kt:603-605`
  - F-6 identity 検証(S): `appendMovement` の INSERT→RETURNING 検証テスト。対象 `StockRegisterDataSource`
- **検証**: 再現手順の実機確認 + 追加テスト green + `known-issues.md` 更新

## Phase 2: モック完成度 98% 達成

- **目的/DoD**: 全画面 視覚・機能とも 98% 以上
- **依存**: F-4 は Phase 1 の wanted 仕様判断に依存。他は独立
- **タスク**: F-3 ProductDetail 予測日数(S)/ F-4 wanted バッジ + needCount(M)/ F-5 3 列グリッド(S・`StockHomeScreen.kt:103`)/ F-7 cancel 導線(S)/ F-9 Welcome wide 分岐(S)/ F-8 Correction 表示方針(S)/ D-6 radius 22dp(S・`OnboardingScreen.kt:386`)
- **検証**: `?preview` harness + dev server `--continuous` でモック clone と並列描画(fidelity-verify-loop 手順)。チェックリスト(`docs/superpowers/fidelity/`)更新

## Phase 3: 仕様逸脱の整理・整合

- **目的/DoD**: spec と実装の乖離ゼロ(コード修正 or spec 改訂のどちらかで解消が記録される)
- **依存**: Phase 1/2 の実装が落ち着いてから
- **タスク**: C-1 refresh 購読の一本化判断(S-M)/ C-2 occurredAt 配線の型統一(S)/ C-3 selectedTab hoist + responsive テスト(S)/ 該当 spec の文言更新(`p6-1b-design.md:60,84` 等)
- **検証**: spec 差分レビュー + 既存テスト green + responsive 切替テスト追加

## Phase 4: 運用ドキュメント完成と最終検証

- **目的/DoD**: Q1〜Q5 全てに YES と宣言できる状態
- **依存**: Phase 0〜3 完了
- **タスク**: デプロイ/監視/バックアップ文書の整備要否を「リリース形態」と紐付けて決定(既決の再確認 or 整備 L)/ known-issues.md・fidelity チェックリストの最終更新 / フル検証(`./gradlew build` + 統合テスト + 全画面 eyeball)
- **検証**: 本レポートの確定リスト 22 件が全てクローズしていることの突き合わせ

## 横断で見えた構造的課題

1. **正本の重層化**: 全体設計→個別 design→fidelity 記録の 3 層で上書き関係が暗黙的。反証エージェントすら CatalogItem で旧正本を参照した。「現在の正本マップ」(1 ページ)が必要
2. **フェーズ送りの scope 落ち**: P6-4a で「Spec2 送り」とした 3 列グリッド・wanted バッジが P6-4b spec に再掲されず宙に浮いた。送り先リストの運用が無い
3. **仕様違反と実装裁量の境界が不明文**: refresh 購読の所在・シグネチャ簡略記述など、spec のどの粒度が拘束力を持つか基準が無い
4. **横断状態管理の App.kt 集中**: F-2 / C-1 / C-3 は同根(refresh・世帯切替・タブ状態の配線が App.kt に集中しテストが薄い)
5. **運用文書の先送りにトリガー定義が無い**: 「公開運用が視野に入ったら」の判断時点が定義されておらず、リリース判断と連動しない
