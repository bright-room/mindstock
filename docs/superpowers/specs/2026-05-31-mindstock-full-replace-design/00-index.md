# mindstock フルリプレイス設計

家庭の在庫管理 SaaS「mindstock」を、Claude Design の画面仕様(全 5 チャットで反復した最終状態)を起点にフルリプレイスするための設計ドキュメント。

- 起点: Claude Design ハンドオフバンドル(`mindstock` プロトタイプ)。**最終 landed 状態**(`data.jsx` を真実とする)をモデル化する。
- 手法: 仕様検討に **sudoモデリング**(システム関連図 → ユースケース図 → ドメインモデル図 → オブジェクト図)、システム設計に **ICONIX**(ドメインモデル → ユースケース記述 → ロバストネス図 → シーケンス図 → 詳細クラス図)。
- 前提: 現行スタック(KMP / Ktor / kotlinx-rpc / Exposed / Compose Multiplatform(Wasm) / PostgreSQL / Zitadel OIDC)と CLAUDE.md の 4 原則(層責務・nullable 戻り値禁止・リッチドメイン・`@Rpc` 必須)を踏襲する。
- 範囲: `:shared` を除く全モジュール(`:domain` / `:rpc` / `:backend:*` / `:frontend`)の Kotlin ソースを削除し、新ドメインモデルから再構築。モジュール構成・gradle・build-logic・CI・settings は維持。

## 構成

| ファイル | 内容 |
|---|---|
| [01-sudo-modeling.md](01-sudo-modeling.md) | 仕様検討: システム関連図 / ユースケース一覧 / 境界付けられたコンテキスト(コンテキストマップ + パッケージ関連図) / オブジェクト図 |
| [02-iconix.md](02-iconix.md) | システム設計: ユースケース記述 / ロバストネス図 / シーケンス図 / 詳細クラス図 / モジュールマッピング |
| [03-domain-detail.md](03-domain-detail.md) | 詳細ドメインモデル: VO 制約 / 集約メソッド完全シグネチャ / 数量畳み込み規則 / ドメイン例外体系 |

## プロトタイプからの意図的な乖離(設計判断)

1. **訂正(correction)は append-only**。プロトは履歴を上書き(`corrected` フラグ)するが、リッチドメイン/台帳原則に沿い、訂正は元 movement を打ち消す**訂正 movement の追記**として表現する。
2. **大元マスタへの「追加リクエスト/承認」フローは存在しない**(チャット途中で導入→撤回済み)。採用はその場で世帯在庫に即反映。
3. **ハード削除は無く、アーカイブ 1 本**(復元可・在庫 0 のときのみアーカイブ可)。
4. **単位プリセットに kg/g/L/mL は無い**(数えて管理する単位のみ + その他自由入力)。
5. **重複登録防止は JAN ベース**(JAN 無し商品はチェックしない)。
6. **消費予測「あと約 N 日」・お知らせ通知・オフライン閲覧は将来対応予定**(モデルには載せるが実装後回し、UI は無効/OFF 固定)。

## ドメイン設計の継承(「コード度外視」≠「標準破棄」)

実装の形は捨てるが、過去に合意したドメイン標準は踏襲する(`domain-refactor-policy-2026-05`):

- `User` クラスは作らない。利用者 =「**住人(resident コンテキスト)**」。内部を `profile`(`Profile`/`DisplayName`)・`identity`(`UserId`)・`identity/auth`(`AuthIdentity`)に入れ子。パッケージは内包・**集約は分離**(`Profile` に認証を埋め込まない=漏洩防止)。公開アグリゲートは `Profile`。
- `DomainException` の sealed 階層は作らない(VO 違反は IAE、前提崩れのみ専用例外)。
- 集合型は `val list` 公開 + ドメイン操作のみメソッド。`@JvmInline value class` を sealed variant にしない。
- 商品/在庫は `CatalogItem`(素性)/ `Product`(世帯の採用)/ `Stock`(数量・台帳)に分解。`StockMovement.actor` は住人の公開アグリゲート `Profile` 埋め込み(脱退後も履歴解決可・認証は漏れない)。
