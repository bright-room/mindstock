# 要求カバレッジ / 実装ギャップ調査(2026-06-07)

当初の全体計画(`docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/`)と画面モック(`docs/ref/mindstock.zip`)の要求が、現状 main(P0〜P6 完了時点)でどこまで実装されているかの突き合わせ結果。frontend / backend のコードを実コードで確認して判定した。

## サマリ

- 設計仕様 02-iconix の **UC1〜UC24 のうち、コア 21 個は完全に実装・配線済み**。
- 未充足は以下の 3 系統に整理でき、**「設計に無いのに漏れた」予期せぬ抜けは無い**。
  - **A. UC一覧にあるが未実装** — UC12(カメラ)・UC22 の画像・UC11 の外部API。いずれも P6 実装中に承認のうえ見送り。
  - **B. 仕様で「将来対応予定」と明記済み** — 消費予測 / Web Push / オフライン / 消費の傾向。
  - **C. 当初から見送り合意** — QR/ディープリンク参加・デスクトップレイアウト。

凡例: ✅ 実装済み(RPC+Service+UI 配線) / 🟡 部分・stub / ❌ 未実装

## UC 別カバレッジ(UC1〜UC24)

| UC | 名前 | 状態 | 備考 |
|---|---|---|---|
| UC1 | ログインする(OIDC) | ✅ | Zitadel OIDC + PKCE、WS ハンドシェイク JWT |
| UC2 | 初回: 表示名を登録する | ✅ | OnboardingViewModel |
| UC3 | 世帯を作成する(スキップ可) | ✅ | create / CreateHouseholdSheet |
| UC4 | 招待コードで世帯に参加する | ✅ | join / JoinCodeSheet(コードは手入力) |
| UC5 | 世帯を切り替える | ✅ | HouseholdSwitcher(WS 再接続不要) |
| UC6 | 世帯名を変更する | ✅ | rename(owner 限定) |
| UC7 | 世帯から退出する | ✅ | leave(最後のオーナー保護あり) |
| UC8 | 招待を発行/再発行/失効する | ✅ | createInvite / revokeInvite(明示発行式) |
| UC9 | メンバーの権限変更/除外 | ✅ | changeRole / removeMember(確認ガード) |
| UC10 | マスタから商品を採用する | ✅ | adopt(単位・最低在庫) |
| UC11 | JANで商品を検索する(マスタ→外部API→手入力) | 🟡 | `lookupByJan` は動作。**外部API(楽天/Yahoo)は `UnconfiguredProductGateway` stub で常に NotFound→手入力フォールバック**。マスタ照合+その場手入力は機能 |
| UC12 | バーコードをスキャンして追加 | ❌ | **frontend にカメラ/スキャナ UI 無し**。JAN は手入力で代替。P6-2 で承認のうえ見送り(Wasm getUserMedia が重い) |
| UC13 | マスタに無い商品をその場で追加 | ✅ | addCustom(JAN 任意) |
| UC14 | 在庫を補充する | ✅ | replenish + MoveSheet + トースト |
| UC15 | 在庫を消費する | ✅ | consume |
| UC16 | 在庫一覧を見る/検索する | ✅ | list/grid + 検索 |
| UC17 | 商品詳細・履歴を見る | ✅ | ProductDetail オーバーレイ + history |
| UC18 | 買い物リストを見る(自動+手動) | ✅ | shoppingList read-model |
| UC19 | 商品を手動で買い物リストに入れる/外す | ✅ | setWanted |
| UC20 | 在庫から探して買い物リストに追加 | ✅ | AddToListSheet |
| UC21 | 記録を訂正する(数量+理由) | ✅ | correct(append-only) |
| UC22 | 商品マスタを編集(単位/画像/最低在庫) | 🟡 | **単位・最低在庫・アーカイブは ✅**。**画像は ❌**(`changeImage` RPC・`ProductImage` 型・DB列はあるが、アップロード/表示の口と UI が無い)。P6-2 で承認のうえ見送り |
| UC23 | 商品をアーカイブ/復元(在庫0時のみ) | ✅ | archive / unarchive + 一覧 |
| UC24 | 全体の活動履歴を見る | ✅ | activity フィード(日付グループ) |

## A. UC一覧にあるが未実装(要求として未充足)

- **UC12 バーコードスキャン(カメラ)** — スキャナ UI 無し。型/`lookupByJan` RPC はある。**承認済み見送り**(P6-2)。
- **UC22 の画像** — `changeImage` RPC・`ProductImage`(None/Stored)・`ImageRef`・DB の imageRef 列・hydrate はあるが、画像選択/アップロード endpoint・表示(Stored 時の取得/キャッシュ)・UI 欄が無い。**承認済み見送り**(P6-2、backend に画像配信経路なし)。
- **UC11 の外部API実連携** — `ExternalProductGateway` interface はあるが実体は `UnconfiguredProductGateway`(DI 配線済・常に NotFound)。楽天/Yahoo の実取得は未。**「実プロバイダは後続」と記録済み**(P5a)。

## B. 仕様で「将来対応予定」と明記済み(計画どおりの後回し)

02-iconix B-6 / 01-sudo-modeling A-2 に明記。UI は placeholder / 無効トグルで配置済み。

- **消費予測「あと約N日」** — domain に消費ペース算出なし。UI 表示も出さない方針(P6-1 で確定)。
- **在庫減少のお知らせ(Web Push)** — `:backend:schedules` で実装予定。設定トグルは将来対応予定で無効。
- **オフライン閲覧(PWA)** — 設定トグルは将来対応予定で無効。
- **消費の傾向(トレンド)** — 設定の「その他」に「近日」表示で無効リンク。

## C. 当初から見送り合意

- **QR/ディープリンク参加** — P6-3 で明示見送り。招待コードは手入力のみ(参加自体は動作)。
- **デスクトップレイアウト(サイドバー/マルチカラム)** — mock にはあるが P6 は mobile 優先。**PR #118 で `NavigationSuiteScaffold`(adaptive)を撤去したため、現状は実質モバイル専用**。デスクトップ対応を再開する場合は下部ナビ周りの再設計が必要(留意点)。

## 参照

- 設計: `docs/superpowers/specs/2026-05-31-mindstock-full-replace-design/{00-index,01-sudo-modeling,02-iconix,03-domain-detail}.md`
- モック: `docs/ref/mindstock.zip`(`app/screens-*.jsx`)
- 実機 eyeball とその後の修正: PR #117(在庫ヘッダ世帯名/ProductDetail 透過)・PR #118(買い物/履歴タブ忠実化・下部ナビ・軽微)
- 外部gateway stub: `backend/core/src/main/kotlin/net/brightroom/mindstock/infrastructure/gateway/UnconfiguredProductGateway.kt`
