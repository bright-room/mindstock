# 業務知識資料(koto import 用)

mindstock の業務知識(ユビキタス言語・業務イベント・業務ルール・設計判断)をドメイン別に整理した資料。業務知識基盤 [koto](https://github.com/kukv/koto) への import 入力として使うことを想定して構造化している。

人が読む従来型の仕様書は [../specs/](../specs/) を参照。本ディレクトリは同じ知識を koto の知識抽出パイプラインに最適化した形で持つ。

## ファイルとコンテキストの対応

koto の `--context` にはドメイン名をそのまま使う想定:

| ファイル | context | 内容 |
|---|---|---|
| [resident.md](resident.md) | `resident` | 居住者(プロフィール・認証アイデンティティ) |
| [household.md](household.md) | `household` | 世帯(メンバー・役割・招待) |
| [catalog.md](catalog.md) | `catalog` | 商品カタログ(検索・JAN 照会・外部 API 連携) |
| [inventory.md](inventory.md) | `inventory` | 在庫(商品・在庫変動・買い物リスト・数量) |
| [barcode.md](barcode.md) | `barcode` | バーコード(JAN 等の規格・スキャン) |
| [session.md](session.md) | `session` | セッション・認証(Zitadel OIDC) |

## import 手順(koto リポジトリ側で実行)

```bash
cd ~/dev/ghq/github.com/kukv/koto
docker compose up -d --build
pnpm install && pnpm build

MINDSTOCK=~/dev/ghq/github.com/bright-room/mindstock
pnpm run import --context resident  "$MINDSTOCK/docs/knowledge/resident.md"
pnpm run import --context household "$MINDSTOCK/docs/knowledge/household.md"
pnpm run import --context catalog   "$MINDSTOCK/docs/knowledge/catalog.md"
pnpm run import --context inventory "$MINDSTOCK/docs/knowledge/inventory.md"
pnpm run import --context barcode   "$MINDSTOCK/docs/knowledge/barcode.md"
pnpm run import --context session   "$MINDSTOCK/docs/knowledge/session.md"

pnpm run review list   # 抽出された draft をレビュー・承認
```

抽出された候補はすべて `draft` + `needs_review` で入る。承認(`pnpm run review approve <id>`)して初めて検索対象になる。

## 資料の書式と koto の型の対応

各ファイルは次の見出し構造で書かれており、koto の知識型に対応する:

| 見出し | koto の型 | 備考 |
|---|---|---|
| `## 用語集` | `term` | 1 概念 = 1 見出し。定義・別名・関連用語・実装クラス |
| `## 業務イベント` | `event` | koto の定型見出し(概要 / アクター / 対象 / 事前条件 / 事後条件 / 取消・失敗 / 順序・タイミング)に準拠 |
| `## 業務ルール` | `rule` | 不変条件・バリデーション・権限。根拠となる実装クラスを併記 |
| `## 設計判断` | `decision` | アーキテクチャ・ドメイン設計上の判断とその理由 |

## 執筆・更新ポリシー

- **事実ベース**: コード・テスト・プロジェクト規約(`.claude/rules/*.md`)から読み取れることだけを書く。推測は書かない
- **不確かさの明示**: コードから意図が読み取れない箇所は「(要確認: …)」と明記している。koto は「社内文書は古い・間違っている前提」でレビューゲートを通すため、要確認事項はヒアリング・レビューで潰す対象
- **更新**: ドメインモデルや RPC インターフェースを変更したら、対応するファイルを更新する。koto へは再 import → レビューで還流する
