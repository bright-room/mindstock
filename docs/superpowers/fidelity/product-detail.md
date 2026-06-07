# 忠実度チェックリスト: ProductDetail（商品詳細・mobile）

- mock: `screens-c.jsx:ProductDetail` / `HistoryRow`
- 正解: `/tmp/ms-fidelity/product-detail/mock.png`、実装: `impl.png`、比較: `sbs-final.png`
- sample: mock seedHistory を写した 4 movements + 1 Correction（2 actor たろう/ゆい・1件訂正済）

## 一致（render 突合 ○）

| 要素 | mock | 実装 | 判定 |
|---|---|---|---|
| ヘッダ | 戻る(navBtn) + 設定(sliders) | NavIconButton Back/Settings | ○(アイコンは material 近似) |
| サムネ | 72 r22 + 商品カテゴリ glyph(drop) | glyphForProductName | ○(修正済) |
| 商品名 | `700 19px/1.35` 中央 | summaryTitle copy 19 中央 | ○ |
| 在庫カード | surface r(lg) padding22 border lineSoft 影sm | 同 | ○ |
| 在庫数 | `700 46px/0.9` tnum・out=red | bigQty copy46 | ○ |
| 単位 | `500 16px/1` faint baseline | unitCaption copy16 | ○ |
| StatusDot | dot + ラベル | StatusDot | ○ |
| **StockBar** | **ok=accent(橙)** / low=amber / out=red | ok→tokens.accent に修正 | ○(修正済) |
| 補充/消費 | soft / ghost | AppButton | ○ |
| divider + wanted | 区切り線 + 状況別ボタン/箱 | 同 | ○ |
| 履歴ノード | 36 r12・補充=accentSoft+ / 消費=surface2− + 縦コネクタ | 同 | ○ |
| 履歴ラベル | `600 14.5px/1.3` + 訂正済バッジ + 時刻 | 同 | ○ |
| アバター | **利用者別色** + 頭文字 | avatarColorOf(表示名) | ○(修正済・hue は世帯依存) |
| 訂正リンク | **pencil + 訂正** accent | AppIcon Pencil + 訂正 | ○(修正済) |
| 相対時刻 | 1日前/4日前/M/D | relTimeOf | ○ |

## 差分（backend 起因・非表示を明示）

| ID | 要素 | 状態 |
|---|---|---|
| D-PD2 | 「あと約X日」予測（accent・最低在庫の下） | `[backend]` 消費ペース未実装 → 非表示。これに伴い最低在庫の縦位置が mock(2行)より下寄りになる |

## 決定済（mock 逸脱を明示で許容）

| ID | 要素 | 決定 |
|---|---|---|
| D-PD4 | 訂正理由の表示 | **表示する**（ユーザ承認 2026-06-08）。mock は出さないが、訂正理由は domain にあり実アプリで有用なため UX 改善として残す。意図的な mock 逸脱として記録。 |

## メモ
- 名前: mock は自分を「あなた」固定表示、実装は実 displayName(「たろう」)。実アプリでは実名が正しいので逸脱として許容（mock はプロトタイプ簡略化）。
- 微調整候補: bigQty を `.copy(fontSize=46)` した際 lineHeight が baseline(27sp)のままなので 46*0.9 に合わせる（縦リズム微差）。
