# 忠実度チェックリスト: StockHome（在庫ホーム・mobile）

- mock: `screens-a.jsx` (StockHome / SummaryStrip / ProductCard / CompactCard)
- 正解: `/tmp/ms-fidelity/stock-home/mock-mobile.png`（mock 実描画・402px・iOS枠/bezel除去）
- 実装: `/tmp/ms-fidelity/stock-home/impl-mobile.png`（`?preview=stock-home`・402px）
- side-by-side: `sbs-header.png` / `sbs-card.png`

## 一致している項目（render 突合済 ○）

| 要素 | プロパティ | mock 値 | 実装 | 判定 |
|---|---|---|---|---|
| 挨拶 | font / 色 | `500 13px/1` sub | greeting() sub | ○ |
| タイトル「在庫」 | font / ls / 行高 | `800 25px/1.1` ls-0.02em | screenTitle()(trim効・締まり一致) | ○ |
| SummaryStrip バナー | bg/padding/radius/影/アイコン箱44 r13/見出し`700 16/1.2`/副文`500 12.5/1.3`/chevR | — | 一致 | ○ |
| 検索欄 | height50 r14 icon placeholder faint | — | SearchField | ○ |
| ProductCard サムネ | soft square r14 + 斜めハッチ + カテゴリ icon | — | Thumb | ○ |
| ProductCard 名前 | `600 15.5px/1.35` ink ellipsis | cardTitle() | ○ |
| StatusDot + ラベル | 8px dot + soft halo + ラベル`600 12.5/1` 同色 | StatusDot | ○ |
| 在庫数 | `700 30px/0.9` tnum / **out は red** | bigQty() + out red | ○ |
| 単位 | `500 11.5px/1` faint | unitCaption() | ○ |
| StockBar | h8 r99 + min マーカー | StockLevelBar | ○ |
| 補充/消費 | soft / ghost ボタン icon+label | AppButton | ○ |
| AddTile「商品を追加」 | dashed border | AddTile | ○ |

## 差分（× → 修正済 ✓ render 再確認）

| ID | 要素 | mock | 修正 | 判定 |
|---|---|---|---|---|
| D1 | HouseholdPill | アイコン + 名前 + 人数を**1行**インライン + chevron(chevD) | 1行化(padding 7/10/7/8・r99・icon箱24 r8・名前`700 13.5/1`・人数`600 11/1`faint・chevD15) | ✓ |
| D2 | Bell ボタン | 右上に**赤い通知ドット**バッジ | NavIconButton に `badge` 追加(8px statusOut + surface 2px リング)・StockHeader で true | ✓ |
| D4 | SegmentedControl | **アイコンのみ**(grid / list)・順序 grid→list・幅~96 | SegOption に icon 追加・icon-only(Grid/ListView)・順序 grid→list・幅96 | ✓ |

注: grid/list/chevronDown は material アイコンで近似(P6-1a の「商品カテゴリ以外は material」決定を踏襲)。mock の自作 SVG と完全一致はしないがレベル一致。

## 差分（backend 起因・本トラックでは出さない・明示）

| ID | 要素 | mock | 状態 |
|---|---|---|---|
| D3 | 予測トレンドバナー「◯◯ はあと約X日で切れる予測です」 | shop バナー下に表示 | `[backend]` 消費ペース未実装 → 非表示（トラック B） |
| D5 | ProductCard「· あと約X日」 | status 行に併記 | `[backend]` 同上 → 非表示 |

## メモ

- 検証ループ（mock 実描画クローン方式 + `?preview=` harness）が end-to-end で機能することを確認。
- MindstockType の lineHeight + `LineHeightStyle(trim=Both, Center)` で CSS の `/1.0`〜`/1.35` の締まりが再現できている（looseness 無し）= advisor 指摘リスクをクリア。
- サンプルデータ（previewStocks 7件 vs mock seed 12件）の違いは見た目比較に影響しない（件数・status 分布の差のみ）。
